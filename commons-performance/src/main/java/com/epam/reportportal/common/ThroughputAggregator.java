package com.epam.reportportal.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe collector of {@link ThroughputSample} events. Record during the run
 * (JMeter listener thread, Gatling stats engine, or any worker); call
 * {@link #snapshot(ThroughputConfig)} once at the end.
 * <p>
 * Calculation is delegated to {@link ThroughputCalculator} so collection and math
 * stay independent.
 */
public final class ThroughputAggregator {

    private final ConcurrentLinkedQueue<ThroughputSample> samples = new ConcurrentLinkedQueue<>();

    public void record(ThroughputSample sample) {
        if (sample != null) {
            samples.add(sample);
        }
    }

    public void record(PerformanceSample sample) {
        record(ThroughputSample.from(sample));
    }

    public void record(long startTimeMs, long endTimeMs) {
        record(new ThroughputSample(startTimeMs, endTimeMs));
    }

    /** Instantaneous completion (no separate start timestamp). */
    public void recordTimestamp(long timestampMs) {
        record(ThroughputSample.at(timestampMs));
    }

    public ThroughputMetrics snapshot() {
        return snapshot(ThroughputConfig.defaults());
    }

    public ThroughputMetrics snapshot(ThroughputConfig config) {
        return ThroughputCalculator.calculate(samples, config);
    }

    /**
     * Defensive copy of recorded samples. Intended for tests and post-run
     * inspection, not for the hot path.
     */
    public List<ThroughputSample> recordedSamples() {
        return new ArrayList<>(samples);
    }

    public int size() {
        return samples.size();
    }

    public void clear() {
        samples.clear();
    }
}
