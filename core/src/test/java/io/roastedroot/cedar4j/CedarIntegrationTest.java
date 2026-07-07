package io.roastedroot.cedar4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

public class CedarIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path CACHE_DIR = Path.of(".cache", "cedar-integration-tests-main");
    private static CedarEngine engine;

    @BeforeAll
    static void setUp() {
        engine = CedarEngine.create();
    }

    @TestFactory
    List<DynamicContainer> handwrittenTests() throws Exception {
        Path testsDir = CACHE_DIR.resolve("tests");
        assumeTrue(Files.isDirectory(testsDir), "Integration tests not downloaded");

        List<DynamicContainer> containers = new ArrayList<>();
        try (Stream<Path> dirs = Files.walk(testsDir)) {
            List<Path> jsonFiles =
                    dirs.filter(p -> p.toString().endsWith(".json"))
                            .sorted()
                            .collect(Collectors.toList());
            for (Path jsonFile : jsonFiles) {
                TestSpec spec = MAPPER.readValue(jsonFile.toFile(), TestSpec.class);
                if (spec.policies == null || spec.requests == null) {
                    continue;
                }
                containers.add(buildTestContainer(jsonFile, spec));
            }
        }
        return containers;
    }

    @TestFactory
    List<DynamicContainer> corpusTests() throws Exception {
        Path corpusDir = CACHE_DIR.resolve("corpus-tests");
        if (!Files.isDirectory(corpusDir)) {
            extractCorpusTests();
        }
        assumeTrue(Files.isDirectory(corpusDir), "Corpus tests not available");

        List<DynamicContainer> containers = new ArrayList<>();
        try (Stream<Path> files = Files.list(corpusDir)) {
            List<Path> jsonFiles =
                    files.filter(
                                    p ->
                                            p.toString().endsWith(".json")
                                                    && !p.toString().endsWith(".entities.json"))
                            .sorted()
                            .collect(Collectors.toList());
            for (Path jsonFile : jsonFiles) {
                TestSpec spec = MAPPER.readValue(jsonFile.toFile(), TestSpec.class);
                if (spec.policies == null || spec.requests == null) {
                    continue;
                }
                containers.add(buildTestContainer(jsonFile, spec));
            }
        }
        return containers;
    }

    private DynamicContainer buildTestContainer(Path jsonFile, TestSpec spec) throws IOException {
        Path baseDir = CACHE_DIR;

        String policiesRaw = Files.readString(baseDir.resolve(spec.policies));
        JsonNode entitiesNode = MAPPER.readTree(baseDir.resolve(spec.entities).toFile());

        JsonNode schemaNode = null;
        if (spec.schema != null) {
            Path schemaPath = baseDir.resolve(spec.schema);
            if (Files.exists(schemaPath)) {
                String schemaText = Files.readString(schemaPath);
                if (spec.schema.endsWith(".json")) {
                    schemaNode = MAPPER.readTree(schemaText);
                } else {
                    schemaNode = MAPPER.getNodeFactory().textNode(schemaText);
                }
            }
        }

        JsonNode policiesNode = buildPoliciesNode(policiesRaw, spec.policies);

        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < spec.requests.size(); i++) {
            TestRequest req = spec.requests.get(i);
            String testName = req.description != null ? req.description : "request " + i;
            JsonNode finalSchema = schemaNode;
            tests.add(
                    DynamicTest.dynamicTest(
                            testName,
                            () -> executeRequest(policiesNode, entitiesNode, finalSchema, req)));
        }

        String containerName = CACHE_DIR.relativize(jsonFile).toString().replace(".json", "");
        return DynamicContainer.dynamicContainer(containerName, tests);
    }

    private JsonNode buildPoliciesNode(String policiesRaw, String policiesPath) throws IOException {
        if (policiesPath.endsWith(".json")) {
            return MAPPER.readTree(policiesRaw);
        }

        String parsedJson = engine.raw().parsePolicies(policiesRaw);
        if (parsedJson.startsWith("ERROR:")) {
            throw new IOException("Failed to parse policies: " + parsedJson);
        }
        JsonNode parsed = MAPPER.readTree(parsedJson);

        ObjectNode result = MAPPER.createObjectNode();
        ObjectNode staticPolicies = result.putObject("staticPolicies");
        for (JsonNode p : parsed.get("policies")) {
            staticPolicies.put(p.get("id").asText(), p.get("text").asText());
        }
        ObjectNode templates = result.putObject("templates");
        if (parsed.has("templates")) {
            for (JsonNode t : parsed.get("templates")) {
                templates.put(t.get("id").asText(), t.get("text").asText());
            }
        }
        result.putArray("templateLinks");
        return result;
    }

    private void executeRequest(
            JsonNode policiesNode, JsonNode entitiesNode, JsonNode schemaNode, TestRequest req)
            throws Exception {
        ObjectNode request = MAPPER.createObjectNode();
        request.set("principal", MAPPER.valueToTree(req.principal));
        request.set("action", MAPPER.valueToTree(req.action));
        request.set("resource", MAPPER.valueToTree(req.resource));
        request.set("context", req.context != null ? req.context : MAPPER.createObjectNode());
        if (schemaNode != null) {
            request.set("schema", schemaNode);
        }
        request.set("policies", policiesNode);
        request.set("entities", entitiesNode);

        String resultJson = engine.raw().authorize(MAPPER.writeValueAsString(request));
        AuthorizationResponse response = MAPPER.readValue(resultJson, AuthorizationResponse.class);

        assertTrue(response.isSuccess(), "Authorization call failed for: " + req.description);

        Decision expectedDecision = "allow".equals(req.decision) ? Decision.ALLOW : Decision.DENY;
        assertEquals(
                expectedDecision, response.decision(), "Decision mismatch for: " + req.description);

        Set<String> expectedReasons = new HashSet<>(req.reason);
        assertEquals(
                expectedReasons, response.reasons(), "Reasons mismatch for: " + req.description);

        assertEquals(
                req.errors.size(),
                response.errors().size(),
                "Error count mismatch for: " + req.description);
    }

    private static void extractCorpusTests() throws IOException {
        Path tarGz = CACHE_DIR.resolve("corpus-tests.tar.gz");
        if (!Files.exists(tarGz)) {
            return;
        }
        try (InputStream fi = Files.newInputStream(tarGz);
                BufferedInputStream bi = new BufferedInputStream(fi);
                GzipCompressorInputStream gzi = new GzipCompressorInputStream(bi);
                TarArchiveInputStream tai = new TarArchiveInputStream(gzi)) {
            TarArchiveEntry entry;
            while ((entry = tai.getNextEntry()) != null) {
                Path dest = CACHE_DIR.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(tai, dest);
                }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TestSpec {
        @JsonProperty public String policies;
        @JsonProperty public String entities;
        @JsonProperty public String schema;
        @JsonProperty public Boolean shouldValidate;
        @JsonProperty public List<TestRequest> requests;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TestRequest {
        @JsonProperty public String description;
        @JsonProperty public EntityUID principal;
        @JsonProperty public EntityUID action;
        @JsonProperty public EntityUID resource;
        @JsonProperty public JsonNode context;
        @JsonProperty public String decision;
        @JsonProperty public List<String> reason;
        @JsonProperty public List<String> errors;
    }
}
