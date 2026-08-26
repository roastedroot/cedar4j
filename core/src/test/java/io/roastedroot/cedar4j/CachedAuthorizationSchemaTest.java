package io.roastedroot.cedar4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The stateful FFI call only accepts a preparsed schema name, never an inline schema. */
public class CachedAuthorizationSchemaTest {

    private static final String CEDAR_SCHEMA =
            "entity User;\n"
                    + "entity Doc;\n"
                    + "action view appliesTo { principal: [User], resource: [Doc],"
                    + " context: { owner: { type: String, id: String } } };\n";

    private static CedarEngine engine;

    @BeforeAll
    static void setUp() {
        engine = CedarEngine.create();
        engine.cacheSchema("s", Schema.fromCedar(CEDAR_SCHEMA));
        engine.cachePolicySet(
                "ps",
                PolicySet.of(
                        Policy.of(
                                "permit(principal, action, resource) when { context.owner =="
                                        + " User::\"admin\" };",
                                "p1")));
    }

    private static Map<String, Object> smuggledContext() {
        Map<String, Object> euid = new LinkedHashMap<>();
        euid.put("type", "User");
        euid.put("id", "admin");
        Map<String, Object> context = new HashMap<>();
        context.put("owner", Collections.singletonMap("__entity", euid));
        return context;
    }

    private static AuthorizationRequest.Builder attacker() {
        return AuthorizationRequest.builder()
                .principal("User", "attacker")
                .action("Action", "view")
                .resource("Doc", "d1")
                .context(smuggledContext());
    }

    @Test
    void rejectsAnInlineSchemaInsteadOfDroppingIt() {
        AuthorizationRequest request = attacker().schema(Schema.fromCedar(CEDAR_SCHEMA)).build();

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> engine.isAuthorizedCached(request, "ps", Collections.emptySet()));

        assertTrue(
                e.getMessage().contains("cacheSchema"),
                "the error should point at the supported route, got: " + e.getMessage());
    }

    @Test
    void acceptsAPreparsedSchemaId() {
        AuthorizationResponse response =
                engine.isAuthorizedCached(attacker().build(), "ps", "s", Collections.emptySet());

        assertFalse(response.isSuccess(), "the cached schema must reject the smuggled escape");
        assertEquals(Decision.DENY, response.decision());
    }

    @Test
    void aRequestWithoutASchemaIsUnaffected() {
        AuthorizationResponse response =
                engine.isAuthorizedCached(attacker().build(), "ps", Collections.emptySet());

        assertTrue(response.isSuccess(), "errors: " + response.errors());
    }

    @Test
    void anInlineSchemaIsFineWhenASchemaIdIsAlsoGiven() {
        AuthorizationRequest request = attacker().schema(Schema.fromCedar(CEDAR_SCHEMA)).build();

        AuthorizationResponse response =
                engine.isAuthorizedCached(request, "ps", "s", Collections.emptySet());

        assertEquals(Decision.DENY, response.decision());
    }
}
