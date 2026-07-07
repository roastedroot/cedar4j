package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class ValidationResponse {
    private final boolean success;
    private final List<ValidationError> validationErrors;
    private final List<ValidationError> validationWarnings;
    private final List<DetailedError> errors;
    private final List<DetailedError> warnings;

    @JsonCreator
    ValidationResponse(
            @JsonProperty("type") String type,
            @JsonProperty("validationErrors") List<ValidationError> validationErrors,
            @JsonProperty("validationWarnings") List<ValidationError> validationWarnings,
            @JsonProperty("errors") List<DetailedError> errors,
            @JsonAlias("otherWarnings") @JsonProperty("warnings") List<DetailedError> warnings) {
        this.success = "success".equals(type);
        this.validationErrors =
                validationErrors != null
                        ? Collections.unmodifiableList(validationErrors)
                        : Collections.emptyList();
        this.validationWarnings =
                validationWarnings != null
                        ? Collections.unmodifiableList(validationWarnings)
                        : Collections.emptyList();
        this.errors =
                errors != null ? Collections.unmodifiableList(errors) : Collections.emptyList();
        this.warnings =
                warnings != null ? Collections.unmodifiableList(warnings) : Collections.emptyList();
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isValid() {
        return success && validationErrors.isEmpty();
    }

    public List<ValidationError> validationErrors() {
        return validationErrors;
    }

    public List<ValidationError> validationWarnings() {
        return validationWarnings;
    }

    public List<DetailedError> errors() {
        return errors;
    }

    public List<DetailedError> warnings() {
        return warnings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValidationResponse)) {
            return false;
        }
        ValidationResponse that = (ValidationResponse) o;
        return success == that.success
                && Objects.equals(validationErrors, that.validationErrors)
                && Objects.equals(validationWarnings, that.validationWarnings)
                && Objects.equals(errors, that.errors)
                && Objects.equals(warnings, that.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, validationErrors, validationWarnings, errors, warnings);
    }

    @Override
    public String toString() {
        if (success) {
            return "ValidationResponse(errors="
                    + validationErrors
                    + ", warnings="
                    + validationWarnings
                    + ")";
        }
        return "ValidationResponse(failure, errors=" + errors + ")";
    }
}
