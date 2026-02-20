package github.tilcob.app.listmerging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Locale;

public record HeaderDefinition(String name,
                               List<String> headers,
                               List<List<String>> headerAliases,
                               HeaderPosition headerPosition) {
    @JsonCreator
    public HeaderDefinition(@JsonProperty("name") String name,
                            @JsonProperty("headers") List<String> headers,
                            @JsonProperty("headerAliases") List<List<String>> headerAliases,
                            @JsonProperty("headerPosition") HeaderPosition headerPosition) {
        this.name = name;
        this.headers = headers;
        this.headerAliases = headerAliases;
        this.headerPosition = headerPosition == null ? HeaderPosition.FIRST : headerPosition;
    }

    public HeaderDefinition(String name, List<String> headers) {
        this(name, headers, null, HeaderPosition.FIRST);
    }

    public HeaderDefinition(String name, List<String> headers, HeaderPosition headerPosition) {
        this(name, headers, null, headerPosition);
    }

    public enum HeaderPosition {
        FIRST,
        LAST,;

        @JsonCreator
        public static HeaderPosition fromJson(String value) {
            if (value == null || value.isBlank()) {
                return FIRST;
            }
            return HeaderPosition.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }


}
