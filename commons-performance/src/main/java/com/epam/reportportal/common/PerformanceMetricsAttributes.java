package com.epam.reportportal.common;

import com.epam.ta.reportportal.ws.model.attribute.ItemAttributeResource;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Builds ReportPortal item/launch attributes from aggregated {@link PerformanceStatsCollector.SamplerStats}.
 * Values are numeric; units live in the attribute key ({@code p95_ms}, {@code error_rate_pct}, …).
 */
public final class PerformanceMetricsAttributes {

    private PerformanceMetricsAttributes() {
    }

    /** Metrics attached to each per-request SUITE item at finish time. */
    public static Set<ItemAttributesRQ> forRequestItem(PerformanceStatsCollector.SamplerStats stats) {
        if (stats == null || stats.getTotal() <= 0) {
            return Collections.emptySet();
        }
        Set<ItemAttributesRQ> attributes = new HashSet<>();
        attributes.add(rq("total", String.valueOf(stats.getTotal())));
        attributes.add(rq("failed", String.valueOf(stats.getFailed())));
        attributes.add(rq("error_rate_pct", String.format("%.2f", stats.getErrorRate())));
        attributes.add(rq("avg_ms", String.valueOf(stats.getAvg())));
        attributes.add(rq("p50_ms", String.valueOf(stats.getPercentile(50.0))));
        attributes.add(rq("p95_ms", String.valueOf(stats.getPercentile(95.0))));
        attributes.add(rq("p99_ms", String.valueOf(stats.getPercentile(99.0))));
        return attributes;
    }

    /** Latency percentile attributes for the launch (throughput added separately). */
    public static Set<ItemAttributeResource> forLaunchLatency(PerformanceStatsCollector.SamplerStats globalStats) {
        if (globalStats == null || globalStats.getTotal() <= 0) {
            return Collections.emptySet();
        }
        Set<ItemAttributeResource> attributes = new HashSet<>();
        attributes.add(resource("p50_ms", String.valueOf(globalStats.getPercentile(50.0))));
        attributes.add(resource("p95_ms", String.valueOf(globalStats.getPercentile(95.0))));
        attributes.add(resource("p99_ms", String.valueOf(globalStats.getPercentile(99.0))));
        return attributes;
    }

    private static ItemAttributesRQ rq(String key, String value) {
        return new ItemAttributesRQ(key, value);
    }

    private static ItemAttributeResource resource(String key, String value) {
        ItemAttributeResource attr = new ItemAttributeResource();
        attr.setKey(key);
        attr.setValue(value);
        return attr;
    }
}
