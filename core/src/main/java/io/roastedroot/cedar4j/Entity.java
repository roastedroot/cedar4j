package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Entity {
    private final EntityUID uid;
    private final Map<String, Object> attrs;
    private final Set<EntityUID> parents;
    private final Map<String, Object> tags;

    @JsonCreator
    private Entity(
            @JsonProperty("uid") EntityUID uid,
            @JsonProperty("attrs") Map<String, Object> attrs,
            @JsonProperty("parents") Set<EntityUID> parents,
            @JsonProperty("tags") Map<String, Object> tags) {
        this.uid = Objects.requireNonNull(uid, "uid");
        this.attrs =
                attrs != null
                        ? Collections.unmodifiableMap(new LinkedHashMap<>(attrs))
                        : Collections.emptyMap();
        this.parents =
                parents != null
                        ? Collections.unmodifiableSet(new LinkedHashSet<>(parents))
                        : Collections.emptySet();
        this.tags =
                tags != null
                        ? Collections.unmodifiableMap(new LinkedHashMap<>(tags))
                        : Collections.emptyMap();
    }

    public static Entity of(EntityUID uid) {
        return new Entity(uid, null, null, null);
    }

    public static Entity of(EntityUID uid, Set<EntityUID> parents) {
        return new Entity(uid, null, parents, null);
    }

    public static Entity of(
            EntityUID uid, Map<String, Object> attrs, Set<EntityUID> parents) {
        return new Entity(uid, attrs, parents, null);
    }

    public static Entity of(
            EntityUID uid,
            Map<String, Object> attrs,
            Set<EntityUID> parents,
            Map<String, Object> tags) {
        return new Entity(uid, attrs, parents, tags);
    }

    @JsonProperty("uid")
    public EntityUID uid() {
        return uid;
    }

    @JsonProperty("attrs")
    public Map<String, Object> attrs() {
        return attrs;
    }

    @JsonProperty("parents")
    public Set<EntityUID> parents() {
        return parents;
    }

    @JsonProperty("tags")
    public Map<String, Object> tags() {
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Entity)) {
            return false;
        }
        Entity that = (Entity) o;
        return uid.equals(that.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public String toString() {
        return "Entity(" + uid + ")";
    }
}
