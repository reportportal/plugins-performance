package com.epam.reportportal.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates streaming performance metrics against configured SLA thresholds.
 */
public final class SlaEvaluator {

    public enum Status {
        PASS,
        FAIL,
        SKIPPED
    }

    public static final class Check {
        private final String metric;
        private final String actual;
        private final String target;
        private final Status status;

        public Check(String metric, String actual, String target, Status status) {
            this.metric = metric;
            this.actual = actual;
            this.target = target;
            this.status = status;
        }

        public String getMetric() {
            return metric;
        }

        public String getActual() {
            return actual;
        }

        public String getTarget() {
            return target;
        }

        public Status getStatus() {
            return status;
        }
    }

    public static final class Result {
        private final List<Check> checks;
        private final boolean passed;

        public Result(List<Check> checks) {
            this.checks = Collections.unmodifiableList(new ArrayList<>(checks));
            this.passed = checks.stream().noneMatch(c -> c.getStatus() == Status.FAIL);
        }

        public List<Check> getChecks() {
            return checks;
        }

        public boolean isPassed() {
            return passed;
        }

        public boolean hasEvaluatedChecks() {
            return checks.stream().anyMatch(c -> c.getStatus() != Status.SKIPPED);
        }
    }

    private SlaEvaluator() {
    }

    public static Result evaluate(SlaConfig config, PerformanceStatsCollector.SamplerStats stats) {
        List<Check> checks = new ArrayList<>();

        if (config.getP95Ms() != null) {
            long actual = stats.getPercentile(95.0);
            long target = config.getP95Ms();
            checks.add(new Check(
                    "p95",
                    actual + " ms",
                    "<= " + target + " ms",
                    actual <= target ? Status.PASS : Status.FAIL
            ));
        }

        if (config.getP99Ms() != null) {
            long actual = stats.getPercentile(99.0);
            long target = config.getP99Ms();
            checks.add(new Check(
                    "p99",
                    actual + " ms",
                    "<= " + target + " ms",
                    actual <= target ? Status.PASS : Status.FAIL
            ));
        }

        if (config.getErrorRatePct() != null) {
            double actual = stats.getErrorRate();
            double target = config.getErrorRatePct();
            checks.add(new Check(
                    "error_rate",
                    String.format("%.2f%%", actual),
                    "<= " + String.format("%.2f%%", target),
                    actual <= target ? Status.PASS : Status.FAIL
            ));
        }

        return new Result(checks);
    }

    public static String toMarkdown(Result result) {
        if (result.getChecks().isEmpty()) {
            return "## SLA / Quality Gate\n\nNo SLA thresholds configured.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## SLA / Quality Gate\n\n");
        sb.append("**Overall: ").append(result.isPassed() ? "PASS" : "FAIL").append("**\n\n");
        sb.append("| Metric | Actual | Target | Result |\n");
        sb.append("| :--- | :---: | :---: | :---: |\n");
        for (Check check : result.getChecks()) {
            sb.append(String.format(
                    "| %s | %s | %s | **%s** |\n",
                    check.getMetric(),
                    check.getActual(),
                    check.getTarget(),
                    check.getStatus().name()
            ));
        }
        return sb.toString();
    }

    /** Compact SLA block for launch description (Overall + table only). */
    public static String toLaunchDescription(Result result) {
        if (result.getChecks().isEmpty()) {
            return "No SLA thresholds configured.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Overall: ").append(result.isPassed() ? "PASS" : "FAIL").append("\n\n");
        sb.append("| Metric | Actual | Target | Result |\n");
        sb.append("| :--- | :---: | :---: | :---: |\n");
        for (Check check : result.getChecks()) {
            sb.append(String.format(
                    "| %s | %s | %s | %s |\n",
                    check.getMetric(),
                    check.getActual(),
                    check.getTarget(),
                    check.getStatus().name()
            ));
        }
        return sb.toString();
    }
}
