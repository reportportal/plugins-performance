package com.epam.reportportal.common;

import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses custom attribute parameters into ReportPortal launch attributes.
 * <p>
 * Accepted formats per parameter (blank = skip):
 * <ul>
 *   <li>{@code key:value} or {@code key=value}</li>
 *   <li>{@code tag} — tag-style attribute (value only)</li>
 * </ul>
 */
public final class CustomLaunchAttributes {
    public static final int MAX = 5;

    private CustomLaunchAttributes() {
    }

    public static List<ItemAttributesRQ> fromParameters(String... rawValues) {
        if (rawValues == null || rawValues.length == 0) {
            return Collections.emptyList();
        }

        List<ItemAttributesRQ> attributes = new ArrayList<>();
        int limit = Math.min(rawValues.length, MAX);
        for (int i = 0; i < limit; i++) {
            ItemAttributesRQ attr = parseOne(rawValues[i]);
            if (attr != null) {
                attributes.add(attr);
            }
        }
        return attributes;
    }

    private static ItemAttributesRQ parseOne(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        int colon = indexOfSeparator(trimmed, ':');
        int equals = indexOfSeparator(trimmed, '=');
        int sep = pickSeparator(colon, equals);

        if (sep > 0) {
            String key = trimmed.substring(0, sep).trim();
            String value = trimmed.substring(sep + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                return null;
            }
            return new ItemAttributesRQ(key, value);
        }

        return new ItemAttributesRQ(trimmed);
    }

    private static int indexOfSeparator(String s, char sep) {
        int idx = s.indexOf(sep);
        return idx > 0 ? idx : -1;
    }

    private static int pickSeparator(int colon, int equals) {
        if (colon < 0) {
            return equals;
        }
        if (equals < 0) {
            return colon;
        }
        return Math.min(colon, equals);
    }
}
