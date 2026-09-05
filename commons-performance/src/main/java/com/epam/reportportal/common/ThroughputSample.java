package com.epam.reportportal.common;

/**
 * Minimal timestamped request used by {@link ThroughputCalculator}.
 * Independent of JMeter {@code SampleResult} and Gatling events so both tools
 * can map into this type (or {@link PerformanceSample}) before calculation.
 * <p>
 * Throughput counts every completed request, success and failure alike.
 * Quality is a separate metric (error rate).
 */
public final class ThroughputSample {

    private final long startTimeMs;
    private final long endTimeMs;

    /**
     * @param startTimeMs request start, epoch milliseconds
     * @param endTimeMs   request completion, epoch milliseconds; if earlier than
     *                    {@code startTimeMs} it is clamped to the start
     */
    public ThroughputSample(long startTimeMs, long endTimeMs) {
        this.startTimeMs = startTimeMs;
        this.endTimeMs = Math.max(endTimeMs, startTimeMs);
    }

    /** Instantaneous sample: start and completion share the same timestamp. */
    public static ThroughputSample at(long timestampMs) {
        return new ThroughputSample(timestampMs, timestampMs);
    }

    public static ThroughputSample from(PerformanceSample sample) {
        if (sample == null) {
            return null;
        }
        return new ThroughputSample(sample.getTimestamp(), sample.getEndTimestamp());
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getEndTimeMs() {
        return endTimeMs;
    }
}
