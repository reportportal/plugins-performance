package com.epam.reportportal.jmeter;

import com.epam.reportportal.common.PerformanceSample;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;

/**
 * Pure mapping helpers for JMeter {@link SampleResult} → ReportPortal models / JTL lines.
 */
final class JmeterSampleMapper {

    private JmeterSampleMapper() {
    }

    static PerformanceSample toPerformanceSample(SampleResult sr) {
        return new PerformanceSample(
                sr.getSampleLabel(),
                extractThreadGroupName(sr.getThreadName()),
                sr.getThreadName(),
                sr.getTimeStamp(),
                sr.getTime(),
                sr.isSuccessful(),
                sr.getResponseCode(),
                sr.getResponseMessage(),
                sr.getSamplerData(),
                sr.getResponseDataAsString()
        );
    }

    /**
     * JMeter sets thread names as "{ThreadGroupName}-{threadNumber}".
     */
    static String extractThreadGroupName(String threadName) {
        if (threadName == null || threadName.isEmpty()) {
            return "Unknown";
        }
        int lastDash = threadName.lastIndexOf('-');
        if (lastDash > 0) {
            String suffix = threadName.substring(lastDash + 1);
            if (suffix.matches("\\d+")) {
                return threadName.substring(0, lastDash);
            }
        }
        return threadName;
    }

    static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    static String getFirstAssertionFailureMessage(SampleResult sr) {
        AssertionResult[] assertionResults = sr.getAssertionResults();
        if (assertionResults != null && assertionResults.length > 0) {
            for (AssertionResult ar : assertionResults) {
                if (ar.isFailure() || ar.isError()) {
                    String msg = ar.getFailureMessage();
                    return msg != null ? msg : "Assertion Failed (No message)";
                }
            }
        }
        return "";
    }
}
