package com.epam.reportportal.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceStatsCollectorTest {

    @Test
    void registerSample_aggregatesPerName() {
        PerformanceStatsCollector collector = new PerformanceStatsCollector();

        collector.registerSample("login", 100L, true);
        collector.registerSample("login", 300L, false);

        PerformanceStatsCollector.SamplerStats stats = collector.getStatsMap().get("login");
        assertEquals(2L, stats.getTotal());
        assertEquals(1L, stats.getSuccess());
        assertEquals(1L, stats.getFailed());
        assertEquals(100L, stats.getMin());
        assertEquals(300L, stats.getMax());
        assertEquals(200L, stats.getAvg());
    }

    @Test
    void registerSample_clipsDurationsBeyondTheTrackableRange() {
        PerformanceStatsCollector collector = new PerformanceStatsCollector();

        collector.registerSample("slow", Long.MAX_VALUE, true);

        PerformanceStatsCollector.SamplerStats stats = collector.getStatsMap().get("slow");
        assertEquals(3_600_000L, stats.getMax());
        assertTrue(stats.getPercentile(99.0) > 0L);
    }

    @Test
    void registerSample_mergesNamesBeyondTheCapIntoOverflowBucket() {
        PerformanceStatsCollector collector = new PerformanceStatsCollector();

        int extra = 50;
        for (int i = 0; i < PerformanceStatsCollector.MAX_TRACKED_NAMES + extra; i++) {
            collector.registerSample("request-" + i, 10L, true);
        }

        // The overflow bucket is itself an entry, hence the +1.
        assertEquals(PerformanceStatsCollector.MAX_TRACKED_NAMES + 1, collector.getStatsMap().size());

        PerformanceStatsCollector.SamplerStats overflow =
                collector.getStatsMap().get(PerformanceStatsCollector.OVERFLOW_NAME);
        assertNotNull(overflow);
        assertEquals(extra, overflow.getTotal());

        // Names seen before the cap keep their own metrics.
        assertNotNull(collector.getStatsMap().get("request-0"));
        assertNull(collector.getStatsMap().get("request-" + (PerformanceStatsCollector.MAX_TRACKED_NAMES + 1)));
    }

    @Test
    void globalStats_mergesEveryName() {
        PerformanceStatsCollector collector = new PerformanceStatsCollector();

        collector.registerSample("a", 100L, true);
        collector.registerSample("b", 200L, false);

        PerformanceStatsCollector.SamplerStats global = collector.getGlobalStats();
        assertEquals(2L, global.getTotal());
        assertEquals(1L, global.getFailed());
        assertEquals(50.0, global.getErrorRate());
    }

    @Test
    void isPlainHttp_detectsUnencryptedEndpoints() {
        assertTrue(ReportPortalClient.isPlainHttp("http://rp.example"));
        assertTrue(ReportPortalClient.isPlainHttp("  HTTP://rp.example"));
        assertFalse(ReportPortalClient.isPlainHttp("https://rp.example"));
        assertFalse(ReportPortalClient.isPlainHttp(null));
    }
}
