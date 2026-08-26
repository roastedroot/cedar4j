package io.roastedroot.cedar4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code cedar_preparse_schema} deserializes its argument as JSON, and the Cedar-format variant of
 * the FFI schema type is a JSON <em>string</em> — so the raw schema text has to be JSON-encoded on
 * the way in, exactly as the non-cached path already does.
 */
public class SchemaCacheTest {

    private static final String CEDAR_SCHEMA =
            "entity User;\n"
                    + "entity Doc;\n"
                    + "action view appliesTo { principal: [User], resource: [Doc],"
                    + " context: { owner: { type: String, id: String } } };\n";

    private static final String JSON_SCHEMA =
            "{\"\":{\"entityTypes\":{\"User\":{},\"Doc\":{}},"
                    + "\"actions\":{\"view\":{\"appliesTo\":{\"principalTypes\":[\"User\"],"
                    + "\"resourceTypes\":[\"Doc\"]}}}}}";

    private static CedarEngine engine;

    @BeforeAll
    static void setUp() {
        engine = CedarEngine.create();
    }

    @Test
    void cachesACedarFormatSchema() {
        engine.cacheSchema("cedar-fmt", Schema.fromCedar(CEDAR_SCHEMA));
    }

    @Test
    void cachesAJsonFormatSchema() {
        engine.cacheSchema("json-fmt", Schema.fromJson(JSON_SCHEMA));
    }

    @Test
    void aCachedCedarSchemaActuallyInformsContextParsing() {
        engine.cacheSchema("guarding", Schema.fromCedar(CEDAR_SCHEMA));
        engine.cachePolicySet(
                "ps",
                PolicySet.of(
                        Policy.of(
                                "permit(principal, action, resource) when { context.owner =="
                                        + " User::\"admin\" };",
                                "p1")));

        // The reserved __entity key would otherwise be read as an entity reference and match.
        Map<String, Object> euid = new LinkedHashMap<>();
        euid.put("type", "User");
        euid.put("id", "admin");
        Map<String, Object> context = new HashMap<>();
        context.put("owner", Collections.singletonMap("__entity", euid));

        AuthorizationRequest request =
                AuthorizationRequest.builder()
                        .principal("User", "attacker")
                        .action("Action", "view")
                        .resource("Doc", "d1")
                        .context(context)
                        .build();

        AuthorizationResponse response =
                engine.isAuthorizedCached(request, "ps", "guarding", Collections.emptySet());

        assertFalse(response.isSuccess(), "the schema must reject the smuggled escape");
        assertEquals(Decision.DENY, response.decision());
    }

    @Test
    void reportsAnInvalidCedarSchema() {
        CedarException e =
                org.junit.jupiter.api.Assertions.assertThrows(
                        CedarException.class,
                        () -> engine.cacheSchema("bad", Schema.fromCedar("this is not a schema")));
        assertTrue(
                e.getMessage().contains("schema") || e.getMessage().contains("parse"),
                "expected a schema parse complaint, got: " + e.getMessage());
    }
}
