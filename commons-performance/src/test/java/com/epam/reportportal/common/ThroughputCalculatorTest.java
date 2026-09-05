package com.epam.reportportal.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Worked example for the three throughput strategies, plus edge cases.
 * <p>
 * Fixture (relative milliseconds from {@link #T0}):
 * <pre>
 *   0s        1s        2s        3s        4s
 *   |---------|---------|---------|---------|
 *   r         r         r         r         r     five completions, 1 s apart
 * </pre>
 */
class ThroughputCalculatorTest {

    private static final long T0 = 1_700_000_000_000L;

    @Test
    void overallMean_isTotalRequestsOverLifecycleSeconds() {
        // 5 requests, first at T0, last at T0+4000 ms → 5 / 4.00 s = 1.25 rps
        ThroughputMetrics metrics = ThroughputCalculator.calculate(evenlySpaced(5, 1_000L));

        assertEquals(5L, metrics.getTotalRequests());
        assertEquals(4_000L, metrics.getTotalDurationMs());
        assertEquals(1.25, metrics.getOverallMeanRps());
        assertEquals(new BigDecimal("1.25"), metrics.getOverallMeanRpsExact());
    }

    @Test
    void steadyState_excludesRampUpAndRampDownWindows() {
        // Window [T0+1000, T0+3000] → 3 completions in 2.00 s = 1.50 rps
        ThroughputConfig config = ThroughputConfig.builder()
                .rampUpSeconds(1)
                .rampDownSeconds(1)
                .windowSizeSeconds(1)
                .build();

        ThroughputMetrics metrics = ThroughputCalculator.calculate(evenlySpaced(5, 1_000L), config);

        assertFalse(metrics.isSteadyStateFellBackToOverall());
        assertEquals(3L, metrics.getSteadyStateRequests());
        assertEquals(2_000L, metrics.getSteadyStateDurationMs());
        assertEquals(1.50, metrics.getSteadyStateRps());
        assertEquals(1.25, metrics.getOverallMeanRps());
    }

    @Test
    void steadyState_fallsBackToOverallWhenRampsConsumeTheRun() {
        ThroughputConfig config = new ThroughputConfig(Duration.ofSeconds(3), Duration.ofSeconds(2), 1);

        ThroughputMetrics metrics = ThroughputCalculator.calculate(evenlySpaced(5, 1_000L), config);

        assertTrue(metrics.isSteadyStateFellBackToOverall());
        assertEquals(metrics.getOverallMeanRps(), metrics.getSteadyStateRps());
        assertEquals(5L, metrics.getSteadyStateRequests());
        assertTrue(MetricsFormatter.throughputMarkdown(metrics).contains("ramp-up + ramp-down"));
    }

    @Test
    void peak_isMaxSlidingWindowNormalizedToRps() {
        // 3 completions in the first 200 ms, then 2 around t=5 s. 1 s window → peak 3.00 rps
        List<ThroughputSample> samples = atOffsets(0L, 100L, 200L, 5_000L, 5_100L);
        ThroughputConfig config = ThroughputConfig.builder().windowSizeSeconds(1).build();

        ThroughputMetrics metrics = ThroughputCalculator.calculate(samples, config);

        assertEquals(3.00, metrics.getPeakRps());
        assertEquals(0.98, metrics.getOverallMeanRps()); // 5 / 5.1 s = 0.980 → 0.98
    }

    @Test
    void peak_fiveSecondWindowDividesCountByWindowSize() {
        // 10 requests packed into 900 ms, then one 20 s later. 5 s window → 10 / 5 = 2.00 rps
        List<ThroughputSample> burst = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            burst.add(at(i * 100L));
        }
        burst.add(at(20_000L));

        ThroughputMetrics metrics = ThroughputCalculator.calculate(
                burst, ThroughputConfig.builder().windowSizeSeconds(5).build());

        assertEquals(2.00, metrics.getPeakRps());
        assertEquals(0.55, metrics.getOverallMeanRps()); // 11 / 20 s
    }

    @Test
    void emptyOrNullInput_returnsZeroes() {
        ThroughputMetrics empty = ThroughputCalculator.calculate(Collections.emptyList());
        assertEquals(0.00, empty.getOverallMeanRps());
        assertEquals(0.00, empty.getSteadyStateRps());
        assertEquals(0.00, empty.getPeakRps());
        assertEquals(0L, empty.getTotalRequests());

        ThroughputMetrics nil = ThroughputCalculator.calculate((Iterable<ThroughputSample>) null);
        assertEquals(ThroughputMetrics.empty(), nil);
    }

    @Test
    void zeroDuration_doesNotDivideByZero() {
        List<ThroughputSample> sameInstant = Arrays.asList(at(0L), at(0L), at(0L));

        ThroughputMetrics metrics = ThroughputCalculator.calculate(sameInstant);

        assertEquals(0L, metrics.getTotalDurationMs());
        assertEquals(0.00, metrics.getOverallMeanRps());
        assertEquals(0.00, metrics.getPeakRps());
        assertEquals(3L, metrics.getTotalRequests());
    }

    @Test
    void shortRunUnderOneSecond_usesActualFractionalDuration() {
        // 10 completions from t=0 to t=450 ms → 10 / 0.45 s = 22.22 rps
        List<ThroughputSample> samples = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            samples.add(at(i * 50L));
        }

        ThroughputMetrics metrics = ThroughputCalculator.calculate(samples);

        assertEquals(450L, metrics.getTotalDurationMs());
        assertEquals(22.22, metrics.getOverallMeanRps());
        // Peak window (1 s) is longer than the run, so peak equals overall mean.
        assertEquals(22.22, metrics.getPeakRps());
    }

    @Test
    void streamAndPerformanceSamples_areAccepted() {
        Stream<ThroughputSample> stream = evenlySpaced(5, 1_000L).stream();
        ThroughputMetrics fromStream = ThroughputCalculator.calculate(stream, ThroughputConfig.defaults());
        assertEquals(1.25, fromStream.getOverallMeanRps());

        List<PerformanceSample> performanceSamples = Arrays.asList(
                performanceSample(T0, 0L),
                performanceSample(T0 + 2_000L, 2_000L)
        );
        ThroughputMetrics fromMapped = ThroughputCalculator.fromPerformanceSamples(
                performanceSamples, ThroughputConfig.defaults());
        // start 0 / end 4000 (second sample starts at +2s and lasts 2s)
        assertEquals(2L, fromMapped.getTotalRequests());
        assertEquals(4_000L, fromMapped.getTotalDurationMs());
        assertEquals(0.50, fromMapped.getOverallMeanRps());
        assertEquals(T0 + 4_000L, performanceSamples.get(1).getEndTimestamp());
    }

    @Test
    void aggregator_isSafeForConcurrentRecording() throws InterruptedException {
        ThroughputAggregator aggregator = new ThroughputAggregator();
        int threads = 4;
        int perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        aggregator.recordTimestamp(T0 + (threadIndex * perThread + i) * 100L);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();

        ThroughputMetrics metrics = aggregator.snapshot();
        assertEquals(threads * perThread, metrics.getTotalRequests());
        assertEquals(metrics, ThroughputCalculator.calculate(aggregator.recordedSamples()));
    }

    @Test
    void config_rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new ThroughputConfig(0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ThroughputConfig.builder().rampUp(Duration.ofSeconds(-1)).build());
    }

    @Test
    void fromParameters_usesDefaultsWhenBlank() {
        assertEquals(ThroughputConfig.defaults(), ThroughputConfig.fromParameters(null, "  ", ""));
    }

    @Test
    void fromParameters_parsesSeconds() {
        ThroughputConfig config = ThroughputConfig.fromParameters("30", "10", "5");
        assertEquals(Duration.ofSeconds(30), config.getRampUpDuration());
        assertEquals(Duration.ofSeconds(10), config.getRampDownDuration());
        assertEquals(5, config.getWindowSizeSeconds());
    }

    @Test
    void nullSamplesInTheList_areSkipped() {
        List<ThroughputSample> samples = Arrays.asList(at(0L), null, at(1_000L));
        ThroughputMetrics metrics = ThroughputCalculator.calculate(samples);
        assertEquals(2L, metrics.getTotalRequests());
        assertEquals(2.00, metrics.getOverallMeanRps());
    }

    private static List<ThroughputSample> evenlySpaced(int count, long stepMs) {
        List<ThroughputSample> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            samples.add(at(i * stepMs));
        }
        return samples;
    }

    private static List<ThroughputSample> atOffsets(long... offsetsMs) {
        List<ThroughputSample> samples = new ArrayList<>(offsetsMs.length);
        for (long offset : offsetsMs) {
            samples.add(at(offset));
        }
        return samples;
    }

    private static ThroughputSample at(long offsetMs) {
        return ThroughputSample.at(T0 + offsetMs);
    }

    private static PerformanceSample performanceSample(long startMs, long durationMs) {
        return new PerformanceSample(
                "login", "scenario", "thread-1",
                startMs, durationMs, true,
                "200", "OK", null, null
        );
    }
}
