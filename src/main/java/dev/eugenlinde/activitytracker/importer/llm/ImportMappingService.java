package dev.eugenlinde.activitytracker.importer.llm;

import dev.eugenlinde.activitytracker.common.exception.ImportMappingException;
import dev.eugenlinde.activitytracker.importer.csv.GridLayoutChecks;
import dev.eugenlinde.activitytracker.importer.csv.GridText;
import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest;
import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest.Message;
import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest.Role;
import dev.eugenlinde.activitytracker.openrouter.ChatCompletionResponse;
import dev.eugenlinde.activitytracker.openrouter.OpenRouterClient;
import dev.eugenlinde.activitytracker.openrouter.OpenRouterException;
import dev.eugenlinde.activitytracker.openrouter.OpenRouterProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Service
public class ImportMappingService {

    private static final int MAX_ATTEMPTS = 2;
    private static final int ANALYSIS_MAX_TOKENS = 1000;
    private static final int GRID_MAX_TOKENS = 8000;
    private static final String FINISH_CUT_OFF = "length";

    private final OpenRouterClient openRouterClient;
    private final OpenRouterProperties openRouterProperties;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final String analysisPrompt;
    private final JsonNode analysisSchema;
    private final String gridPrompt;
    private final JsonNode gridSchema;

    public ImportMappingService(
            OpenRouterClient openRouterClient,
            OpenRouterProperties openRouterProperties,
            JsonMapper jsonMapper,
            Validator validator
    ) {
        this.openRouterClient = openRouterClient;
        this.openRouterProperties = openRouterProperties;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.analysisPrompt = resource("llm/import-analysis-prompt.txt");
        this.analysisSchema = jsonMapper.readTree(resource("llm/import-analysis-schema.json"));
        this.gridPrompt = resource("llm/import-grid-prompt.txt");
        this.gridSchema = jsonMapper.readTree(resource("llm/import-grid-schema.json"));
    }

    public FileAnalysis analyse(String fileHead) {
        return ask(analysisPrompt, "file_analysis", analysisSchema, fileHead, ANALYSIS_MAX_TOKENS,
                FileAnalysis.class, ImportMappingService::tableProblems);
    }

    public GridLayout gridLayout(List<List<String>> rows) {
        return ask(gridPrompt, "grid_layout", gridSchema, GridText.indexed(rows), GRID_MAX_TOKENS,
                GridLayout.class, layout -> GridLayoutChecks.problems(rows, layout));
    }

    JsonNode analysisSchema() {
        return analysisSchema;
    }

    JsonNode gridLayoutSchema() {
        return gridSchema;
    }

    private static List<String> tableProblems(FileAnalysis analysis) {
        if (analysis.kind() != FileAnalysis.Kind.TABLE) {
            return List.of();
        }
        if (analysis.table() == null) {
            return List.of("table: must be given when kind is TABLE");
        }
        if (analysis.table().weightColumn() == null && analysis.table().stepsColumn() == null) {
            return List.of("table: weightColumn and stepsColumn are both null; a TABLE needs at least one of them");
        }
        return List.of();
    }

    private <T> T ask(
            String systemPrompt,
            String schemaName,
            JsonNode schema,
            String userContent,
            int maxTokens,
            Class<T> type,
            Function<T, List<String>> extraChecks
    ) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(Role.SYSTEM, systemPrompt));
        messages.add(new Message(Role.USER, userContent));
        List<String> problems = List.of();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String content = complete(messages, schemaName, schema, maxTokens, attempt);
            problems = new ArrayList<>();
            T parsed = null;
            try {
                parsed = jsonMapper.readValue(content, type);
            } catch (JacksonException notTheExpectedJson) {
                problems.add("the answer was not JSON of the required shape");
            }
            if (parsed != null) {
                for (ConstraintViolation<T> violation : validator.validate(parsed)) {
                    problems.add(violation.getPropertyPath() + ": " + violation.getMessage());
                }
                if (problems.isEmpty()) {
                    problems.addAll(extraChecks.apply(parsed));
                }
                if (problems.isEmpty()) {
                    return parsed;
                }
            }
            log.warn("Import mapping {} attempt {} rejected: {}", schemaName, attempt, problems);
            messages.add(new Message(Role.ASSISTANT, content));
            messages.add(new Message(Role.USER,
                    "That answer was rejected. Fix these problems and answer again with the complete JSON:\n"
                            + String.join("\n", problems)));
        }
        throw new ImportMappingException("The model's answer was still unusable after " + MAX_ATTEMPTS + " attempts: "
                + String.join("; ", problems));
    }

    private String complete(List<Message> messages, String schemaName, JsonNode schema, int maxTokens, int attempt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                openRouterProperties.getModel(),
                List.copyOf(messages),
                new ChatCompletionRequest.ResponseFormat(
                        "json_schema",
                        new ChatCompletionRequest.JsonSchema(schemaName, true, schema)
                ),
                0.0,
                maxTokens
        );
        ChatCompletionResponse response;
        try {
            response = openRouterClient.call(request);
        } catch (OpenRouterException upstream) {
            throw new ImportMappingException("The model could not be reached (" + reason(upstream) + ")");
        }
        if (response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null) {
            throw new ImportMappingException("The model returned no answer");
        }
        ChatCompletionResponse.Choice choice = response.choices().getFirst();
        ChatCompletionResponse.Usage usage = response.usage();
        log.info("Import mapping {} attempt {}: model {}, tokens in {} out {}",
                schemaName, attempt, openRouterProperties.getModel(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens());
        if (FINISH_CUT_OFF.equals(choice.finishReason())) {
            throw new ImportMappingException("The model's answer was cut off before it was complete");
        }
        return choice.message().content();
    }

    private static String reason(OpenRouterException upstream) {
        if (upstream.getCause() instanceof RestClientResponseException response) {
            return "upstream status " + response.getStatusCode().value();
        }
        if (upstream.getCause() != null) {
            return upstream.getCause().getClass().getSimpleName();
        }
        return "no response";
    }

    private static String resource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
