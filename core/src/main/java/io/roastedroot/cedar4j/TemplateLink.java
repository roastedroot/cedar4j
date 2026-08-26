package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TemplateLink {
    private final String templateId;
    private final String resultPolicyId;
    private final List<LinkValue> linkValues;

    private TemplateLink(String templateId, String resultPolicyId, List<LinkValue> linkValues) {
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.resultPolicyId = Objects.requireNonNull(resultPolicyId, "resultPolicyId");
        this.linkValues =
                linkValues != null
                        ? Collections.unmodifiableList(linkValues)
                        : Collections.emptyList();
        // The FFI keys link values by slot and rejects duplicate keys, so a repeated slot is
        // always an error -- surface it here rather than at serialization time.
        Map<String, EntityUID> seen = new LinkedHashMap<>();
        for (LinkValue value : this.linkValues) {
            if (seen.put(value.slot(), value.value()) != null) {
                throw new IllegalArgumentException(
                        "Duplicate slot in link values: " + value.slot());
            }
        }
    }

    public static TemplateLink of(
            String templateId, String resultPolicyId, List<LinkValue> linkValues) {
        return new TemplateLink(templateId, resultPolicyId, linkValues);
    }

    @JsonProperty("templateId")
    public String templateId() {
        return templateId;
    }

    @JsonProperty("newId")
    public String resultPolicyId() {
        return resultPolicyId;
    }

    @JsonIgnore
    public List<LinkValue> linkValues() {
        return linkValues;
    }

    /**
     * The FFI declares link values as a map from slot id to entity uid; the list form is the public
     * API only.
     */
    @JsonProperty("values")
    Map<String, EntityUID> serializedValues() {
        Map<String, EntityUID> values = new LinkedHashMap<>();
        for (LinkValue value : linkValues) {
            values.put(value.slot(), value.value());
        }
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TemplateLink)) {
            return false;
        }
        TemplateLink that = (TemplateLink) o;
        return Objects.equals(templateId, that.templateId)
                && Objects.equals(resultPolicyId, that.resultPolicyId)
                && Objects.equals(linkValues, that.linkValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templateId, resultPolicyId, linkValues);
    }

    @Override
    public String toString() {
        return "TemplateLink(templateId="
                + templateId
                + ", resultPolicyId="
                + resultPolicyId
                + ", values="
                + linkValues
                + ")";
    }

    public static final class LinkValue {
        private final String slot;
        private final EntityUID value;

        private LinkValue(String slot, EntityUID value) {
            this.slot = Objects.requireNonNull(slot, "slot");
            this.value = Objects.requireNonNull(value, "value");
        }

        public static LinkValue of(String slot, EntityUID value) {
            return new LinkValue(slot, value);
        }

        public String slot() {
            return slot;
        }

        public EntityUID value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof LinkValue)) {
                return false;
            }
            LinkValue that = (LinkValue) o;
            return Objects.equals(slot, that.slot) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slot, value);
        }

        @Override
        public String toString() {
            return "LinkValue(slot=" + slot + ", value=" + value + ")";
        }
    }
}
