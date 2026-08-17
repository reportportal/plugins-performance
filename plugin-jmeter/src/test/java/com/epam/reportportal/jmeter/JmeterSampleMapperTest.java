package com.epam.reportportal.jmeter;

import com.epam.reportportal.common.PerformanceSample;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JmeterSampleMapperTest {

    @Mock
    private SampleResult sampleResult;

    @Test
    void extractThreadGroupName_parsesStandardJMeterPattern() {
        assertEquals("Thread Group", JmeterSampleMapper.extractThreadGroupName("Thread Group-1"));
        assertEquals("API Users", JmeterSampleMapper.extractThreadGroupName("API Users-42"));
    }

    @Test
    void extractThreadGroupName_keepsNameWithoutNumericSuffix() {
        assertEquals("setUp", JmeterSampleMapper.extractThreadGroupName("setUp"));
        assertEquals("group-name", JmeterSampleMapper.extractThreadGroupName("group-name"));
    }

    @Test
    void extractThreadGroupName_handlesBlank() {
        assertEquals("Unknown", JmeterSampleMapper.extractThreadGroupName(null));
        assertEquals("Unknown", JmeterSampleMapper.extractThreadGroupName(""));
    }

    @Test
    void escapeCsv_quotesSpecialCharacters() {
        assertEquals("", JmeterSampleMapper.escapeCsv(null));
        assertEquals("plain", JmeterSampleMapper.escapeCsv("plain"));
        assertEquals("\"a,b\"", JmeterSampleMapper.escapeCsv("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", JmeterSampleMapper.escapeCsv("say \"hi\""));
        assertEquals("\"line1\nline2\"", JmeterSampleMapper.escapeCsv("line1\nline2"));
    }

    @Test
    void toPerformanceSample_mapsFields() {
        when(sampleResult.getSampleLabel()).thenReturn("GET /login");
        when(sampleResult.getThreadName()).thenReturn("Users-3");
        when(sampleResult.getTimeStamp()).thenReturn(1_700_000_000_000L);
        when(sampleResult.getTime()).thenReturn(250L);
        when(sampleResult.isSuccessful()).thenReturn(true);
        when(sampleResult.getResponseCode()).thenReturn("200");
        when(sampleResult.getResponseMessage()).thenReturn("OK");
        when(sampleResult.getSamplerData()).thenReturn("user=a");
        when(sampleResult.getResponseDataAsString()).thenReturn("{\"ok\":true}");

        PerformanceSample sample = JmeterSampleMapper.toPerformanceSample(sampleResult);

        assertEquals("GET /login", sample.getLabel());
        assertEquals("Users", sample.getScenarioName());
        assertEquals("Users-3", sample.getThreadName());
        assertEquals(1_700_000_000_000L, sample.getTimestamp());
        assertEquals(250L, sample.getDurationMs());
        assertTrue(sample.isSuccess());
        assertEquals("200", sample.getResponseCode());
        assertEquals("OK", sample.getResponseMessage());
        assertEquals("user=a", sample.getRequestBody());
        assertEquals("{\"ok\":true}", sample.getResponseBody());
    }

    @Test
    void toPerformanceSample_mapsFailure() {
        when(sampleResult.getSampleLabel()).thenReturn("POST /checkout");
        when(sampleResult.getThreadName()).thenReturn("Checkout-1");
        when(sampleResult.getTimeStamp()).thenReturn(10L);
        when(sampleResult.getTime()).thenReturn(900L);
        when(sampleResult.isSuccessful()).thenReturn(false);
        when(sampleResult.getResponseCode()).thenReturn("500");
        when(sampleResult.getResponseMessage()).thenReturn("Internal Error");
        when(sampleResult.getSamplerData()).thenReturn(null);
        when(sampleResult.getResponseDataAsString()).thenReturn("boom");

        PerformanceSample sample = JmeterSampleMapper.toPerformanceSample(sampleResult);

        assertFalse(sample.isSuccess());
        assertEquals("Checkout", sample.getScenarioName());
        assertEquals("500", sample.getResponseCode());
    }

    @Test
    void getFirstAssertionFailureMessage_returnsFirstFailed() {
        AssertionResult ok = new AssertionResult("ok");
        AssertionResult failed = new AssertionResult("assert");
        failed.setFailure(true);
        failed.setFailureMessage("expected 200");
        when(sampleResult.getAssertionResults()).thenReturn(new AssertionResult[]{ok, failed});

        assertEquals("expected 200", JmeterSampleMapper.getFirstAssertionFailureMessage(sampleResult));
    }

    @Test
    void getFirstAssertionFailureMessage_emptyWhenNone() {
        when(sampleResult.getAssertionResults()).thenReturn(null);
        assertEquals("", JmeterSampleMapper.getFirstAssertionFailureMessage(sampleResult));
    }
}
