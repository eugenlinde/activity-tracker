package dev.eugenlinde.activitytracker.importer.llm;

import dev.eugenlinde.activitytracker.common.exception.ImportMappingException;
import dev.eugenlinde.activitytracker.importer.csv.CsvText;
import dev.eugenlinde.activitytracker.importer.csv.DateOrder;
import dev.eugenlinde.activitytracker.importer.csv.GridText;
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
    private static final List<List<String>> SHEET = CsvText.parse("""
            KAVA,,,,,,,,,,,
            ,,,Nädal 1,,,,,Nädal 2,,,
            ,Treening 1,,Raskus,Varus,Kordused,Kommentaar,,Raskus,Varus,Kordused,Kommentaar
            5-8,Kükk,,100,3,"8, 7",,,"102,5",1-2,"8, 7, 6",RP
            8-12,Surumine,,60,3,"10, 9",,,60,1-2,"10, 9",KP
            ,,,,,,,,,,,
            ,Treening 2,,,,,,,,,,
            10-15,Lõuatõmbed,,0,3,"12, 12",,,,,,
            """);
    private static final String LAYOUT_ANSWER = """
            {"nameColumn":1,"targetRepsColumn":0,"videoColumn":2,
             "weeks":[{"label":"Nädal 1","weightColumn":3,"repsColumn":5,"repsInReserveColumn":4,"commentColumn":6},
                      {"label":"Nädal 2","weightColumn":8,"repsColumn":10,"repsInReserveColumn":9,"commentColumn":11}],
             "workouts":[{"label":"Treening 1","firstRow":4,"lastRow":5},{"label":"Treening 2","firstRow":8,"lastRow":8}]}
            """;
    private static final String LAYOUT_ROWS_BACKWARDS = LAYOUT_ANSWER.replace("\"firstRow\":4,\"lastRow\":5", "\"firstRow\":5,\"lastRow\":4");
    private static final String LAYOUT_NOT_A_GRID = """
            {"nameColumn":0,"targetRepsColumn":null,"videoColumn":null,"weeks":[],"workouts":[]}
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
    void gridLayout_validAnswer_returnsTheLayoutInOneCall() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("grid_layout"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.messages[1].content").value(GridText.indexed(SHEET)))
                .andRespond(withSuccess(completion(LAYOUT_ANSWER, "stop"), MediaType.APPLICATION_JSON));

        GridLayout layout = service.gridLayout(SHEET);

        assertEquals(new GridLayout(1, 0, 2,
                List.of(new GridLayout.Week("Nädal 1", 3, 5, 4, 6), new GridLayout.Week("Nädal 2", 8, 10, 9, 11)),
                List.of(new GridLayout.Workout("Treening 1", 4, 5), new GridLayout.Workout("Treening 2", 8, 8))
        ), layout);
    }

    @Test
    void gridLayout_sheetWithoutThisLayout_isAnEmptyLayout() {
        answer(LAYOUT_NOT_A_GRID);

        GridLayout layout = service.gridLayout(SHEET);

        assertTrue(layout.weeks().isEmpty());
        assertTrue(layout.workouts().isEmpty());
    }

    @Test
    void gridLayout_workoutRowsBackwards_isSentBackOnceThenAccepted() {
        answer(LAYOUT_ROWS_BACKWARDS);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[3].content", containsString("firstRow")))
                .andRespond(withSuccess(completion(LAYOUT_ANSWER, "stop"), MediaType.APPLICATION_JSON));

        assertEquals(2, service.gridLayout(SHEET).workouts().size());
    }

    @Test
    void gridLayout_requiredFieldMissing_isSentBackInsteadOfCrashing() {
        answer("{\"nameColumn\":null,\"targetRepsColumn\":null,\"videoColumn\":null,\"weeks\":[null],\"workouts\":null}");
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[3].content", containsString("nameColumn")))
                .andRespond(withSuccess(completion(LAYOUT_ANSWER, "stop"), MediaType.APPLICATION_JSON));

        assertEquals(2, service.gridLayout(SHEET).workouts().size());
    }

    @Test
    void gridLayout_invalidTwice_failsAfterExactlyTwoCalls() {
        server.expect(ExpectedCount.times(2), requestTo(URL))
                .andRespond(withSuccess(completion(LAYOUT_ROWS_BACKWARDS, "stop"), MediaType.APPLICATION_JSON));

        assertThrows(ImportMappingException.class, () -> service.gridLayout(SHEET));
    }

    @Test
    void gridLayout_answerIsNotJsonTwice_fails() {
        server.expect(ExpectedCount.times(2), requestTo(URL))
                .andRespond(withSuccess(completion("Here is your layout!", "stop"), MediaType.APPLICATION_JSON));

        assertThrows(ImportMappingException.class, () -> service.gridLayout(SHEET));
    }

    @Test
    void gridLayout_answerCutOff_isAModelFailureWithoutRetry() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess(completion("{\"nameColumn\":1,", "length"), MediaType.APPLICATION_JSON));

        assertThrows(ImportMappingException.class, () -> service.gridLayout(SHEET));
    }

    @Test
    void gridLayout_noChoices_fails() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThrows(ImportMappingException.class, () -> service.gridLayout(SHEET));
    }

    @Test
    void gridLayout_upstreamError_failsWithoutRetryAndKeepsTheUpstreamBodyOutOfTheMessage() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withServerError().body("flagged input: Kükk kangiga 100 kg").contentType(MediaType.TEXT_PLAIN));

        ImportMappingException failure = assertThrows(ImportMappingException.class, () -> service.gridLayout(SHEET));

        assertTrue(failure.getMessage().contains("500"), failure.getMessage());
        assertFalse(failure.getMessage().contains("Kükk"), failure.getMessage());
    }

    @Test
    void gridLayout_allowsRoomForAModelThatThinksBeforeAnswering() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.max_tokens").value(8000))
                .andRespond(withSuccess(completion(LAYOUT_ANSWER, "stop"), MediaType.APPLICATION_JSON));

        service.gridLayout(SHEET);
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
        JsonNode layout = service.gridLayoutSchema();
        JsonNode week = layout.at("/properties/weeks/items");
        JsonNode workout = layout.at("/properties/workouts/items");
        JsonNode analysis = service.analysisSchema();
        JsonNode table = analysis.at("/properties/table");

        assertEquals(components(GridLayout.class), required(layout));
        assertEquals(components(GridLayout.Week.class), required(week));
        assertEquals(components(GridLayout.Workout.class), required(workout));
        assertEquals(components(FileAnalysis.class), required(analysis));
        assertEquals(components(TableMapping.class), required(table));
        for (JsonNode object : List.of(layout, week, workout, analysis, table)) {
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
