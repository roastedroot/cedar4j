package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class EntityUID {
    private final String type;
    private final String id;

    @JsonCreator
    private EntityUID(@JsonProperty("type") String type, @JsonProperty("id") String id) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = Objects.requireNonNull(id, "id");
    }

    public static EntityUID of(String type, String id) {
        return new EntityUID(type, id);
    }

    @JsonProperty("type")
    public String type() {
        return type;
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntityUID)) {
            return false;
        }
        EntityUID that = (EntityUID) o;
        return type.equals(that.type) && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    @Override
    public String toString() {
        return type + "::\"" + id + "\"";
    }
}
