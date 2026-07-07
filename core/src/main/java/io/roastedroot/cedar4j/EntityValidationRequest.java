package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;

public final class EntityValidationRequest {
    private final Object schema;
    private final Set<Entity> entities;

    private EntityValidationRequest(Object schema, Set<Entity> entities) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.entities = Objects.requireNonNull(entities, "entities");
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("schema")
    Object getSchema() {
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
            Object schemaValue;
            if (schema.format() == Schema.Format.CEDAR) {
                schemaValue = schema.text();
            } else {
                try {
                    schemaValue = CedarEngine.DEFAULT_MAPPER.readTree(schema.text());
                } catch (Exception e) {
                    throw new CedarException("Invalid JSON schema", e);
                }
            }
            return new EntityValidationRequest(schemaValue, entities);
        }
    }
}
