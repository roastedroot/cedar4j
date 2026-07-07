package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class EntityValidationResponse {
    private final boolean success;
    private final List<String> errors;

    @JsonCreator
    EntityValidationResponse(
            @JsonProperty("success") String successStr,
            @JsonProperty("errors") List<String> errors) {
        this.success = "true".equals(successStr);
        this.errors =
                errors != null ? Collections.unmodifiableList(errors) : Collections.emptyList();
    }

    public boolean isSuccess() {
        return success;
    }

    public List<String> errors() {
        return errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntityValidationResponse)) {
            return false;
        }
        EntityValidationResponse that = (EntityValidationResponse) o;
        return success == that.success && Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errors);
    }

    @Override
    public String toString() {
        if (success) {
            return "EntityValidationResponse(success)";
        }
        return "EntityValidationResponse(failure, errors=" + errors + ")";
    }
}
