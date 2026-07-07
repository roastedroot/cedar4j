package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class AuthorizationResponse {
    private final boolean success;
    private final Decision decision;
    private final Set<String> reasons;
    private final List<DetailedError> errors;
    private final List<DetailedError> warnings;

    @JsonCreator
    AuthorizationResponse(
            @JsonProperty("type") String type,
            @JsonProperty("response") InnerResponse response,
            @JsonProperty("errors") List<DetailedError> outerErrors,
            @JsonProperty("warnings") List<DetailedError> warnings) {
        this.success = "success".equals(type);
        this.warnings =
                warnings != null
                        ? Collections.unmodifiableList(warnings)
                        : Collections.emptyList();

        if (this.success && response != null) {
            this.decision = response.decision;
            this.reasons =
                    response.diagnostics != null && response.diagnostics.reason != null
                            ? Collections.unmodifiableSet(
                                    new LinkedHashSet<>(response.diagnostics.reason))
                            : Collections.emptySet();
            this.errors =
                    response.diagnostics != null && response.diagnostics.errors != null
                            ? Collections.unmodifiableList(response.diagnostics.errors)
                            : Collections.emptyList();
        } else {
            this.decision = Decision.DENY;
            this.reasons = Collections.emptySet();
            this.errors =
                    outerErrors != null
                            ? Collections.unmodifiableList(outerErrors)
                            : Collections.emptyList();
        }
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

    public Set<String> reasons() {
        return reasons;
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
        if (!(o instanceof AuthorizationResponse)) return false;
        AuthorizationResponse that = (AuthorizationResponse) o;
        return success == that.success
                && decision == that.decision
                && Objects.equals(reasons, that.reasons)
                && Objects.equals(errors, that.errors)
                && Objects.equals(warnings, that.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, decision, reasons, errors, warnings);
    }

    @Override
    public String toString() {
        return "AuthorizationResponse(" + decision + ", reasons=" + reasons + ")";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class InnerResponse {
        @JsonProperty("decision")
        Decision decision;

        @JsonProperty("diagnostics")
        Diagnostics diagnostics;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Diagnostics {
        @JsonProperty("reason")
        Set<String> reason;

        @JsonProperty("errors")
        List<DetailedError> errors;
    }
}
