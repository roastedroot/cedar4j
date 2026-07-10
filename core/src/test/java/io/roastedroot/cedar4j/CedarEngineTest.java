package io.roastedroot.cedar4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CedarEngineTest {

    private static CedarEngine engine;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        engine = CedarEngine.create();
    }

    @Test
    void cedarVersion() {
        assertEquals("4.11.2", engine.version());
    }

    @Test
    void permit() {
        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .build();

        PolicySet policies = PolicySet.of(Policy.of("permit(principal,action,resource);", "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());

        assertEquals(Decision.ALLOW, response.decision());
        assertTrue(response.isAllowed());
        assertTrue(response.reasons().contains("p0"));
    }

    @Test
    void forbid() {
        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .build();

        PolicySet policies = PolicySet.of(Policy.of("forbid(principal,action,resource);", "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());

        assertEquals(Decision.DENY, response.decision());
        assertFalse(response.isAllowed());
    }

    @Test
    void withContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("authenticated", true);

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .context(context)
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { context.authenticated };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());

        assertTrue(response.isAllowed());
    }

    @Test
    void withEntities() {
        EntityUID aliceUid = EntityUID.of("User", "alice");
        EntityUID adminGroup = EntityUID.of("Group", "admins");

        Entity alice = Entity.of(aliceUid, Set.of(adminGroup));
        Entity group = Entity.of(adminGroup);

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal(aliceUid)
                        .action("Action", "delete")
                        .resource("Resource", "doc1")
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { principal in Group::\"admins\" };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Set.of(alice, group));

        assertTrue(response.isAllowed());
    }

    @Test
    void forbidWithDiagnosticErrors() {
        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal,action,resource) when"
                                        + " { principal.missing_attr };",
                                "p0"));

        AuthorizationResponse response =
                engine.isAuthorized(request, policies, Collections.emptySet());

        assertTrue(response.isSuccess());
        assertFalse(response.isAllowed());
        assertFalse(response.errors().isEmpty());
    }

    @Test
    void nullRequestThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        engine.isAuthorized(
                                null,
                                PolicySet.of(Policy.of("permit(principal,action,resource);", "p0")),
                                Collections.emptySet()));
    }

    @Test
    void parsePolicy() {
        String result = engine.raw().parsePolicy("permit(principal,action,resource);");
        assertFalse(result.startsWith("ERROR:"), "Parse failed: " + result);
        assertTrue(result.contains("permit"), "Expected permit in parsed output: " + result);
    }

    @Test
    void parsePolicyInvalid() {
        String result = engine.raw().parsePolicy("not a valid policy");
        assertTrue(result.startsWith("ERROR:"), "Expected error for invalid policy");
    }

    @Test
    void policyEffect() {
        assertEquals("permit", engine.raw().policyEffect("permit(principal,action,resource);"));
        assertEquals("forbid", engine.raw().policyEffect("forbid(principal,action,resource);"));
    }

    @Test
    void parseTemplate() {
        String result =
                engine.raw()
                        .parseTemplate("permit(principal==?principal,action,resource==?resource);");
        assertFalse(result.startsWith("ERROR:"), "Parse failed: " + result);
    }

    @Test
    void templateEffect() {
        assertEquals(
                "permit",
                engine.raw()
                        .templateEffect(
                                "permit(principal==?principal,action,resource==?resource);"));
    }

    @Test
    void policyToJsonAndBack() {
        String policyText = "permit(principal,action,resource);";
        String json = engine.raw().policyToJson(policyText);
        assertFalse(json.startsWith("ERROR:"), "toJson failed: " + json);

        String roundTripped = engine.raw().policyFromJson(json);
        assertFalse(roundTripped.startsWith("ERROR:"), "fromJson failed: " + roundTripped);
        assertTrue(roundTripped.contains("permit"));
    }

    @Test
    void policyAnnotations() {
        String policy = "@id(\"myPolicy\") @myKey(\"myValue\") permit(principal,action,resource);";
        String annotations = engine.raw().policyAnnotations(policy);
        assertFalse(annotations.startsWith("ERROR:"), "annotations failed: " + annotations);
        assertTrue(annotations.contains("\"id\""), "Expected 'id' annotation");
        assertTrue(annotations.contains("\"myPolicy\""), "Expected 'myPolicy' value");
        assertTrue(annotations.contains("\"myKey\""), "Expected 'myKey' annotation");
    }

    @Test
    void parseJsonSchema() throws Exception {
        ObjectNode schema = MAPPER.createObjectNode();
        ObjectNode ns = schema.putObject("");
        ObjectNode entityTypes = ns.putObject("entityTypes");
        entityTypes.putObject("User").putArray("memberOfTypes").add("Group");
        entityTypes.putObject("Group");
        entityTypes.putObject("File");
        ObjectNode actions = ns.putObject("actions");
        ObjectNode read = actions.putObject("read").putObject("appliesTo");
        read.putArray("principalTypes").add("User");
        read.putArray("resourceTypes").add("File");

        String result = engine.raw().parseJsonSchema(MAPPER.writeValueAsString(schema));
        assertEquals("success", result);
    }

    @Test
    void parseCedarSchema() {
        String schema =
                "entity User = { name: String };\n"
                        + "entity Photo;\n"
                        + "action view appliesTo {\n"
                        + "    principal: [User],\n"
                        + "    resource: [Photo]\n"
                        + "};";
        String result = engine.raw().parseCedarSchema(schema);
        assertEquals("success", result);
    }

    @Test
    void formatPolicies() {
        String result = engine.raw().formatPolicies("permit(principal,action,resource);");
        assertFalse(result.startsWith("ERROR:"), "format failed: " + result);
        assertTrue(result.contains("permit"));
    }

    @Test
    void typedValidation() {
        Schema schema =
                Schema.fromCedar(
                        "entity User;\n"
                                + "entity Photo;\n"
                                + "action viewPhoto appliesTo {\n"
                                + "    principal: [User],\n"
                                + "    resource: [Photo]\n"
                                + "};");

        ValidationRequest request =
                ValidationRequest.builder()
                        .schema(schema)
                        .policies(
                                PolicySet.of(Policy.of("permit(principal,action,resource);", "p0")))
                        .build();

        ValidationResponse response = engine.validate(request);
        assertTrue(response.isSuccess());
        assertTrue(response.isValid());
    }

    @Test
    void typedValidationWithErrors() {
        Schema schema =
                Schema.fromCedar(
                        "entity User;\n"
                                + "entity Photo;\n"
                                + "action viewPhoto appliesTo {\n"
                                + "    principal: [User],\n"
                                + "    resource: [Photo]\n"
                                + "};");

        ValidationRequest request =
                ValidationRequest.builder()
                        .schema(schema)
                        .policies(
                                PolicySet.of(
                                        Policy.of(
                                                "permit(principal == User::\"alice\","
                                                        + " action == Action::\"bogus\","
                                                        + " resource);",
                                                "p0")))
                        .build();

        ValidationResponse response = engine.validate(request);
        assertTrue(response.isSuccess());
        assertFalse(response.isValid());
        assertFalse(response.validationErrors().isEmpty());
    }

    @Test
    void typedEntityValidation() {
        Schema schema = Schema.fromCedar("entity User;\nentity Photo;\n");

        EntityValidationRequest request =
                EntityValidationRequest.builder()
                        .schema(schema)
                        .entities(Set.of(Entity.of(EntityUID.of("User", "alice"))))
                        .build();

        EntityValidationResponse response = engine.validateEntities(request);
        assertTrue(response.isSuccess());
    }

    @Test
    void partialAuthorization() {
        PartialAuthorizationRequest request =
                PartialAuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .build();

        PolicySet policies = PolicySet.of(Policy.of("permit(principal,action,resource);", "p0"));

        PartialAuthorizationResponse response =
                engine.isAuthorizedPartial(request, policies, Collections.emptySet());

        assertTrue(response.isSuccess());
        assertTrue(response.isAllowed());
    }

    @Test
    void partialAuthorizationWithMissingPrincipal() {
        PartialAuthorizationRequest request =
                PartialAuthorizationRequest.builder()
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .build();

        PolicySet policies =
                PolicySet.of(
                        Policy.of(
                                "permit(principal == User::\"alice\"," + "action,resource);",
                                "p0"));

        PartialAuthorizationResponse response =
                engine.isAuthorizedPartial(request, policies, Collections.emptySet());

        assertTrue(response.isSuccess());
    }

    @Test
    void typedCachedAuthorization() {
        PolicySet policies = PolicySet.of(Policy.of("permit(principal,action,resource);", "p0"));
        engine.cachePolicySet("cached-policies", policies);

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "alice")
                        .action("Action", "view")
                        .resource("Resource", "doc1")
                        .build();

        AuthorizationResponse response =
                engine.isAuthorizedCached(request, "cached-policies", Collections.emptySet());

        assertTrue(response.isAllowed());
    }
}
