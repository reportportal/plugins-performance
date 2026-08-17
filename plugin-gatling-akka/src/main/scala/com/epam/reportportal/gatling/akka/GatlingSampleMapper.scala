package com.epam.reportportal.gatling.akka

import com.epam.reportportal.common.PerformanceSample
import io.gatling.commons.stats.OK
import io.gatling.commons.stats.Status
import io.gatling.core.stats.writer.ResponseMessage

object GatlingSampleMapper {

  def requestLabel(groups: Seq[String], name: String): String =
    if (groups.isEmpty) name
    else groups.mkString(" / ") + " / " + name

  def toSample(message: ResponseMessage): PerformanceSample =
    toSample(
      scenario = message.scenario,
      groups = message.groupHierarchy,
      name = message.name,
      startTimestamp = message.startTimestamp,
      endTimestamp = message.endTimestamp,
      status = message.status,
      responseCode = optionAsString(message.responseCode),
      message = optionAsString(message.message)
    )

  def toSample(
      scenario: String,
      groups: Seq[String],
      name: String,
      startTimestamp: Long,
      endTimestamp: Long,
      status: Status,
      responseCode: String,
      message: String
  ): PerformanceSample = {
    val duration = math.max(0L, endTimestamp - startTimestamp)
    val success = status == OK
    new PerformanceSample(
      requestLabel(groups, name),
      scenario,
      scenario,
      startTimestamp,
      duration,
      success,
      if (responseCode == null) "" else responseCode,
      if (message == null) "" else message,
      null,
      message
    )
  }

  private def optionAsString(value: Option[String]): String =
    value.orNull
}
