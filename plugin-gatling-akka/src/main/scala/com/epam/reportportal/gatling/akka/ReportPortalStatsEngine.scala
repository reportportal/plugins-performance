package com.epam.reportportal.gatling.akka

import akka.actor.ActorRef
import io.gatling.commons.stats.KO
import io.gatling.commons.stats.Status
import io.gatling.core.session.GroupBlock
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.writer.RunMessage
import io.gatling.core.stats.writer.UserEndMessage

/**
 * Decorates Gatling's StatsEngine so every request is also reported to ReportPortal.
 */
final class ReportPortalStatsEngine(
    delegate: StatsEngine,
    handler: ReportPortalHandler,
    config: ReportPortalPluginConfig,
    runMessage: RunMessage
) extends StatsEngine {

  override def start(): Unit = {
    val launchNameOverride =
      if (config.launchName == ReportPortalPluginConfig.DefaultLaunchName)
        Option(runMessage.simulationClassName).filter(_.trim.nonEmpty)
      else None
    handler.start(launchNameOverride)
    delegate.start()
  }

  override def stop(controller: ActorRef, exception: Option[Exception]): Unit =
    try {
      handler.shutdown()
    } finally {
      delegate.stop(controller, exception)
    }

  override def logUserStart(scenario: String, timestamp: Long): Unit =
    delegate.logUserStart(scenario, timestamp)

  override def logUserEnd(userMessage: UserEndMessage): Unit =
    delegate.logUserEnd(userMessage)

  override def logResponse(
      scenario: String,
      groups: List[String],
      requestName: String,
      startTimestamp: Long,
      endTimestamp: Long,
      status: Status,
      responseCode: Option[String],
      message: Option[String]
  ): Unit = {
    handler.onResponse(
      scenario,
      groups,
      requestName,
      startTimestamp,
      endTimestamp,
      status,
      responseCode,
      message
    )
    delegate.logResponse(
      scenario,
      groups,
      requestName,
      startTimestamp,
      endTimestamp,
      status,
      responseCode,
      message
    )
  }

  override def logGroupEnd(scenario: String, groupBlock: GroupBlock, timestamp: Long): Unit =
    delegate.logGroupEnd(scenario, groupBlock, timestamp)

  override def logCrash(scenario: String, groups: List[String], requestName: String, error: String): Unit = {
    val now = System.currentTimeMillis()
    handler.onResponse(scenario, groups, requestName, now, now, KO, None, Some(error))
    delegate.logCrash(scenario, groups, requestName, error)
  }

  override def reportUnbuildableRequest(
      scenario: String,
      groups: List[String],
      requestName: String,
      errorMessage: String
  ): Unit =
    delegate.reportUnbuildableRequest(scenario, groups, requestName, errorMessage)
}
