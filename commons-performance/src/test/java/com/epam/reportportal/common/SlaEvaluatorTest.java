package com.epam.reportportal.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaEvaluatorTest {

    @Test
    void evaluate_throughputSla_passesWhenActualMeetsMinimum() {
        PerformanceStatsCollector collector = new PerformanceStatsCollector();
        collector.registerSample("req", 100L, true);

        SlaConfig config = SlaConfig.fromParameters(null, null, null, "10.0", "5.0");
        ThroughputMetrics throughput = new ThroughputMetrics(
                new BigDecimal("12.34"),
                new BigDecimal("8.00"),
                100L,
                8_000L
        );

        SlaEvaluator.Result result = SlaEvaluator.evaluate(config, collector.getGlobalStats(), throughput);

        assertTrue(result.isPassed());
        assertTrue(result.getChecks().stream().anyMatch(c ->
                "throughput_rps".equals(c.getMetric()) && c.getStatus() == SlaEvaluator.Status.PASS));
        assertTrue(result.getChecks().stream().anyMatch(c ->
                "peak_throughput_rps".equals(c.getMetric()) && c.getStatus() == SlaEvaluator.Status.PASS));
    }

    @Test
    void evaluate_throughputSla_failsWhenBelowMinimum() {
        PerformanceStatsCollector collector = new PerformanceStatsCollector();
        collector.registerSample("req", 100L, true);

        SlaConfig config = SlaConfig.fromParameters(null, null, null, "100.0", null);
        ThroughputMetrics throughput = new ThroughputMetrics(
                new BigDecimal("12.34"),
                new BigDecimal("8.00"),
                100L,
                8_000L
        );

        SlaEvaluator.Result result = SlaEvaluator.evaluate(config, collector.getGlobalStats(), throughput);

        assertFalse(result.isPassed());
        assertTrue(result.getChecks().stream().anyMatch(c ->
                "throughput_rps".equals(c.getMetric()) && c.getStatus() == SlaEvaluator.Status.FAIL));
    }
}
