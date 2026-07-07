package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AuthorizationRequest {
    private final EntityUID principal;
    private final EntityUID action;
    private final EntityUID resource;
    private final Map<String, Object> context;
    private final Schema schema;
    private final boolean enableRequestValidation;

    private AuthorizationRequest(
            EntityUID principal,
            EntityUID action,
            EntityUID resource,
            Map<String, Object> context,
            Schema schema,
            boolean enableRequestValidation) {
        this.principal = Objects.requireNonNull(principal, "principal");
        this.action = Objects.requireNonNull(action, "action");
        this.resource = Objects.requireNonNull(resource, "resource");
        this.context =
                context != null
                        ? Collections.unmodifiableMap(new HashMap<>(context))
                        : Collections.emptyMap();
        this.schema = schema;
        this.enableRequestValidation = enableRequestValidation;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("principal")
    public EntityUID principal() {
        return principal;
    }

    @JsonProperty("action")
    public EntityUID action() {
        return action;
    }

    @JsonProperty("resource")
    public EntityUID resource() {
        return resource;
    }

    @JsonProperty("context")
    public Map<String, Object> context() {
        return context;
    }

    @JsonIgnore
    public Schema schema() {
        return schema;
    }

    @JsonProperty("validateRequest")
    public boolean isRequestValidationEnabled() {
        return enableRequestValidation;
    }

    @Override
    public String toString() {
        return "Request(" + principal + ", " + action + ", " + resource + ")";
    }

    public static final class Builder {
        private EntityUID principal;
        private EntityUID action;
        private EntityUID resource;
        private Map<String, Object> context;
        private Schema schema;
        private boolean enableRequestValidation;

        private Builder() {}

        public Builder principal(EntityUID principal) {
            this.principal = principal;
            return this;
        }

        public Builder principal(String type, String id) {
            this.principal = EntityUID.of(type, id);
            return this;
        }

        public Builder action(EntityUID action) {
            this.action = action;
            return this;
        }

        public Builder action(String type, String id) {
            this.action = EntityUID.of(type, id);
            return this;
        }

        public Builder resource(EntityUID resource) {
            this.resource = resource;
            return this;
        }

        public Builder resource(String type, String id) {
            this.resource = EntityUID.of(type, id);
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public Builder schema(Schema schema) {
            this.schema = schema;
            return this;
        }

        public Builder enableRequestValidation(boolean enable) {
            this.enableRequestValidation = enable;
            return this;
        }

        public AuthorizationRequest build() {
            return new AuthorizationRequest(
                    principal, action, resource, context,
                    schema, enableRequestValidation);
        }
    }
}
