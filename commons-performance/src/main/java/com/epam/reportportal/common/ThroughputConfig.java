package com.epam.reportportal.common;

import java.time.Duration;
import java.util.Objects;

/**
 * Parameters for {@link ThroughputCalculator} strategies 2 and 3.
 * Strategy 1 (overall mean) does not use this config.
 */
public final class ThroughputConfig {

    public static final int DEFAULT_WINDOW_SIZE_SECONDS = 1;

    private final Duration rampUpDuration;
    private final Duration rampDownDuration;
    private final int windowSizeSeconds;

    public ThroughputConfig(Duration rampUpDuration, Duration rampDownDuration, int windowSizeSeconds) {
        if (windowSizeSeconds < 1) {
            throw new IllegalArgumentException("windowSizeSeconds must be >= 1, got: " + windowSizeSeconds);
        }
        this.rampUpDuration = requireNonNegative(rampUpDuration, "rampUpDuration");
        this.rampDownDuration = requireNonNegative(rampDownDuration, "rampDownDuration");
        this.windowSizeSeconds = windowSizeSeconds;
    }

    public ThroughputConfig(long rampUpSeconds, long rampDownSeconds, int windowSizeSeconds) {
        this(Duration.ofSeconds(rampUpSeconds), Duration.ofSeconds(rampDownSeconds), windowSizeSeconds);
    }

    public static ThroughputConfig defaults() {
        return new ThroughputConfig(Duration.ZERO, Duration.ZERO, DEFAULT_WINDOW_SIZE_SECONDS);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Duration getRampUpDuration() {
        return rampUpDuration;
    }

    public Duration getRampDownDuration() {
        return rampDownDuration;
    }

    public int getWindowSizeSeconds() {
        return windowSizeSeconds;
    }

    public long getRampUpMs() {
        return rampUpDuration.toMillis();
    }

    public long getRampDownMs() {
        return rampDownDuration.toMillis();
    }

    public long getWindowSizeMs() {
        return windowSizeSeconds * 1000L;
    }

    private static Duration requireNonNegative(Duration duration, String name) {
        Duration value = duration != null ? duration : Duration.ZERO;
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must be >= 0, got: " + value);
        }
        return value;
    }

    public static final class Builder {
        private Duration rampUpDuration = Duration.ZERO;
        private Duration rampDownDuration = Duration.ZERO;
        private int windowSizeSeconds = DEFAULT_WINDOW_SIZE_SECONDS;

        public Builder rampUp(Duration duration) {
            this.rampUpDuration = duration;
            return this;
        }

        public Builder rampUpSeconds(long seconds) {
            this.rampUpDuration = Duration.ofSeconds(seconds);
            return this;
        }

        public Builder rampDown(Duration duration) {
            this.rampDownDuration = duration;
            return this;
        }

        public Builder rampDownSeconds(long seconds) {
            this.rampDownDuration = Duration.ofSeconds(seconds);
            return this;
        }

        public Builder windowSizeSeconds(int seconds) {
            this.windowSizeSeconds = seconds;
            return this;
        }

        public ThroughputConfig build() {
            return new ThroughputConfig(rampUpDuration, rampDownDuration, windowSizeSeconds);
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
        return windowSizeSeconds == that.windowSizeSeconds
                && Objects.equals(rampUpDuration, that.rampUpDuration)
                && Objects.equals(rampDownDuration, that.rampDownDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rampUpDuration, rampDownDuration, windowSizeSeconds);
    }
}
