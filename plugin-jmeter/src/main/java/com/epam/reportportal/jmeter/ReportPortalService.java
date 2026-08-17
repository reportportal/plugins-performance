package com.epam.reportportal.jmeter;

import com.epam.reportportal.common.PerformanceReporter;
import com.epam.reportportal.common.SampleFilter;
import com.epam.reportportal.common.SlaConfig;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import org.apache.commons.io.FileUtils;
import org.apache.jmeter.report.dashboard.ReportGenerator;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * JMeter-specific adapter: maps {@link SampleResult} to the shared reporter
 * and optionally generates the native JMeter HTML dashboard.
 */
public class ReportPortalService {
    private static final Logger logger = LoggerFactory.getLogger(ReportPortalService.class);

    private final PerformanceReporter reporter = new PerformanceReporter();

    private SampleFilter sampleFilter = SampleFilter.fromParameters("", "");
    private boolean attachHtmlDashboard = true;
    private File tempJtlFile;
    private BufferedWriter jtlWriter;

    public void init(String endpoint, String apiToken, String project, String launchName,
                     boolean attachHtmlDashboard, SlaConfig slaConfig, SampleFilter sampleFilter,
                     List<ItemAttributesRQ> customAttributes) {
        this.attachHtmlDashboard = attachHtmlDashboard;
        this.sampleFilter = sampleFilter != null
                ? sampleFilter
                : SampleFilter.fromParameters("", "");

        reporter.init(endpoint, apiToken, project, launchName, slaConfig, customAttributes);

        if (this.attachHtmlDashboard) {
            try {
                this.tempJtlFile = File.createTempFile("rp-jmeter-temp-", ".jtl");
                this.jtlWriter = new BufferedWriter(new FileWriter(tempJtlFile), 1024 * 1024);
                this.jtlWriter.write("timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect\n");
            } catch (IOException e) {
                logger.error("Failed to create temporary JTL file for HTML dashboard", e);
                this.attachHtmlDashboard = false;
            }
        }
    }

    public void processSample(SampleResult sr) {
        if (sr == null || sr.isIgnore()) {
            return;
        }
        if (!sampleFilter.accept(sr.getSampleLabel())) {
            return;
        }

        if (attachHtmlDashboard && jtlWriter != null) {
            writeJtlLine(sr);
        }

        reporter.processSample(JmeterSampleMapper.toPerformanceSample(sr));
    }

    public void shutdown() {
        if (jtlWriter != null) {
            try {
                jtlWriter.flush();
                jtlWriter.close();
            } catch (IOException e) {
                logger.error("Failed to close JTL writer", e);
            }
        }

        reporter.shutdown(() -> {
            if (attachHtmlDashboard && tempJtlFile != null && tempJtlFile.exists()) {
                generateAndAttachDashboard();
            }
        });
        cleanTempFiles();
    }

    private void writeJtlLine(SampleResult sr) {
        try {
            String line = String.format("%d,%d,%s,%s,%s,%s,%s,%b,%s,%d,%d,%d,%d,%s,%d,%d,%d\n",
                    sr.getTimeStamp(),
                    sr.getTime(),
                    JmeterSampleMapper.escapeCsv(sr.getSampleLabel()),
                    sr.getResponseCode(),
                    JmeterSampleMapper.escapeCsv(sr.getResponseMessage()),
                    JmeterSampleMapper.escapeCsv(sr.getThreadName()),
                    sr.getDataType(),
                    sr.isSuccessful(),
                    JmeterSampleMapper.escapeCsv(JmeterSampleMapper.getFirstAssertionFailureMessage(sr)),
                    sr.getBytesAsLong(),
                    sr.getSentBytes(),
                    sr.getGroupThreads(),
                    sr.getAllThreads(),
                    JmeterSampleMapper.escapeCsv(sr.getUrlAsString()),
                    sr.getLatency(),
                    sr.getIdleTime(),
                    sr.getConnectTime()
            );
            jtlWriter.write(line);
        } catch (IOException e) {
            logger.error("Failed to write sample to temporary JTL", e);
        }
    }

    private void generateAndAttachDashboard() {
        File tempReportDir = new File(System.getProperty("java.io.tmpdir"), "jmeter-rp-html-report-" + System.currentTimeMillis());
        File zipFile = new File(System.getProperty("java.io.tmpdir"), "jmeter-html-report-" + UUID.randomUUID() + ".zip");

        try {
            logger.info("Generating JMeter HTML dashboard");
            JMeterUtils.setProperty("jmeter.reportgenerator.outputdir", tempReportDir.getAbsolutePath());

            ReportGenerator generator = new ReportGenerator(tempJtlFile.getAbsolutePath(), null);
            generator.generate();
            logger.info("HTML dashboard generated");

            logger.info("Archiving report to ZIP");
            zipFolder(tempReportDir.toPath(), zipFile.toPath());

            byte[] zipBytes = Files.readAllBytes(zipFile.toPath());

            logger.info("Uploading HTML dashboard ZIP to ReportPortal");
            reporter.attachFileToSummary(
                    "Attached full interactive JMeter HTML Dashboard. Download the ZIP file, extract it, and open 'index.html' in your browser.",
                    zipBytes,
                    "application/zip",
                    "jmeter-html-report.zip"
            );
            logger.info("HTML dashboard uploaded");

        } catch (Exception e) {
            logger.error("Failed to generate or attach HTML dashboard to ReportPortal", e);
        } finally {
            try {
                if (tempReportDir.exists()) {
                    FileUtils.deleteDirectory(tempReportDir);
                }
                if (zipFile.exists()) {
                    Files.delete(zipFile.toPath());
                }
            } catch (IOException e) {
                logger.error("Failed to delete temporary directories", e);
            }
        }
    }

    private void zipFolder(Path sourceFolderPath, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
             Stream<Path> paths = Files.walk(sourceFolderPath)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourceFolderPath.relativize(path).toString());
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            logger.error("Failed to add file to ZIP: {}", path, e);
                        }
                    });
        }
    }

    private void cleanTempFiles() {
        try {
            if (tempJtlFile != null && tempJtlFile.exists()) {
                Files.delete(tempJtlFile.toPath());
                logger.info("Temporary JTL file deleted");
            }
        } catch (IOException e) {
            logger.error("Failed to clean up temporary JTL file", e);
        }
    }
}
