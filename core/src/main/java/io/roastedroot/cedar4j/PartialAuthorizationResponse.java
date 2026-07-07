package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class PartialAuthorizationResponse {
    private final boolean success;
    private final Decision decision;
    private final Set<String> satisfied;
    private final Set<String> errored;
    private final Set<String> mayBeDetermining;
    private final Set<String> mustBeDetermining;
    private final Map<String, Object> residuals;
    private final Set<String> nontrivialResiduals;
    private final List<DetailedError> errors;
    private final List<DetailedError> warnings;

    @JsonCreator
    PartialAuthorizationResponse(
            @JsonProperty("type") String type,
            @JsonProperty("response") ResidualResponse response,
            @JsonProperty("errors") List<DetailedError> outerErrors,
            @JsonProperty("warnings") List<DetailedError> warnings) {
        this.success = "residuals".equals(type);
        this.warnings =
                warnings != null ? Collections.unmodifiableList(warnings) : Collections.emptyList();

        if (this.success && response != null) {
            this.decision = response.decision;
            this.satisfied = unmodifiableOrEmpty(response.satisfied);
            this.errored = unmodifiableOrEmpty(response.errored);
            this.mayBeDetermining = unmodifiableOrEmpty(response.mayBeDetermining);
            this.mustBeDetermining = unmodifiableOrEmpty(response.mustBeDetermining);
            this.residuals =
                    response.residuals != null
                            ? Collections.unmodifiableMap(new LinkedHashMap<>(response.residuals))
                            : Collections.emptyMap();
            this.nontrivialResiduals = unmodifiableOrEmpty(response.nontrivialResiduals);
            this.errors = Collections.emptyList();
        } else {
            this.decision = Decision.DENY;
            this.satisfied = Collections.emptySet();
            this.errored = Collections.emptySet();
            this.mayBeDetermining = Collections.emptySet();
            this.mustBeDetermining = Collections.emptySet();
            this.residuals = Collections.emptyMap();
            this.nontrivialResiduals = Collections.emptySet();
            this.errors =
                    outerErrors != null
                            ? Collections.unmodifiableList(outerErrors)
                            : Collections.emptyList();
        }
    }

    private static Set<String> unmodifiableOrEmpty(Set<String> set) {
        return set != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(set))
                : Collections.emptySet();
    }

    public boolean isSuccess() {
        return success;
    }

    public Decision decision() {
        return decision;
    }

    public boolean isAllowed() {
        return success && decision == Decision.ALLOW;
    }

    public Set<String> satisfied() {
        return satisfied;
    }

    public Set<String> errored() {
        return errored;
    }

    public Set<String> mayBeDetermining() {
        return mayBeDetermining;
    }

    public Set<String> mustBeDetermining() {
        return mustBeDetermining;
    }

    public Map<String, Object> residuals() {
        return residuals;
    }

    public Set<String> nontrivialResiduals() {
        return nontrivialResiduals;
    }

    public List<DetailedError> errors() {
        return errors;
    }

    public List<DetailedError> warnings() {
        return warnings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartialAuthorizationResponse)) return false;
        PartialAuthorizationResponse that = (PartialAuthorizationResponse) o;
        return success == that.success
                && decision == that.decision
                && Objects.equals(satisfied, that.satisfied)
                && Objects.equals(errored, that.errored)
                && Objects.equals(mayBeDetermining, that.mayBeDetermining)
                && Objects.equals(mustBeDetermining, that.mustBeDetermining)
                && Objects.equals(residuals, that.residuals)
                && Objects.equals(nontrivialResiduals, that.nontrivialResiduals)
                && Objects.equals(errors, that.errors)
                && Objects.equals(warnings, that.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                success,
                decision,
                satisfied,
                errored,
                mayBeDetermining,
                mustBeDetermining,
                residuals,
                nontrivialResiduals,
                errors,
                warnings);
    }

    @Override
    public String toString() {
        if (success) {
            return "PartialAuthorizationResponse("
                    + decision
                    + ", residuals="
                    + nontrivialResiduals.size()
                    + ")";
        }
        return "PartialAuthorizationResponse(failure)";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ResidualResponse {
        @JsonProperty("decision")
        Decision decision;

        @JsonProperty("satisfied")
        Set<String> satisfied;

        @JsonProperty("errored")
        Set<String> errored;

        @JsonProperty("mayBeDetermining")
        Set<String> mayBeDetermining;

        @JsonProperty("mustBeDetermining")
        Set<String> mustBeDetermining;

        @JsonProperty("residuals")
        Map<String, Object> residuals;

        @JsonProperty("nontrivialResiduals")
        Set<String> nontrivialResiduals;
    }
}
