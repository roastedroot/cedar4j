package io.roastedroot.cedar4j;

import java.util.Objects;

public final class Policy {

    private final String policyId;
    private final String policySrc;

    private Policy(String policySrc, String policyId) {
        this.policySrc = Objects.requireNonNull(policySrc, "policySrc");
        this.policyId = Objects.requireNonNull(policyId, "policyId");
    }

    public static Policy of(String policySrc, String policyId) {
        return new Policy(policySrc, policyId);
    }

    public String id() {
        return policyId;
    }

    public String source() {
        return policySrc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Policy)) {
            return false;
        }
        Policy that = (Policy) o;
        return policyId.equals(that.policyId);
    }

    @Override
    public int hashCode() {
        return policyId.hashCode();
    }

    @Override
    public String toString() {
        return "Policy(" + policyId + ")";
    }
}
