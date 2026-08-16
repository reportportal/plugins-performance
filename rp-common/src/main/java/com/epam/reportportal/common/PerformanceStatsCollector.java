package com.epam.reportportal.common;

import org.HdrHistogram.ConcurrentHistogram;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Streaming performance metrics with fixed memory via HdrHistogram.
 * Does not retain individual response times.
 */
public class PerformanceStatsCollector {

    // Highest trackable latency: 1 hour. 3 significant digits ≈ fixed ~tens of KB per histogram.
    private static final long HIGHEST_TRACKABLE_VALUE_MS = 3_600_000L;
    private static final int SIGNIFICANT_DIGITS = 3;

    public static class SamplerStats {
        public final String name;
        private final LongAdder total = new LongAdder();
        private final LongAdder success = new LongAdder();
        private final LongAdder failed = new LongAdder();
        private final LongAdder sumResponseTime = new LongAdder();
        private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxResponseTime = new AtomicLong(0L);
        private final ConcurrentHistogram histogram =
                new ConcurrentHistogram(HIGHEST_TRACKABLE_VALUE_MS, SIGNIFICANT_DIGITS);

        public SamplerStats(String name) {
            this.name = name;
            this.histogram.setAutoResize(true);
        }

        public void addSample(long durationMs, boolean successful) {
            total.increment();
            if (successful) {
                success.increment();
            } else {
                failed.increment();
            }

            long duration = Math.max(0L, durationMs);
            if (duration > HIGHEST_TRACKABLE_VALUE_MS) {
                duration = HIGHEST_TRACKABLE_VALUE_MS;
            }

            sumResponseTime.add(duration);
            histogram.recordValue(duration);
            updateMin(duration);
            updateMax(duration);
        }

        private void updateMin(long duration) {
            long current;
            do {
                current = minResponseTime.get();
                if (duration >= current) {
                    return;
                }
            } while (!minResponseTime.compareAndSet(current, duration));
        }

        private void updateMax(long duration) {
            long current;
            do {
                current = maxResponseTime.get();
                if (duration <= current) {
                    return;
                }
            } while (!maxResponseTime.compareAndSet(current, duration));
        }

        public long getTotal() {
            return total.sum();
        }

        public long getSuccess() {
            return success.sum();
        }

        public long getFailed() {
            return failed.sum();
        }

        public double getErrorRate() {
            long t = getTotal();
            return t == 0 ? 0.0 : ((double) getFailed() / t) * 100.0;
        }

        public long getMin() {
            long min = minResponseTime.get();
            return min == Long.MAX_VALUE ? 0L : min;
        }

        public long getMax() {
            return maxResponseTime.get();
        }

        public long getAvg() {
            long t = getTotal();
            return t == 0 ? 0L : sumResponseTime.sum() / t;
        }

        public long getPercentile(double percentile) {
            if (histogram.getTotalCount() == 0) {
                return 0L;
            }
            return histogram.getValueAtPercentile(percentile);
        }

        void mergeInto(SamplerStats target) {
            target.total.add(getTotal());
            target.success.add(getSuccess());
            target.failed.add(getFailed());
            target.sumResponseTime.add(sumResponseTime.sum());
            target.updateMin(getMin());
            target.updateMax(getMax());
            target.histogram.add(histogram);
        }
    }

    private final Map<String, SamplerStats> statsMap = new ConcurrentHashMap<>();

    public void registerSample(PerformanceSample sample) {
        registerSample(sample.getLabel(), sample.getDurationMs(), sample.isSuccess());
    }

    public void registerSample(String name, long durationMs, boolean success) {
        statsMap.computeIfAbsent(name, SamplerStats::new).addSample(durationMs, success);
    }

    public Map<String, SamplerStats> getStatsMap() {
        return statsMap;
    }

    public SamplerStats getGlobalStats() {
        SamplerStats global = new SamplerStats("Global");
        for (SamplerStats s : statsMap.values()) {
            s.mergeInto(global);
        }
        return global;
    }
}
