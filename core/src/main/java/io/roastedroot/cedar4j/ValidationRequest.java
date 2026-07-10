package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class ValidationRequest {
    private final Schema schema;
    private final PolicySet policies;

    private ValidationRequest(Schema schema, PolicySet policies) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnore
    Schema schema() {
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
            return new ValidationRequest(schema, policies);
        }
    }
}
