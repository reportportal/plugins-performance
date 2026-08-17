package com.epam.reportportal.jmeter;

import com.epam.reportportal.common.CustomLaunchAttributes;
import com.epam.reportportal.common.SampleFilter;
import com.epam.reportportal.common.SlaConfig;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PerformanceReporterClient extends AbstractBackendListenerClient {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceReporterClient.class);

    private ReportPortalService rpService;

    @Override
    public Arguments getDefaultParameters() {
        Arguments arguments = new Arguments();
        arguments.addArgument("ReportPortal_URL", "http://localhost:8080");
        arguments.addArgument("Project_Name", "default_personal");
        arguments.addArgument("API_Token", "your_api_token_here");
        arguments.addArgument("Launch_Name", "JMeter Performance Metrics");
        arguments.addArgument("Attach_HTML_Dashboard", "true");
        // Leave blank to disable a check. Values are global (whole launch) thresholds.
        arguments.addArgument("SLA_P95_MS", "");
        arguments.addArgument("SLA_P99_MS", "");
        arguments.addArgument("SLA_ERROR_RATE_PCT", "");
        // Sample filtering: include/exclude by label regex.
        arguments.addArgument("Sample_Include_Regex", "");
        arguments.addArgument("Sample_Exclude_Regex", "");
        // Optional Attribute_1..Attribute_5 are not listed by default — add them
        // in the Backend Listener table when custom launch attributes are needed.
        return arguments;
    }

    @Override
    public void setupTest(BackendListenerContext context) throws Exception {
        logger.info("Initializing Performance Reporter plugin");

        String url = context.getParameter("ReportPortal_URL");
        String project = context.getParameter("Project_Name");
        String token = context.getParameter("API_Token");
        String launch = context.getParameter("Launch_Name");
        boolean attachHtml = Boolean.parseBoolean(context.getParameter("Attach_HTML_Dashboard"));

        SlaConfig slaConfig = SlaConfig.fromParameters(
                context.getParameter("SLA_P95_MS"),
                context.getParameter("SLA_P99_MS"),
                context.getParameter("SLA_ERROR_RATE_PCT")
        );

        SampleFilter sampleFilter = SampleFilter.fromParameters(
                context.getParameter("Sample_Include_Regex"),
                context.getParameter("Sample_Exclude_Regex")
        );

        List<ItemAttributesRQ> customAttributes =
                CustomLaunchAttributes.fromParameters(
                        context.getParameter("Attribute_1"),
                        context.getParameter("Attribute_2"),
                        context.getParameter("Attribute_3"),
                        context.getParameter("Attribute_4"),
                        context.getParameter("Attribute_5")
                );

        this.rpService = new ReportPortalService();
        this.rpService.init(url, token, project, launch, attachHtml, slaConfig, sampleFilter, customAttributes);

        super.setupTest(context);
    }

    @Override
    public void handleSampleResults(List<SampleResult> results, BackendListenerContext context) {
        if (rpService == null || results == null) {
            return;
        }
        for (SampleResult sr : results) {
            rpService.processSample(sr);
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) throws Exception {
        logger.info("Test finished, completing ReportPortal reporting");
        if (this.rpService != null) {
            this.rpService.shutdown();
        }
        super.teardownTest(context);
    }
}
