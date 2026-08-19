package com.epam.reportportal.common;

import com.epam.ta.reportportal.ws.model.attribute.ItemAttributeResource;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tool-agnostic performance launch reporter: hierarchy, stats, SLA, and summary logs.
 */
public class PerformanceReporter {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceReporter.class);
    private static final int MAX_FAILED_SAMPLES_PER_REQUEST = 20;
    private static final String KEY_SEP = "\u0000";

    private final ReportPortalClient client = new ReportPortalClient();
    private final PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
    private final Map<String, Maybe<String>> scenarioSuites = new ConcurrentHashMap<>();
    private final Map<String, Maybe<String>> requestSuites = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> scenarioRequestKeys = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requestFailureCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> reportedFailedSamples = new ConcurrentHashMap<>();
    private final Set<String> failureLimitNotices = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean labelOverflowReported = new AtomicBoolean();

    private Maybe<String> summaryItemUuid;
    private SlaConfig slaConfig = new SlaConfig(null, null, null);
    private List<ItemAttributesRQ> customAttributes = Collections.emptyList();
    private SlaEvaluator.Result slaResult;

    public void init(String endpoint, String apiToken, String project, String launchName,
                     SlaConfig slaConfig, List<ItemAttributesRQ> customAttributes) {
        this.slaConfig = slaConfig != null ? slaConfig : new SlaConfig(null, null, null);
        this.customAttributes = customAttributes != null
                ? new ArrayList<>(customAttributes)
                : Collections.emptyList();

        client.startLaunch(endpoint, apiToken, project, launchName, this.customAttributes);

        this.summaryItemUuid = client.startRootItem(
                "Performance Summary Report",
                "STEP",
                Calendar.getInstance().getTime()
        );
    }

    public void processSample(PerformanceSample sample) {
        String label = trackedLabel(sample);
        statsCollector.registerSample(label, sample.getDurationMs(), sample.isSuccess());
        reportSampleHistory(sample, label);
    }

    /**
     * Keeps the number of reported request names bounded. Every new name allocates a histogram,
     * six map entries and a ReportPortal item, so dynamic names would exhaust heap and flood the API.
     */
    private String trackedLabel(PerformanceSample sample) {
        String label = sample.getLabel();
        if (requestSuites.size() < PerformanceStatsCollector.MAX_TRACKED_NAMES
                || requestSuites.containsKey(requestKey(sample.getScenarioName(), label))) {
            return label;
        }

        if (labelOverflowReported.compareAndSet(false, true)) {
            logger.warn("Reached {} distinct request names; the rest are reported under '{}'.",
                    PerformanceStatsCollector.MAX_TRACKED_NAMES, PerformanceStatsCollector.OVERFLOW_NAME);
        }
        return PerformanceStatsCollector.OVERFLOW_NAME;
    }

    /**
     * Hierarchy:
     * 1) Scenario: {scenario} (SUITE)
     * 2) Request label (SUITE)
     * 3) Failed sample STEPs only (capped at {@link #MAX_FAILED_SAMPLES_PER_REQUEST})
     */
    private void reportSampleHistory(PerformanceSample sample, String label) {
        String scenarioName = sample.getScenarioName();
        String requestKey = requestKey(scenarioName, label);

        Date sampleTime = new Date(sample.getTimestamp());

        Maybe<String> scenarioUuid = scenarioSuites.computeIfAbsent(scenarioName, name ->
                client.startRootItem("Scenario: " + name, "SUITE", sampleTime));

        Maybe<String> requestUuid = requestSuites.computeIfAbsent(requestKey, key -> {
            scenarioRequestKeys
                    .computeIfAbsent(scenarioName, ignored -> ConcurrentHashMap.newKeySet())
                    .add(requestKey);
            return client.startChildItem(scenarioUuid, label, "SUITE", sampleTime);
        });

        if (sample.isSuccess()) {
            return;
        }

        requestFailureCounts
                .computeIfAbsent(requestKey, ignored -> new AtomicInteger(0))
                .incrementAndGet();

        int failedIndex = reportedFailedSamples
                .computeIfAbsent(requestKey, ignored -> new AtomicInteger(0))
                .incrementAndGet();
        if (failedIndex > MAX_FAILED_SAMPLES_PER_REQUEST) {
            if (failureLimitNotices.add(requestKey)) {
                client.emitLog(requestUuid, "WARN", String.format(
                        "Failed request history capped at %d samples for '%s' in scenario '%s'. "
                                + "Further failures are counted in metrics only.",
                        MAX_FAILED_SAMPLES_PER_REQUEST,
                        label,
                        scenarioName
                ), Calendar.getInstance().getTime());
            }
            return;
        }

        Date startTime = sampleTime;
        Maybe<String> stepUuid = client.startChildItem(
                requestUuid,
                MetricsFormatter.sampleStepName(sample),
                "STEP",
                startTime
        );

        Date logTime = new Date(sample.getTimestamp() + sample.getDurationMs());
        client.emitLog(stepUuid, "ERROR", MetricsFormatter.failureDetail(sample), logTime);

        Date endTime = new Date(sample.getTimestamp() + Math.max(sample.getDurationMs(), 0));
        client.finishItem(stepUuid, "FAILED", endTime);
    }

    private String requestKey(String scenarioName, String label) {
        return scenarioName + KEY_SEP + label;
    }

    public void attachFileToSummary(String message, byte[] content, String contentType, String fileName) {
        client.saveLogSync(summaryItemUuid, resolvedUuid -> {
            SaveLogRQ rq = new SaveLogRQ();
            rq.setLevel("INFO");
            rq.setMessage(message);
            rq.setLogTime(Calendar.getInstance().getTime());

            SaveLogRQ.File file = new SaveLogRQ.File();
            file.setContent(content);
            file.setContentType(contentType);
            file.setName(fileName);
            rq.setFile(file);
            return rq;
        });
    }

    /**
     * Completes reporting. {@code beforeFinishItems} runs after summary logs and before items are closed
     * (used by tools that attach extra artifacts such as an HTML dashboard).
     */
    public void shutdown(Runnable beforeFinishItems) {
        if (!client.isStarted()) {
            return;
        }

        logger.info("Generating aggregated performance report");

        PerformanceStatsCollector.SamplerStats globalStats = statsCollector.getGlobalStats();
        this.slaResult = SlaEvaluator.evaluate(slaConfig, globalStats);

        emitAggregatedReportLogs(globalStats, slaResult);

        if (beforeFinishItems != null) {
            beforeFinishItems.run();
        }

        boolean slaFailed = slaConfig.hasAnyThreshold() && !slaResult.isPassed();
        Date now = Calendar.getInstance().getTime();

        client.finishItem(summaryItemUuid, slaFailed ? "FAILED" : "PASSED", now);

        for (Map.Entry<String, Maybe<String>> entry : requestSuites.entrySet()) {
            boolean requestFailed = requestFailureCounts
                    .getOrDefault(entry.getKey(), new AtomicInteger(0))
                    .get() > 0;
            client.finishItem(entry.getValue(), requestFailed ? "FAILED" : "PASSED", now);
        }

        for (Map.Entry<String, Maybe<String>> entry : scenarioSuites.entrySet()) {
            String scenarioName = entry.getKey();
            boolean scenarioFailed = scenarioRequestKeys
                    .getOrDefault(scenarioName, Set.of())
                    .stream()
                    .anyMatch(requestKey -> requestFailureCounts
                            .getOrDefault(requestKey, new AtomicInteger(0))
                            .get() > 0);
            client.finishItem(entry.getValue(), scenarioFailed ? "FAILED" : "PASSED", now);
        }

        client.finishLaunch(slaFailed ? "FAILED" : "PASSED", now);
        updateLaunchAttributes(globalStats, slaResult);
    }

    public void shutdown() {
        shutdown(null);
    }

    private void updateLaunchAttributes(PerformanceStatsCollector.SamplerStats globalStats,
                                        SlaEvaluator.Result slaResult) {
        if (globalStats.getTotal() <= 0) {
            return;
        }

        Set<ItemAttributeResource> attributes = new HashSet<>();
        attributes.add(createAttribute("p50", String.format("%d_ms", globalStats.getPercentile(50.0))));
        attributes.add(createAttribute("p95", String.format("%d_ms", globalStats.getPercentile(95.0))));
        attributes.add(createAttribute("p99", String.format("%d_ms", globalStats.getPercentile(99.0))));

        if (slaConfig.hasAnyThreshold()) {
            attributes.add(createAttribute("sla", slaResult.isPassed() ? "PASS" : "FAIL"));
        }

        attributes.addAll(customAttributes);
        client.updateLaunch(SlaEvaluator.toLaunchDescription(slaResult), attributes);
    }

    private void emitAggregatedReportLogs(PerformanceStatsCollector.SamplerStats globalStats,
                                          SlaEvaluator.Result slaResult) {
        emitSummaryLog("INFO", "# FINAL PERFORMANCE AGGREGATED REPORT");

        boolean slaFailed = slaConfig.hasAnyThreshold() && !slaResult.isPassed();
        emitSummaryLog(slaFailed ? "ERROR" : "INFO", SlaEvaluator.toMarkdown(slaResult));

        emitSummaryLog("INFO", MetricsFormatter.globalMetricsMarkdown(globalStats));
        emitSummaryLog("INFO", MetricsFormatter.perRequestMetricsMarkdown(statsCollector.getStatsMap().values()));
    }

    private void emitSummaryLog(String level, String message) {
        client.saveLogSync(summaryItemUuid, level, message, Calendar.getInstance().getTime());
    }

    private ItemAttributeResource createAttribute(String key, String value) {
        ItemAttributeResource attr = new ItemAttributeResource();
        attr.setKey(key);
        attr.setValue(value);
        return attr;
    }
}
