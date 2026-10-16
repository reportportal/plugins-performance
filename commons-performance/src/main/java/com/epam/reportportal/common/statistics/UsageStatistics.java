package com.epam.reportportal.common.statistics;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.LaunchImpl;
import com.epam.reportportal.service.statistics.DummyClient;
import com.epam.reportportal.service.statistics.Statistics;
import com.epam.reportportal.service.statistics.StatisticsClient;
import com.epam.reportportal.service.statistics.StatisticsService;
import com.epam.reportportal.service.statistics.item.StatisticsEvent;
import com.epam.reportportal.service.statistics.item.StatisticsItem;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Sends the {@code start_launch} Google Analytics event with {@code framework_version},
 * replacing the event client-java sends on its own.
 */
public final class UsageStatistics {
    private static final Logger logger = LoggerFactory.getLogger(UsageStatistics.class);
    private static final long FLUSH_TIMEOUT_SECONDS = 5;
    private static final String DISABLE_ENV = "AGENT_NO_ANALYTICS";
    // Same Measurement Protocol credentials client-java's StatisticsService uses.
    private static final String[] MEASUREMENT_CREDENTIALS = new String(
            Base64.getDecoder().decode("Ry1XUDU3UlNHOFhMOjUxREVTTzQ4UV9DbmlnbVEwY2JoYmc="), StandardCharsets.UTF_8)
            .split(":", 2);

    private final Statistics client;
    private final Maybe<?> event;

    private UsageStatistics(Statistics client, Maybe<?> event) {
        this.client = client;
        this.event = event;
    }

    /**
     * Must be called before {@link Launch#start()}. Returns {@code null} when analytics is disabled
     * or could not be scheduled.
     */
    public static UsageStatistics start(Launch launch, ListenerParameters params,
                                        String agentName, String frameworkVersion) {
        if (System.getenv(DISABLE_ENV) != null) {
            return null;
        }
        disableBundledStatistics(launch);
        try {
            StatisticsEvent startLaunch = new StatisticsEvent("start_launch");
            startLaunch.setParams(eventParams(agentName, frameworkVersion));
            StatisticsItem item = new StatisticsItem(clientId()).addEvent(startLaunch);
            Statistics client = new StatisticsClient(MEASUREMENT_CREDENTIALS[0], MEASUREMENT_CREDENTIALS[1], params);
            Maybe<?> event = client.send(item)
                    .doOnError(e -> logger.debug("Unable to send statistics event: {}", e.toString()))
                    .onErrorComplete()
                    .cache();
            event.subscribe();
            return new UsageStatistics(client, event);
        } catch (RuntimeException e) {
            logger.debug("Unable to schedule statistics event: {}", e.toString());
            return null;
        }
    }

    /**
     * Waits briefly for the event to be sent, then releases the HTTP client.
     */
    public void finish() {
        event.timeout(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS).onErrorComplete().blockingGet();
        try {
            client.close();
        } catch (IOException e) {
            logger.debug("Unable to close statistics client: {}", e.toString());
        }
    }

    private static void disableBundledStatistics(Launch launch) {
        if (!(launch instanceof LaunchImpl)) {
            return;
        }
        try {
            Field field = LaunchImpl.class.getDeclaredField("statisticsService");
            field.setAccessible(true);
            StatisticsService bundled = (StatisticsService) field.get(launch);
            field.set(launch, new StatisticsService(launch.getParameters(), new DummyClient()));
            if (bundled != null) {
                bundled.close();
            }
        } catch (ReflectiveOperationException e) {
            logger.debug("Unable to disable client-java statistics: {}", e.toString());
        }
    }

    private static Map<String, Object> eventParams(String agentName, String frameworkVersion) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("interpreter", "Java " + System.getProperty("java.version"));
        params.put("client_name", property("client.properties", "client.name", "client-java"));
        params.put("client_version", property("client.properties", "client.version", "unknown"));
        putIfPresent(params, "agent_name", agentName);
        putIfPresent(params, "agent_version", property(
                "META-INF/maven/com.epam.reportportal/commons-performance/pom.properties", "version", null));
        putIfPresent(params, "framework_version", frameworkVersion);
        return params;
    }

    private static void putIfPresent(Map<String, Object> params, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            params.put(key, value.trim());
        }
    }

    private static String property(String resource, String key, String fallback) {
        try (InputStream in = UsageStatistics.class.getClassLoader().getResourceAsStream(resource)) {
            Properties properties = new Properties();
            if (in != null) {
                properties.load(in);
            }
            String value = properties.getProperty(key);
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        } catch (IOException e) {
            return fallback;
        }
    }

    private static String clientId() {
        Path file = Paths.get(System.getProperty("user.home"), ".rp", "rp.properties");
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                logger.debug("Unable to read client id: {}", e.toString());
            }
        }
        String clientId = properties.getProperty("client.id");
        if (clientId != null && !clientId.trim().isEmpty()) {
            return clientId.trim();
        }
        clientId = UUID.randomUUID().toString();
        properties.setProperty("client.id", clientId);
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(out, null);
            }
        } catch (IOException e) {
            logger.debug("Unable to store client id: {}", e.toString());
        }
        return clientId;
    }
}
