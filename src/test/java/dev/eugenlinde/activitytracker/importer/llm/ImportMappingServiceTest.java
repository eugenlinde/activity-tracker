package dev.eugenlinde.activitytracker.importer.llm;

import dev.eugenlinde.activitytracker.common.exception.ImportMappingException;
import dev.eugenlinde.activitytracker.common.exception.ImportTooLargeException;
import dev.eugenlinde.activitytracker.importer.csv.DateOrder;
import dev.eugenlinde.activitytracker.openrouter.OpenRouterClient;
import dev.eugenlinde.activitytracker.openrouter.OpenRouterClientConfig;
import dev.eugenlinde.activitytracker.openrouter.OpenRouterProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ImportMappingServiceTest {

    private static final String URL = "http://openrouter.test/api/v1/chat/completions";
    private static final String TABLE_ANSWER = """
            {"kind":"TABLE","table":{"headerRows":1,"dateColumn":0,"dateOrder":"DMY","weightColumn":1,"stepsColumn":null}}
            """;
    private static final String TABLE_WITHOUT_VALUES = """
            {"kind":"TABLE","table":{"headerRows":1,"dateColumn":0,"dateOrder":"DMY","weightColumn":null,"stepsColumn":null}}
            """;
    private static final String WORKOUT_ANSWER = """
            {"drafts":[{"label":"Nädal 1 / Treening 1","notes":null,"exercises":[
              {"name":"Kükk","targetReps":"5-8","weight":100,"repsInReserve":"1-2","reps":[8,7,6],"comment":"raske","videoUrl":null}
            ]}]}
            """;
    private static final String WORKOUT_WITHOUT_REPS = """
            {"drafts":[{"label":"Nädal 1 / Treening 1","notes":null,"exercises":[
              {"name":"Kükk","targetReps":null,"weight":100,"repsInReserve":null,"reps":[],"comment":null,"videoUrl":null}
            ]}]}
            """;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private ImportMappingService service;

    @BeforeEach
    void setUp() {
        OpenRouterProperties properties = new OpenRouterProperties();
        properties.setBaseUrl("http://openrouter.test/api/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        RestClient configured = new OpenRouterClientConfig().openRouterRestClient(RestClient.builder(), properties);
        RestClient.Builder builder = configured.mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new ImportMappingService(new OpenRouterClient(builder.build()), properties, jsonMapper, validator);
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    private String completion(String content, String finishReason) {
        ObjectNode message = jsonMapper.createObjectNode().put("role", "assistant").put("content", content);
        ObjectNode choice = jsonMapper.createObjectNode().put("finish_reason", finishReason);
        choice.set("message", message);
        ObjectNode body = jsonMapper.createObjectNode();
        body.putArray("choices").add(choice);
        body.set("usage", jsonMapper.createObjectNode().put("prompt_tokens", 10).put("completion_tokens", 5).put("total_tokens", 15));
        return body.toString();
    }

    private void answer(String content) {
        server.expect(requestTo(URL)).andRespond(withSuccess(completion(content, "stop"), MediaType.APPLICATION_JSON));
    }

    @Test
    void analyse_sendsTheFileHeadWithAStrictSchemaAndReturnsTheMapping() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.temperature").value(0.0))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("file_analysis"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Kuupäev;Kaal\n01.11.2026;81,3"))
                .andRespond(withSuccess(completion(TABLE_ANSWER, "stop"), MediaType.APPLICATION_JSON));

        FileAnalysis analysis = service.analyse("Kuupäev;Kaal\n01.11.2026;81,3");

        assertEquals(new FileAnalysis(FileAnalysis.Kind.TABLE, new TableMapping(1, 0, DateOrder.DMY, 1, null)), analysis);
    }

    @Test
    void analyse_tableWithoutAnyValueColumn_isSentBackOnceThenAccepted() {
        answer(TABLE_WITHOUT_VALUES);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[3].role").value("user"))
                .andExpect(jsonPath("$.messages[3].content", containsString("weightColumn")))
                .andRespond(withSuccess(completion(TABLE_ANSWER, "stop"), MediaType.APPLICATION_JSON));

        assertEquals(FileAnalysis.Kind.TABLE, service.analyse("x").kind());
    }

    @Test
    void analyse_unknownKind_needsNoTable() {
        answer("{\"kind\":\"UNKNOWN\",\"table\":null}");

        assertEquals(new FileAnalysis(FileAnalysis.Kind.UNKNOWN, null), service.analyse("x"));
    }

    @Test
    void extractWorkouts_validAnswer_returnsDraftsInOneCall() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("workout_extraction"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.messages[1].content").value("the whole csv"))
                .andRespond(withSuccess(completion(WORKOUT_ANSWER, "stop"), MediaType.APPLICATION_JSON));

        WorkoutExtraction extraction = service.extractWorkouts("the whole csv");

        assertEquals(1, extraction.drafts().size());
        assertEquals("Nädal 1 / Treening 1", extraction.drafts().getFirst().label());
        assertEquals(List.of(8, 7, 6), extraction.drafts().getFirst().exercises().getFirst().reps());
        assertEquals(100.0, extraction.drafts().getFirst().exercises().getFirst().weight());
    }

    @Test
    void extractWorkouts_invalidThenValid_succeedsInExactlyTwoCallsAndNamesTheViolation() {
        answer(WORKOUT_WITHOUT_REPS);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[3].content", containsString("drafts[0].exercises[0].reps")))
                .andRespond(withSuccess(completion(WORKOUT_ANSWER, "stop"), MediaType.APPLICATION_JSON));

        assertEquals(1, service.extractWorkouts("csv").drafts().size());
    }

    @Test
    void extractWorkouts_invalidTwice_failsAfterExactlyTwoCalls() {
        server.expect(ExpectedCount.times(2), requestTo(URL))
                .andRespond(withSuccess(completion(WORKOUT_WITHOUT_REPS, "stop"), MediaType.APPLICATION_JSON));

        assertThrows(ImportMappingException.class, () -> service.extractWorkouts("csv"));
    }

    @Test
    void extractWorkouts_answerIsNotJsonTwice_fails() {
        server.expect(ExpectedCount.times(2), requestTo(URL))
                .andRespond(withSuccess(completion("Here are your workouts!", "stop"), MediaType.APPLICATION_JSON));

        assertThrows(ImportMappingException.class, () -> service.extractWorkouts("csv"));
    }

    @Test
    void extractWorkouts_answerCutOff_isReportedAsTooLargeWithoutRetry() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess(completion("{\"drafts\":[", "length"), MediaType.APPLICATION_JSON));

        assertThrows(ImportTooLargeException.class, () -> service.extractWorkouts("csv"));
    }

    @Test
    void extractWorkouts_noChoices_fails() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThrows(ImportMappingException.class, () -> service.extractWorkouts("csv"));
    }

    @Test
    void extractWorkouts_upstreamError_failsWithoutRetryAndKeepsTheUpstreamBodyOutOfTheMessage() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withServerError().body("flagged input: Kükk kangiga 100 kg").contentType(MediaType.TEXT_PLAIN));

        ImportMappingException failure = assertThrows(ImportMappingException.class, () -> service.extractWorkouts("csv"));

        assertTrue(failure.getMessage().contains("500"), failure.getMessage());
        assertFalse(failure.getMessage().contains("Kükk"), failure.getMessage());
    }

    @Test
    void analyse_allowsRoomForAModelThatThinksBeforeAnswering() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.max_tokens").value(1000))
                .andRespond(withSuccess(completion(TABLE_ANSWER, "stop"), MediaType.APPLICATION_JSON));

        service.analyse("x");
    }

    @Test
    void schemas_listExactlyTheRecordComponentsAsRequired() {
        JsonNode workouts = service.workoutSchema();
        JsonNode draft = workouts.at("/properties/drafts/items");
        JsonNode exercise = draft.at("/properties/exercises/items");
        JsonNode analysis = service.analysisSchema();
        JsonNode table = analysis.at("/properties/table");

        assertEquals(components(WorkoutExtraction.class), required(workouts));
        assertEquals(components(WorkoutExtraction.Draft.class), required(draft));
        assertEquals(components(WorkoutExtraction.DraftExercise.class), required(exercise));
        assertEquals(components(FileAnalysis.class), required(analysis));
        assertEquals(components(TableMapping.class), required(table));
        for (JsonNode object : List.of(workouts, draft, exercise, analysis, table)) {
            assertEquals(false, object.get("additionalProperties").asBoolean(true));
        }
    }

    private static List<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).sorted().toList();
    }

    private static List<String> required(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.get("required").forEach(name -> names.add(name.asString()));
        return names.stream().sorted().toList();
    }
}
