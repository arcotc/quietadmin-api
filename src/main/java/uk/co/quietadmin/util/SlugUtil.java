package uk.co.quietadmin.util;

public final class SlugUtil {

    private SlugUtil() {}

    public static String slugify(String input) {
        if (input == null) return null;

        return input
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}