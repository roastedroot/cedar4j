package io.roastedroot.cedar4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Some Wasm exports answer with a bare {@code ERROR:<message>} instead of the JSON envelope. */
public class WasmErrorReportingTest {

    /** Stands in for marshalling drift that hands the module something unparseable. */
    private static final class BrokenMapper extends ObjectMapper {
        @Override
        public String writeValueAsString(Object value) {
            return "not json";
        }
    }

    @Test
    void rawErrorSurfacesAsCedarExceptionCarryingTheModuleMessage() {
        CedarEngine engine = CedarEngine.builder().withObjectMapper(new BrokenMapper()).build();
        PolicySet policies = PolicySet.of(Policy.of("permit(principal,action,resource);", "p0"));

        CedarException e =
                assertThrows(CedarException.class, () -> engine.cachePolicySet("ps", policies));

        assertTrue(
                e.getMessage().contains("expected ident"),
                "expected the module's own message, got: " + e.getMessage());
        assertFalse(
                e.getMessage().contains("Failed to serialize"),
                "the failure is the module rejecting the payload, not a serialization error");
    }

    @Test
    void cacheSchemaRawErrorAlsoSurfaces() {
        CedarEngine engine = CedarEngine.builder().withObjectMapper(new BrokenMapper()).build();

        CedarException e =
                assertThrows(
                        CedarException.class,
                        () -> engine.cacheSchema("s", Schema.fromJson("{\"\":{}}")));

        assertTrue(
                e.getMessage().contains("expected ident"),
                "expected the module's own message, got: " + e.getMessage());
    }

    @Test
    void rawEngineKeepsReturningThePrefixedStringVerbatim() {
        CedarEngine engine = CedarEngine.create();

        String result = engine.raw().preParsePolicySet("x", "not json");

        assertTrue(
                result.startsWith("ERROR:"), "the raw escape hatch must stay raw, got: " + result);
    }
}
