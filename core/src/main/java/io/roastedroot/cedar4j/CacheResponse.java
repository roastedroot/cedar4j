package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
final class CacheResponse {
    private final boolean success;
    private final List<DetailedError> errors;

    @JsonCreator
    CacheResponse(
            @JsonProperty("type") String type,
            @JsonProperty("errors") List<DetailedError> errors) {
        this.success = "success".equals(type);
        this.errors =
                errors != null
                        ? Collections.unmodifiableList(errors)
                        : Collections.emptyList();
    }

    boolean isSuccess() {
        return success;
    }

    List<DetailedError> errors() {
        return errors;
    }
}
