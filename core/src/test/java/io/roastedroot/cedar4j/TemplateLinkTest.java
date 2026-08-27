package io.roastedroot.cedar4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The FFI declares template link values as a map from slot id to entity uid. */
public class TemplateLinkTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static CedarEngine engine;

    @BeforeAll
    static void setUp() {
        engine = CedarEngine.create();
    }

    private static PolicySet linkedTo(String principalId) {
        return PolicySet.of(
                Collections.emptySet(),
                Set.of(Policy.of("permit(principal == ?principal, action, resource);", "t1")),
                List.of(
                        TemplateLink.of(
                                "t1",
                                "linked",
                                List.of(
                                        TemplateLink.LinkValue.of(
                                                "?principal",
                                                EntityUID.of("User", principalId))))));
    }

    private static AuthorizationResponse authorizeAs(String principalId, PolicySet policies) {
        return engine.isAuthorized(
                AuthorizationRequest.builder()
                        .principal("User", principalId)
                        .action("Action", "view")
                        .resource("Doc", "d1")
                        .build(),
                policies,
                Collections.emptySet());
    }

    @Test
    void linkedTemplateAllowsTheLinkedPrincipal() {
        AuthorizationResponse response = authorizeAs("alice", linkedTo("alice"));

        assertTrue(response.isSuccess(), "errors: " + response.errors());
        assertEquals(Decision.ALLOW, response.decision());
        assertTrue(
                response.reasons().contains("linked"),
                "expected the linked policy id in reasons, got: " + response.reasons());
    }

    @Test
    void linkedTemplateDeniesEveryoneElse() {
        AuthorizationResponse response = authorizeAs("mallory", linkedTo("alice"));

        assertTrue(response.isSuccess(), "errors: " + response.errors());
        assertEquals(Decision.DENY, response.decision());
        assertFalse(response.isAllowed());
    }

    @Test
    void linkValuesSerializeAsAMapKeyedBySlot() throws Exception {
        JsonNode values =
                MAPPER.valueToTree(linkedTo("alice")).get("templateLinks").get(0).get("values");

        assertTrue(values.isObject(), "values must be a map, got: " + values);
        assertEquals("User", values.get("?principal").get("type").asText());
        assertEquals("alice", values.get("?principal").get("id").asText());
    }

    @Test
    void aHostileEntityIdStaysDataAcrossTheLink() {
        String hostile = "nobody\" || true || principal == User::\"x";

        assertEquals(Decision.DENY, authorizeAs("mallory", linkedTo(hostile)).decision());
        assertEquals(Decision.ALLOW, authorizeAs(hostile, linkedTo(hostile)).decision());
    }

    @Test
    void duplicateSlotsAreRejectedAtConstruction() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                TemplateLink.of(
                                        "t1",
                                        "linked",
                                        List.of(
                                                TemplateLink.LinkValue.of(
                                                        "?principal", EntityUID.of("User", "a")),
                                                TemplateLink.LinkValue.of(
                                                        "?principal", EntityUID.of("User", "b")))));
        assertTrue(
                e.getMessage().contains("?principal"),
                "expected the offending slot named, got: " + e.getMessage());
    }

    @Test
    void publicAccessorStillExposesTheList() {
        TemplateLink link =
                TemplateLink.of(
                        "t1",
                        "linked",
                        List.of(
                                TemplateLink.LinkValue.of(
                                        "?principal", EntityUID.of("User", "alice"))));

        assertEquals(1, link.linkValues().size());
        assertEquals("?principal", link.linkValues().get(0).slot());
    }
}
