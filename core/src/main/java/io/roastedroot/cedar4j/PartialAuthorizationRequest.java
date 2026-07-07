package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public final class PartialAuthorizationRequest {
    private final EntityUID principal;
    private final EntityUID action;
    private final EntityUID resource;
    private final Map<String, Object> context;

    private PartialAuthorizationRequest(
            EntityUID principal,
            EntityUID action,
            EntityUID resource,
            Map<String, Object> context) {
        this.principal = principal;
        this.action = action;
        this.resource = resource;
        this.context =
                context != null
                        ? Collections.unmodifiableMap(new java.util.HashMap<>(context))
                        : Collections.emptyMap();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartialAuthorizationRequest)) return false;
        PartialAuthorizationRequest that = (PartialAuthorizationRequest) o;
        return Objects.equals(principal, that.principal)
                && Objects.equals(action, that.action)
                && Objects.equals(resource, that.resource)
                && Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principal, action, resource, context);
    }

    @Override
    public String toString() {
        return "PartialAuthorizationRequest(principal="
                + principal
                + ", action="
                + action
                + ", resource="
                + resource
                + ")";
    }

    public static final class Builder {
        private EntityUID principal;
        private EntityUID action;
        private EntityUID resource;
        private Map<String, Object> context;

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

        public PartialAuthorizationRequest build() {
            return new PartialAuthorizationRequest(principal, action, resource, context);
        }
    }
}
