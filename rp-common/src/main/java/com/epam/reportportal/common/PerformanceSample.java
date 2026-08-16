package com.epam.reportportal.common;

/**
 * Tool-agnostic representation of a single load-test sample/request.
 */
public final class PerformanceSample {
    private final String label;
    private final String scenarioName;
    private final String threadName;
    private final long timestamp;
    private final long durationMs;
    private final boolean success;
    private final String responseCode;
    private final String responseMessage;
    private final String requestBody;
    private final String responseBody;

    public PerformanceSample(String label, String scenarioName, String threadName,
                             long timestamp, long durationMs, boolean success,
                             String responseCode, String responseMessage,
                             String requestBody, String responseBody) {
        this.label = label != null ? label : "";
        this.scenarioName = (scenarioName == null || scenarioName.isEmpty()) ? "Unknown" : scenarioName;
        this.threadName = threadName;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.success = success;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
    }

    public String getLabel() {
        return label;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
