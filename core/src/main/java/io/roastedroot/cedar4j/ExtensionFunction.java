package io.roastedroot.cedar4j;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.function.Function;

final class ExtensionFunction {
    private final String name;
    private final Function<JsonNode, JsonNode> callback;

    ExtensionFunction(String name, Function<JsonNode, JsonNode> callback) {
        this.name = Objects.requireNonNull(name);
        this.callback = Objects.requireNonNull(callback);
    }

    String name() {
        return name;
    }

    JsonNode invoke(JsonNode args) {
        return callback.apply(args);
    }
}
