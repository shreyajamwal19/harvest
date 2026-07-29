package com.harvest.chef.util;

/**
 * The reasoning model is prompted to return raw JSON, but sometimes wraps it in
 * markdown code fences anyway. This strips that wrapping before parsing.
 */
public final class JsonExtractionUtil {

    private JsonExtractionUtil() {
    }

    public static String stripCodeFences(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(json)?", "").trim();
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }
}
