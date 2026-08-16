package com.epam.reportportal.gatling.akka

import io.gatling.app.ReportPortalGatling
import io.gatling.core.scenario.Simulation

/**
 * Drop-in replacement for `io.gatling.app.Gatling` that reports every request to ReportPortal.
 *
 * Configure the Gatling Maven plugin (or bundle) main class as
 * `com.epam.reportportal.gatling.akka.Gatling`.
 */
object Gatling {

  def main(args: Array[String]): Unit = ReportPortalGatling.main(args)

  def fromMap(overrides: collection.mutable.Map[String, _]): Int =
    ReportPortalGatling.fromMap(overrides)

  def fromArgs(args: Array[String], selectedSimulationClass: Option[Class[Simulation]]): Int =
    ReportPortalGatling.fromArgs(args, selectedSimulationClass)
}
