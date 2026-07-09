package io.roastedroot.cedar4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import java.util.Set;

public final class CedarEngine implements AutoCloseable {
    public static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    private final CedarWasm wasm;
    private final ObjectMapper mapper;
    private final CedarRawEngine rawEngine;

    private CedarEngine(CedarWasm wasm, ObjectMapper mapper) {
        this.wasm = wasm;
        this.mapper = mapper;
        this.rawEngine = new CedarRawEngine(wasm);
    }

    public static CedarEngine create() {
        return new CedarEngine(CedarWasm.create(), DEFAULT_MAPPER);
    }

    public static Builder builder() {
        return new Builder();
    }

    public CedarRawEngine raw() {
        return rawEngine;
    }

    public AuthorizationResponse isAuthorized(
            AuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policySet, "policySet");
        Objects.requireNonNull(entities, "entities");
        String json;
        try {
            ObjectNode root = mapper.valueToTree(request);
            serializeSchema(root, request.schema());
            root.set("policies", mapper.valueToTree(policySet));
            root.set("entities", mapper.valueToTree(entities));
            json = mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to serialize authorization request", e);
        }
        String result = wasm.authorize(json);
        try {
            return mapper.readValue(result, AuthorizationResponse.class);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to parse authorization response", e);
        }
    }

    public PartialAuthorizationResponse isAuthorizedPartial(
            PartialAuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policySet, "policySet");
        Objects.requireNonNull(entities, "entities");
        String json;
        try {
            ObjectNode root = mapper.valueToTree(request);
            root.set("policies", mapper.valueToTree(policySet));
            root.set("entities", mapper.valueToTree(entities));
            json = mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to serialize partial authorization request", e);
        }
        String result = wasm.call("AuthorizationPartialOperation", json);
        try {
            return mapper.readValue(result, PartialAuthorizationResponse.class);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to parse partial authorization response", e);
        }
    }

    public ValidationResponse validate(ValidationRequest request) {
        Objects.requireNonNull(request, "request");
        String json;
        try {
            json = mapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to serialize validation request", e);
        }
        String result = wasm.call("ValidateOperation", json);
        try {
            return mapper.readValue(result, ValidationResponse.class);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to parse validation response", e);
        }
    }

    public EntityValidationResponse validateEntities(EntityValidationRequest request) {
        Objects.requireNonNull(request, "request");
        String json;
        try {
            json = mapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to serialize entity validation request", e);
        }
        String result = wasm.call("ValidateEntities", json);
        try {
            return mapper.readValue(result, EntityValidationResponse.class);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to parse entity validation response", e);
        }
    }

    public String version() {
        int widePtr = wasm.exports().cedarVersion();
        return wasm.readWidePtr(widePtr);
    }

    public void cachePolicySet(String id, PolicySet policySet) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(policySet, "policySet");
        try {
            String json = mapper.writeValueAsString(policySet);
            String result = wasm.callExport(wasm.exports()::cedarPreparsePolicySet, id, json);
            CacheResponse response = mapper.readValue(result, CacheResponse.class);
            if (!response.isSuccess()) {
                throw new CedarException("Failed to cache policy set: " + response.errors());
            }
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to serialize policy set", e);
        }
    }

    public void cacheSchema(String id, Schema schema) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schema, "schema");
        String schemaText = schema.text();
        if (schema.format() == Schema.Format.JSON) {
            try {
                schemaText = mapper.writeValueAsString(mapper.readTree(schemaText));
            } catch (JsonProcessingException e) {
                throw new CedarException("Invalid JSON schema", e);
            }
        }
        try {
            String result = wasm.callExport(wasm.exports()::cedarPreparseSchema, id, schemaText);
            CacheResponse response = mapper.readValue(result, CacheResponse.class);
            if (!response.isSuccess()) {
                throw new CedarException("Failed to cache schema: " + response.errors());
            }
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to parse cache response", e);
        }
    }

    public AuthorizationResponse isAuthorizedCached(
            AuthorizationRequest request, String policySetId, Set<Entity> entities) {
        return isAuthorizedCached(request, policySetId, null, entities);
    }

    public AuthorizationResponse isAuthorizedCached(
            AuthorizationRequest request,
            String policySetId,
            String schemaId,
            Set<Entity> entities) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policySetId, "policySetId");
        Objects.requireNonNull(entities, "entities");
        String json;
        try {
            ObjectNode root = mapper.valueToTree(request);
            root.put("preparsedPolicySetId", policySetId);
            if (schemaId != null) {
                root.put("preparsedSchemaName", schemaId);
            }
            root.put("validateRequest", request.isRequestValidationEnabled());
            root.set("entities", mapper.valueToTree(entities));
            json = mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to serialize cached authorization request", e);
        }
        String result = wasm.statefulAuthorize(json);
        try {
            return mapper.readValue(result, AuthorizationResponse.class);
        } catch (JsonProcessingException e) {
            throw new CedarException("Failed to parse authorization response", e);
        }
    }

    @Override
    public void close() {
        // Wasm memory is GC'd with the instance; contract established for future use
    }

    private void serializeSchema(ObjectNode root, Schema schema) {
        if (schema == null) {
            return;
        }
        try {
            if (schema.format() == Schema.Format.CEDAR) {
                root.put("schema", schema.text());
            } else {
                root.set("schema", mapper.readTree(schema.text()));
            }
        } catch (JsonProcessingException e) {
            throw new CedarException("Invalid JSON schema", e);
        }
    }

    public static final class Builder {
        private ObjectMapper mapper;

        private Builder() {}

        public Builder withObjectMapper(ObjectMapper mapper) {
            this.mapper = Objects.requireNonNull(mapper, "mapper");
            return this;
        }

        public CedarEngine build() {
            ObjectMapper m = mapper != null ? mapper : DEFAULT_MAPPER;
            return new CedarEngine(CedarWasm.create(), m);
        }
    }
}
