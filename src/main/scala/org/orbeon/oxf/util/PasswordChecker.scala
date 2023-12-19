package org.orbeon.oxf.util

import me.gosimple.nbvcxz.Nbvcxz
import org.log4s.Logger

import scala.jdk.CollectionConverters._


object PasswordChecker {

  def checkAndLog(propertyName: String, rawPassword: String)(implicit logger: Logger): Boolean = {

    val passwordChecker = new Nbvcxz(
      (new me.gosimple.nbvcxz.resources.ConfigurationBuilder)
        .setMinimumEntropy(40d)
        .createConfiguration
    )

    val checkResult = passwordChecker.estimate(rawPassword)
    val scoreLine   = s"score (0-4): ${checkResult.getBasicScore}"

    if (checkResult.isMinimumEntropyMet) {
      logger.info(s"Password for `$propertyName` property is strong enough: $scoreLine")
    } else {

      val warningLine =
        Option(checkResult.getFeedback).flatMap(f => Option(f.getWarning)) map { warning =>
          s"warning: $warning"
        }

      val suggestionLines =
        Option(checkResult.getFeedback).toList.flatMap(f => f.getSuggestion.asScala)

      val lines = scoreLine :: warningLine.toList ::: suggestionLines

      logger.error(
        s"Password for `$propertyName` property is not strong enough:\n${lines.map(l => s"- $l\n").mkString}"
      )
    }

    checkResult.isMinimumEntropyMet
  }
}
