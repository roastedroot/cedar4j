package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class ValidationError {
    private final String policyId;
    private final DetailedError error;

    @JsonCreator
    private ValidationError(
            @JsonProperty("policyId") String policyId, @JsonProperty("error") DetailedError error) {
        this.policyId = policyId;
        this.error = error;
    }

    public String policyId() {
        return policyId;
    }

    public DetailedError error() {
        return error;
    }

    @Override
    public String toString() {
        return "ValidationError(policyId=" + policyId + ", error=" + error + ")";
    }
}
