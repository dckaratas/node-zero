package com.nodezero.sim.content;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import java.util.stream.Collectors;

/** Invalid content. Messages point at {@code file:line:column (field.path)} where possible. */
public final class ContentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContentException(String message) {
        super(message);
    }

    public ContentException(String message, Throwable cause) {
        super(message, cause);
    }

    static ContentException from(String file, JsonProcessingException e) {
        StringBuilder where = new StringBuilder(file);
        JsonLocation location = e.getLocation();
        if (location != null && location.getLineNr() > 0) {
            where.append(':').append(location.getLineNr()).append(':').append(location.getColumnNr());
        }
        if (e instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            where.append(" (").append(path(mapping)).append(')');
        }
        return new ContentException(where + ": " + reason(e), e);
    }

    private static String reason(JsonProcessingException e) {
        if (e instanceof UnrecognizedPropertyException unknown) {
            return "unknown field '" + unknown.getPropertyName() + "'";
        }
        if (e instanceof ValueInstantiationException && e.getCause() != null) {
            return e.getCause().getMessage();
        }
        if (e.getCause() instanceof IllegalArgumentException invalid) {
            return invalid.getMessage();
        }
        return e.getOriginalMessage();
    }

    private static String path(JsonMappingException e) {
        return e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }
}
