package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PolicySet {
    private final Set<Policy> policies;
    private final Set<Policy> templates;
    private final List<TemplateLink> templateLinks;

    private PolicySet(
            Set<Policy> policies, Set<Policy> templates, List<TemplateLink> templateLinks) {
        this.policies =
                policies != null
                        ? Collections.unmodifiableSet(new LinkedHashSet<>(policies))
                        : Collections.emptySet();
        this.templates =
                templates != null
                        ? Collections.unmodifiableSet(new LinkedHashSet<>(templates))
                        : Collections.emptySet();
        this.templateLinks =
                templateLinks != null
                        ? Collections.unmodifiableList(templateLinks)
                        : Collections.emptyList();
    }

    public static PolicySet of(Policy... policies) {
        return new PolicySet(new LinkedHashSet<>(Arrays.asList(policies)), null, null);
    }

    public static PolicySet of(Set<Policy> policies) {
        return new PolicySet(policies, null, null);
    }

    public static PolicySet of(
            Set<Policy> policies, Set<Policy> templates, List<TemplateLink> templateLinks) {
        return new PolicySet(policies, templates, templateLinks);
    }

    @JsonProperty("staticPolicies")
    public Map<String, String> staticPolicies() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Policy p : policies) {
            map.put(p.id(), p.source());
        }
        return map;
    }

    @JsonProperty("templates")
    public Map<String, String> templates() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Policy t : templates) {
            map.put(t.id(), t.source());
        }
        return map;
    }

    @JsonProperty("templateLinks")
    public List<TemplateLink> templateLinks() {
        return templateLinks;
    }

    @JsonIgnore
    public Set<Policy> policies() {
        return policies;
    }

    @JsonIgnore
    public int numPolicies() {
        return policies.size();
    }

    @JsonIgnore
    public int numTemplates() {
        return templates.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PolicySet)) {
            return false;
        }
        PolicySet that = (PolicySet) o;
        return Objects.equals(policies, that.policies)
                && Objects.equals(templates, that.templates)
                && Objects.equals(templateLinks, that.templateLinks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policies, templates, templateLinks);
    }

    @Override
    public String toString() {
        return "PolicySet(policies="
                + policies.size()
                + ", templates="
                + templates.size()
                + ", templateLinks="
                + templateLinks.size()
                + ")";
    }
}
