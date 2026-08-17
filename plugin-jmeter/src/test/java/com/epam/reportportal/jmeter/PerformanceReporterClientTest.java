package com.epam.reportportal.jmeter;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PerformanceReporterClientTest {

    @Mock
    private BackendListenerContext context;

    @Mock
    private SampleResult sampleResult;

    @Test
    void getDefaultParameters_exposesRequiredKeys() {
        PerformanceReporterClient client = new PerformanceReporterClient();
        Map<String, String> defaults = client.getDefaultParameters().getArgumentsAsMap();

        assertEquals("http://localhost:8080", defaults.get("ReportPortal_URL"));
        assertEquals("default_personal", defaults.get("Project_Name"));
        assertEquals("JMeter Performance Metrics", defaults.get("Launch_Name"));
        assertEquals("true", defaults.get("Attach_HTML_Dashboard"));
        assertTrue(defaults.containsKey("SLA_P95_MS"));
        assertTrue(defaults.containsKey("Sample_Include_Regex"));
        assertTrue(defaults.containsKey("Sample_Exclude_Regex"));
    }

    @Test
    void handleSampleResults_isNoopWhenServiceNotInitialized() {
        PerformanceReporterClient client = new PerformanceReporterClient();
        List<SampleResult> results = Collections.singletonList(sampleResult);

        assertDoesNotThrow(() -> client.handleSampleResults(results, context));
        assertDoesNotThrow(() -> client.handleSampleResults(null, context));
    }
}
