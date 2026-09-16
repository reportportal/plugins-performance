package com.epam.reportportal.common;

import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceMetricsAttributesTest {

    @Test
    void forRequestItem_exposesNumericMetricsWithUnitsInKeys() {
        PerformanceStatsCollector collector = new PerformanceStatsCollector();
        collector.registerSample("login", 100L, true);
        collector.registerSample("login", 300L, false);

        Set<ItemAttributesRQ> attributes = PerformanceMetricsAttributes.forRequestItem(
                collector.getStatsMap().get("login"));

        Map<String, String> byKey = attributes.stream()
                .collect(Collectors.toMap(ItemAttributesRQ::getKey, ItemAttributesRQ::getValue));

        assertEquals("2", byKey.get("total"));
        assertEquals("1", byKey.get("failed"));
        assertEquals("50.00", byKey.get("error_rate_pct"));
        assertEquals("200", byKey.get("avg_ms"));
        assertTrue(byKey.containsKey("p50_ms"));
        assertTrue(byKey.containsKey("p95_ms"));
        assertTrue(byKey.containsKey("p99_ms"));
    }

    @Test
    void forRequestItem_returnsEmptyWhenNoSamples() {
        assertTrue(PerformanceMetricsAttributes.forRequestItem(null).isEmpty());
        assertTrue(PerformanceMetricsAttributes.forRequestItem(
                new PerformanceStatsCollector.SamplerStats("empty")).isEmpty());
    }
}
