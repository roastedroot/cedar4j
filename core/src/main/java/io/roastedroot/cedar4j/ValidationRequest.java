package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;

public final class ValidationRequest {
    private final Object schema;
    private final PolicySet policies;

    private ValidationRequest(Object schema, PolicySet policies) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("schema")
    Object getSchema() {
        return schema;
    }

    @JsonProperty("policies")
    PolicySet getSerializedPolicies() {
        return policies;
    }

    public static final class Builder {
        private Schema schema;
        private PolicySet policies;

        private Builder() {}

        public Builder schema(Schema schema) {
            this.schema = schema;
            return this;
        }

        public Builder policies(PolicySet policies) {
            this.policies = policies;
            return this;
        }

        public ValidationRequest build() {
            Objects.requireNonNull(schema, "schema");
            Objects.requireNonNull(policies, "policies");
            Object schemaValue;
            if (schema.format() == Schema.Format.CEDAR) {
                schemaValue = schema.text();
            } else {
                try {
                    schemaValue = CedarEngine.DEFAULT_MAPPER.readTree(schema.text());
                } catch (JsonProcessingException e) {
                    throw new CedarException("Invalid JSON schema", e);
                }
            }
            return new ValidationRequest(schemaValue, policies);
        }
    }
}
