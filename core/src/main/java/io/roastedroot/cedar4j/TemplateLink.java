package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TemplateLink {
    private final String templateId;
    private final String resultPolicyId;
    private final List<LinkValue> linkValues;

    @JsonCreator
    private TemplateLink(
            @JsonProperty("templateId") String templateId,
            @JsonProperty("newId") String resultPolicyId,
            @JsonProperty("values") List<LinkValue> linkValues) {
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.resultPolicyId = Objects.requireNonNull(resultPolicyId, "resultPolicyId");
        this.linkValues =
                linkValues != null
                        ? Collections.unmodifiableList(linkValues)
                        : Collections.emptyList();
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

    @JsonProperty("values")
    public List<LinkValue> linkValues() {
        return linkValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TemplateLink)) return false;
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

        @JsonCreator
        private LinkValue(
                @JsonProperty("slot") String slot,
                @JsonProperty("value") EntityUID value) {
            this.slot = Objects.requireNonNull(slot, "slot");
            this.value = Objects.requireNonNull(value, "value");
        }

        public static LinkValue of(String slot, EntityUID value) {
            return new LinkValue(slot, value);
        }

        @JsonProperty("slot")
        public String slot() {
            return slot;
        }

        @JsonProperty("value")
        public EntityUID value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LinkValue)) return false;
            LinkValue that = (LinkValue) o;
            return Objects.equals(slot, that.slot)
                    && Objects.equals(value, that.value);
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
