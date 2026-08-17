package com.epam.reportportal.gatling.akka

import com.epam.reportportal.common.PerformanceReporter
import com.epam.reportportal.common.SampleFilter
import io.gatling.commons.stats.Status
import io.gatling.core.stats.writer.ResponseMessage
import org.slf4j.LoggerFactory

/**
 * Shared ReportPortal reporting state for a single Gatling run.
 * Used by both [[ReportPortalDataWriter]] and [[ReportPortalStatsEngine]].
 */
final class ReportPortalHandler(config: ReportPortalPluginConfig) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val reporter = new PerformanceReporter()
  private val sampleFilter: SampleFilter = config.sampleFilter
  @volatile private var started = false

  def start(launchNameOverride: Option[String] = None): Unit = synchronized {
    if (started) {
      return
    }
    val launchName = launchNameOverride.filter(_.trim.nonEmpty).getOrElse(config.launchName)
    logger.info("Starting ReportPortal reporting for Gatling launch '{}'", launchName)
    reporter.init(
      config.endpoint,
      config.apiToken,
      config.project,
      launchName,
      config.slaConfig,
      config.customAttributes
    )
    started = true
  }

  def onResponse(message: ResponseMessage): Unit = {
    if (!started) {
      return
    }
    val sample = GatlingSampleMapper.toSample(message)
    if (sampleFilter.accept(sample.getLabel)) {
      reporter.processSample(sample)
    }
  }

  def onResponse(
      scenario: String,
      groups: Seq[String],
      name: String,
      startTimestamp: Long,
      endTimestamp: Long,
      status: Status,
      responseCode: Option[String],
      message: Option[String]
  ): Unit = {
    if (!started) {
      return
    }
    val sample = GatlingSampleMapper.toSample(
      scenario,
      groups,
      name,
      startTimestamp,
      endTimestamp,
      status,
      responseCode.orNull,
      message.orNull
    )
    if (sampleFilter.accept(sample.getLabel)) {
      reporter.processSample(sample)
    }
  }

  def shutdown(): Unit = synchronized {
    if (!started) {
      return
    }
    logger.info("Finishing ReportPortal reporting for Gatling")
    try {
      reporter.shutdown()
    } finally {
      started = false
    }
  }

  def isStarted: Boolean = started
}
