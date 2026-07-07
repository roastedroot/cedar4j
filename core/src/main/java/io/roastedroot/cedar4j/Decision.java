package io.roastedroot.cedar4j;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Decision {
    @JsonProperty("allow")
    ALLOW,

    @JsonProperty("deny")
    DENY
}
