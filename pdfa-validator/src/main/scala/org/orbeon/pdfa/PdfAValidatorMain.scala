/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.pdfa

import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import org.verapdf.pdfa.validation.validators.ValidatorFactory

import java.io.File
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal


/**
 * Validate a PDF file against a PDF/A flavour with veraPDF (https://verapdf.org/).
 *
 * This runs in its own JVM, started by `PdfProductionTest`: veraPDF depends on Saxon-HE, which shares its
 * packages with Orbeon's Saxon, so it must not be on the Orbeon classpath.
 *
 * Usage: `PdfAValidatorMain <file.pdf> <flavour>` where the flavour is `1a`, `1b`, `2a`, `2b`, `2u`, `3a`, `3b`, `3u`...
 *
 * Output: a first line `COMPLIANT` or `NOT COMPLIANT`, then one line per failed rule with the number of failures.
 * Exit code: 0 if compliant, 1 if not, 2 on error.
 */
object PdfAValidatorMain {

  def main(args: Array[String]): Unit =
    args match {
      case Array(path, flavourId) =>
        try {
          System.exit(if (validate(new File(path), flavourId)) 0 else 1)
        } catch {
          case NonFatal(t) =>
            System.err.println(s"ERROR: ${t.getClass.getName}: ${t.getMessage}")
            System.exit(2)
        }
      case _ =>
        System.err.println("Usage: PdfAValidatorMain <file.pdf> <flavour>")
        System.exit(2)
    }

  def validate(file: File, flavourId: String): Boolean = {

    val flavour = PDFAFlavour.byFlavourId(flavourId)
    require(flavour != PDFAFlavour.NO_FLAVOUR, s"unknown PDF/A flavour `$flavourId`")

    VeraGreenfieldFoundryProvider.initialise()

    val parser = Foundries.defaultInstance().createParser(file, flavour)
    try {
      val result = ValidatorFactory.createValidator(flavour, false).validate(parser)

      println(if (result.isCompliant) "COMPLIANT" else "NOT COMPLIANT")

      val failedByRule =
        result.getTestAssertions.asScala
          .filter(_.getStatus == TestAssertion.Status.FAILED)
          .groupBy(_.getRuleId.toString)

      for ((_, assertions) <- failedByRule.toList.sortBy(_._1)) {
        val first = assertions.head
        val rule  = first.getRuleId
        println(s"${rule.getSpecification.getId} ${rule.getClause}-${rule.getTestNumber} (${assertions.size}): ${first.getMessage}")
      }

      result.isCompliant
    } finally
      parser.close()
  }
}
