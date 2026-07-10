package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;

public final class EntityValidationRequest {
    private final Schema schema;
    private final Set<Entity> entities;

    private EntityValidationRequest(Schema schema, Set<Entity> entities) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.entities = Objects.requireNonNull(entities, "entities");
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnore
    Schema schema() {
        return schema;
    }

    @JsonProperty("entities")
    Set<Entity> getEntities() {
        return entities;
    }

    public static final class Builder {
        private Schema schema;
        private Set<Entity> entities;

        private Builder() {}

        public Builder schema(Schema schema) {
            this.schema = schema;
            return this;
        }

        public Builder entities(Set<Entity> entities) {
            this.entities = entities;
            return this;
        }

        public EntityValidationRequest build() {
            Objects.requireNonNull(schema, "schema");
            Objects.requireNonNull(entities, "entities");
            return new EntityValidationRequest(schema, entities);
        }
    }
}
