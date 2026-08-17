package io.gatling.app

import akka.actor.ActorSystem
import com.epam.reportportal.gatling.akka.ReportPortalHandler
import com.epam.reportportal.gatling.akka.ReportPortalPluginConfig
import com.epam.reportportal.gatling.akka.ReportPortalStatsEngine
import io.gatling.commons.util.Clock
import io.gatling.commons.util.DefaultClock
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.scenario.SimulationParams
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.writer.RunMessage
import io.netty.channel.EventLoopGroup

/**
 * Lives in `io.gatling.app` because Gatling 3.6 keeps [[Runner]] package-private.
 */
class ReportPortalRunner(
    system: ActorSystem,
    eventLoopGroup: EventLoopGroup,
    clock: Clock,
    configuration: GatlingConfiguration
) extends Runner(system, eventLoopGroup, clock, configuration) {

  override def newStatsEngine(simulationParams: SimulationParams, runMessage: RunMessage): StatsEngine = {
    val delegate = super.newStatsEngine(simulationParams, runMessage)
    val config = ReportPortalPluginConfig.load()
    val handler = new ReportPortalHandler(config)
    new ReportPortalStatsEngine(delegate, handler, config, runMessage)
  }
}

object ReportPortalRunner {
  def apply(
      system: ActorSystem,
      eventLoopGroup: EventLoopGroup,
      configuration: GatlingConfiguration
  ): ReportPortalRunner =
    new ReportPortalRunner(system, eventLoopGroup, new DefaultClock, configuration)
}
