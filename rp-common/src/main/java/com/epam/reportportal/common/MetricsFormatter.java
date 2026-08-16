package com.epam.reportportal.common;

/**
 * Markdown formatting for aggregated performance metrics and log bodies.
 */
public final class MetricsFormatter {

    private MetricsFormatter() {
    }

    public static String globalMetricsMarkdown(PerformanceStatsCollector.SamplerStats globalStats) {
        return "## Global metrics\n\n"
                + String.format(
                "| Total | Success | Failed | Error %% | Min | Max | Avg | p50 | p95 | p99 |\n"
                        + "| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |\n"
                        + "| %d | %d | %d | %.2f%% | %d | %d | %d | %d | %d | %d |\n",
                globalStats.getTotal(),
                globalStats.getSuccess(),
                globalStats.getFailed(),
                globalStats.getErrorRate(),
                globalStats.getMin(),
                globalStats.getMax(),
                globalStats.getAvg(),
                globalStats.getPercentile(50.0),
                globalStats.getPercentile(95.0),
                globalStats.getPercentile(99.0)
        );
    }

    public static String perRequestMetricsMarkdown(Iterable<PerformanceStatsCollector.SamplerStats> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Per-request metrics\n\n");
        sb.append("| Request | Total | Success | Failed | Error % | Min (ms) | Max (ms) | Avg (ms) | 50% (ms) | 95% (ms) | 99% (ms) |\n");
        sb.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |\n");

        for (PerformanceStatsCollector.SamplerStats samplerStats : stats) {
            sb.append(String.format(
                    "| **%s** | %d | %d | %d | %.2f%% | %d | %d | %d | %d | %d | %d |\n",
                    samplerStats.name,
                    samplerStats.getTotal(),
                    samplerStats.getSuccess(),
                    samplerStats.getFailed(),
                    samplerStats.getErrorRate(),
                    samplerStats.getMin(),
                    samplerStats.getMax(),
                    samplerStats.getAvg(),
                    samplerStats.getPercentile(50.0),
                    samplerStats.getPercentile(95.0),
                    samplerStats.getPercentile(99.0)
            ));
        }
        return sb.toString();
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "\n... [TRUNCATED] ..." : text;
    }

    public static String failureDetail(PerformanceSample sample) {
        return String.format(
                "REQUEST FAILURE DETAIL:\n\n" +
                        "URL/Sampler: %s\n" +
                        "Thread: %s\n" +
                        "Elapsed: %d ms\n" +
                        "Response Code: %s\n" +
                        "Response Message: %s\n\n" +
                        "--- REQUEST BODY ---\n%s\n\n" +
                        "--- RESPONSE BODY ---\n%s",
                sample.getLabel(),
                sample.getThreadName(),
                sample.getDurationMs(),
                sample.getResponseCode(),
                sample.getResponseMessage(),
                sample.getRequestBody() != null ? sample.getRequestBody() : "No Request Data",
                sample.getResponseBody() != null ? truncate(sample.getResponseBody(), 3000) : "No Response Data"
        );
    }

    public static String sampleStepName(PerformanceSample sample) {
        return String.format("%s | %s | %d ms",
                sample.getThreadName() != null ? sample.getThreadName() : "thread",
                sample.getResponseCode() != null ? sample.getResponseCode() : "-",
                sample.getDurationMs());
    }
}
