package com.epam.reportportal.gatling.akka

import io.gatling.commons.util.Clock
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.writer.DataWriter
import io.gatling.core.stats.writer.Init
import io.gatling.core.stats.writer.LoadEventMessage
import io.gatling.core.stats.writer.ResponseMessage

/**
 * Gatling 3.6 DataWriter that streams request results to ReportPortal.
 *
 * Gatling OSS does not load custom writer names from `gatling.data.writers`.
 * Use [[Gatling]] as the process main class (recommended), or mix in
 * [[ReportPortalSimulation]] to import `simulation.log` after the run.
 *
 * Expected constructor signature matches built-in writers:
 * `(Clock, GatlingConfiguration)`.
 */
class ReportPortalDataWriter(_clock: Clock, _configuration: GatlingConfiguration)
    extends DataWriter[ReportPortalData] {

  override def onInit(init: Init): ReportPortalData = {
    val config = ReportPortalPluginConfig.load()
    val launchName =
      if (config.launchName == ReportPortalPluginConfig.DefaultLaunchName)
        Option(init.runMessage.simulationClassName).filter(_.trim.nonEmpty).getOrElse(config.launchName)
      else config.launchName
    val handler = new ReportPortalHandler(config)
    handler.start(Some(launchName))
    new ReportPortalData(handler)
  }

  override def onFlush(data: ReportPortalData): Unit = ()

  override def onMessage(message: LoadEventMessage, data: ReportPortalData): Unit =
    message match {
      case response: ResponseMessage => data.handler.onResponse(response)
      case _                         => ()
    }

  override def onCrash(cause: String, data: ReportPortalData): Unit =
    logger.error(s"Gatling DataWriter crash: $cause")

  override def onStop(data: ReportPortalData): Unit =
    data.handler.shutdown()
}
