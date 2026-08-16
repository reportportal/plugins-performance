package com.epam.reportportal.common;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Filters which samples are reported (stats, SLA, failures)
 * using optional include/exclude label regexes.
 */
public final class SampleFilter {

    private final Pattern includePattern;
    private final Pattern excludePattern;

    public SampleFilter(Pattern includePattern, Pattern excludePattern) {
        this.includePattern = includePattern;
        this.excludePattern = excludePattern;
    }

    public static SampleFilter fromParameters(String includeRegex, String excludeRegex) {
        return new SampleFilter(
                compileOptional(includeRegex, "Sample_Include_Regex"),
                compileOptional(excludeRegex, "Sample_Exclude_Regex")
        );
    }

    public boolean accept(String label) {
        if (label == null) {
            label = "";
        }

        if (excludePattern != null && excludePattern.matcher(label).find()) {
            return false;
        }

        if (includePattern != null && !includePattern.matcher(label).find()) {
            return false;
        }

        return true;
    }

    public Pattern getIncludePattern() {
        return includePattern;
    }

    public Pattern getExcludePattern() {
        return excludePattern;
    }

    private static Pattern compileOptional(String regex, String paramName) {
        if (regex == null || regex.trim().isEmpty()) {
            return null;
        }
        try {
            return Pattern.compile(regex.trim());
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid Java regex for " + paramName + ": " + regex, e);
        }
    }
}
