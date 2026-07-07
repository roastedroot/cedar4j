package io.roastedroot.cedar4j;

import java.util.Objects;

public final class Schema {

    public enum Format {
        CEDAR,
        JSON
    }

    private final Format format;
    private final String text;

    private Schema(Format format, String text) {
        this.format = Objects.requireNonNull(format, "format");
        this.text = Objects.requireNonNull(text, "text");
    }

    public static Schema fromCedar(String cedarText) {
        return new Schema(Format.CEDAR, cedarText);
    }

    public static Schema fromJson(String jsonText) {
        return new Schema(Format.JSON, jsonText);
    }

    public Format format() {
        return format;
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Schema)) {
            return false;
        }
        Schema that = (Schema) o;
        return format == that.format && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, text);
    }

    @Override
    public String toString() {
        return "Schema(" + format + ")";
    }
}
