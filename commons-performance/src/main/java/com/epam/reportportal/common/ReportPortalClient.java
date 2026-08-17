package com.epam.reportportal.common;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.PathParamInterceptor;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributeResource;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.LaunchResource;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.launch.UpdateLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.reactivex.Maybe;
import okhttp3.CookieJar;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.HttpException;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Tool-agnostic ReportPortal HTTP client: authentication, launch lifecycle, items, and logs.
 */
public class ReportPortalClient {
    private static final Logger logger = LoggerFactory.getLogger(ReportPortalClient.class);

    private ReportPortal rp;
    private Launch launch;

    public void startLaunch(String endpoint, String apiToken, String project, String launchName,
                            Collection<ItemAttributesRQ> attributes) {
        logger.info("Initializing ReportPortal client");

        ListenerParameters params = new ListenerParameters();
        params.setBaseUrl(endpoint);
        params.setApiKey(apiToken);
        params.setProjectName(project);
        params.setLaunchName(launchName);
        params.setEnable(true);

        // client-java's default CookieJar accumulates Set-Cookie values (SSO/session)
        // and eventually triggers Tomcat 400 "Request header is too large".
        // API-key auth does not need cookies.
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .retryOnConnectionFailure(true)
                .addInterceptor(new PathParamInterceptor("projectName", project));

        this.rp = ReportPortal.builder()
                .withParameters(params)
                .withHttpClient(httpClient)
                .build();

        StartLaunchRQ startLaunchRQ = new StartLaunchRQ();
        startLaunchRQ.setName(launchName);
        startLaunchRQ.setStartTime(Calendar.getInstance().getTime());
        if (attributes != null && !attributes.isEmpty()) {
            startLaunchRQ.setAttributes(new HashSet<>(attributes));
        }

        this.launch = rp.newLaunch(startLaunchRQ);
        this.launch.start();
    }

    public Maybe<String> startRootItem(String name, String type, Date startTime) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName(name);
        rq.setType(type);
        rq.setStartTime(startTime);
        return launch.startTestItem(rq);
    }

    public Maybe<String> startChildItem(Maybe<String> parentUuid, String name, String type, Date startTime) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName(name);
        rq.setType(type);
        rq.setStartTime(startTime);
        return launch.startTestItem(parentUuid, rq);
    }

    public void finishItem(Maybe<String> itemUuid, String status, Date endTime) {
        FinishTestItemRQ finish = new FinishTestItemRQ();
        finish.setEndTime(endTime);
        finish.setStatus(status);
        launch.finishTestItem(itemUuid, finish);
    }

    public void emitLog(Maybe<String> itemUuid, Function<String, SaveLogRQ> logSupplier) {
        ReportPortal.emitLog(itemUuid, logSupplier);
    }

    public void emitLog(Maybe<String> itemUuid, String level, String message, Date logTime) {
        emitLog(itemUuid, resolvedUuid -> {
            SaveLogRQ rq = new SaveLogRQ();
            rq.setItemUuid(resolvedUuid);
            rq.setLevel(level);
            rq.setMessage(message);
            rq.setLogTime(logTime);
            return rq;
        });
    }

    public void finishLaunch(String status, Date endTime) {
        logger.info("Finishing ReportPortal launch");
        FinishExecutionRQ finishLaunch = new FinishExecutionRQ();
        finishLaunch.setEndTime(endTime);
        finishLaunch.setStatus(status);
        launch.finish(finishLaunch);
        logger.info("ReportPortal launch finished");
    }

    public void updateLaunch(String description, Set<ItemAttributeResource> attributes) {
        try {
            logger.info("Updating launch attributes with performance metrics and SLA results");
            String launchUuid = launch.start().blockingGet();
            logger.info("Launch UUID: {}", launchUuid);

            LaunchResource serverLaunch = launch.getClient()
                    .getLaunchByUuid(launchUuid)
                    .blockingGet();

            if (serverLaunch == null) {
                logger.error("Failed to retrieve launch from server; attributes update skipped");
                return;
            }

            Long dbLaunchId = serverLaunch.getLaunchId();
            logger.info("Resolved launch database ID: {}", dbLaunchId);

            UpdateLaunchRQ updateRq = new UpdateLaunchRQ();
            updateRq.setDescription(description);
            updateRq.setAttributes(attributes);
            launch.getClient().updateLaunch(String.valueOf(dbLaunchId), updateRq).blockingGet();
            logger.info("Updated launch description and attributes with performance metrics and SLA results");
        } catch (HttpException e) {
            try {
                String errorBody = e.response().errorBody() != null
                        ? e.response().errorBody().string()
                        : "Empty response body";
                logger.error("ReportPortal updateLaunch rejected request. HTTP status: {}, details: {}",
                        e.code(), errorBody);
            } catch (Exception ioException) {
                logger.error("Failed to read HTTP error response body", ioException);
            }
        } catch (Exception e) {
            logger.error("Unexpected error during launch attributes update", e);
        }
    }

    public boolean isStarted() {
        return launch != null;
    }
}
