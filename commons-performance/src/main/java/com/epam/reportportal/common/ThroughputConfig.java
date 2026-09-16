package com.epam.reportportal.common;

import java.util.Objects;

/**
 * Parameters for peak throughput calculation in {@link ThroughputCalculator}.
 * Overall mean throughput does not use this config.
 */
public final class ThroughputConfig {

    public static final int DEFAULT_WINDOW_SIZE_SECONDS = 1;

    private final int windowSizeSeconds;

    public ThroughputConfig(int windowSizeSeconds) {
        if (windowSizeSeconds < 1) {
            throw new IllegalArgumentException("windowSizeSeconds must be >= 1, got: " + windowSizeSeconds);
        }
        this.windowSizeSeconds = windowSizeSeconds;
    }

    public static ThroughputConfig defaults() {
        return new ThroughputConfig(DEFAULT_WINDOW_SIZE_SECONDS);
    }

    /**
     * Blank / null value uses default window ({@code 1} second).
     */
    public static ThroughputConfig fromParameters(String windowSizeSeconds) {
        return new ThroughputConfig(
                parsePositiveInt(windowSizeSeconds, DEFAULT_WINDOW_SIZE_SECONDS, "throughput window size seconds")
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getWindowSizeSeconds() {
        return windowSizeSeconds;
    }

    public long getWindowSizeMs() {
        return windowSizeSeconds * 1000L;
    }

    private static int parsePositiveInt(String value, int defaultValue, String name) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 1) {
            throw new IllegalArgumentException(name + " must be >= 1, got: " + value);
        }
        return parsed;
    }

    public static final class Builder {
        private int windowSizeSeconds = DEFAULT_WINDOW_SIZE_SECONDS;

        public Builder windowSizeSeconds(int seconds) {
            this.windowSizeSeconds = seconds;
            return this;
        }

        public ThroughputConfig build() {
            return new ThroughputConfig(windowSizeSeconds);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThroughputConfig)) {
            return false;
        }
        ThroughputConfig that = (ThroughputConfig) o;
        return windowSizeSeconds == that.windowSizeSeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(windowSizeSeconds);
    }
}
