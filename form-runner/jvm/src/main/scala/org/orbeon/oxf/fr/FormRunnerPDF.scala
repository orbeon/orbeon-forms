/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import java.{util ⇒ ju}

import org.orbeon.oxf.fr.FormRunner.{FormRunnerParams, _}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.URLFinder
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.function.xxforms.{XXFormsPropertiesStartsWith, XXFormsProperty}
import org.orbeon.saxon.functions.EscapeURI
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

import scala.collection.JavaConverters._

trait FormRunnerPDF {

  // Return mappings (formatName → expression) for all PDF formats in the properties
  //@XPathFunction
  def getPDFFormats: ju.Map[String, String] = {

    def propertiesStartingWithIt(prefix: String) =
      XXFormsPropertiesStartsWith.propertiesStartsWith(prefix).iterator map (_.getStringValue)

    val formatPairsIt =
      for {
        formatPropertyName ← propertiesStartingWithIt("oxf.fr.pdf.format")
        expression         ← XXFormsProperty.propertyAsString(formatPropertyName)
        formatName         = formatPropertyName split '.' last
      } yield
        formatName → expression

    formatPairsIt.toMap.asJava
  }

  // Return the PDF formatting expression for the given parameters
  //@XPathFunction
  def getPDFFormatExpression(
    pdfFormats : ju.Map[String, String],
    app        : String,
    form       : String,
    name       : String,
    dataType   : String
  ): String = {

    val propertyName = List("oxf.fr.pdf.map", app, form, name) ::: Option(dataType).toList mkString "."

    val expressionOpt =
      for {
        format     ← XXFormsProperty.propertyAsString(propertyName)
        expression ← Option(pdfFormats.get(format))
      } yield
        expression

    expressionOpt.orNull
  }

  // Build a PDF control id from the given HTML control
  //@XPathFunction
  def buildPDFFieldNameFromHTML(control: NodeInfo): String = {

    def isContainer(e: NodeInfo) = {
      val classes = e.attClasses
      classes("xbl-fr-section") || (classes("xbl-fr-grid") && (e \\ "table" exists (_.attClasses("fr-repeat"))))
    }

    def findControlName(e: NodeInfo) =
      getStaticIdFromId(e.id).trimAllToOpt flatMap FormRunner.controlNameFromIdOpt

    def ancestorContainers(e: NodeInfo) =
      control ancestor * filter isContainer reverse

    def suffixAsList(id: String) =
      getEffectiveIdSuffix(id).trimAllToOpt.toList

    // This only makes sense if we are passed a control with a name
    findControlName(control) map { controlName ⇒
      ((ancestorContainers(control) flatMap findControlName) :+ controlName) ++ suffixAsList(control.id) mkString "$"
     } orNull
  }

  import URLFinder._

  // Add http/https/mailto hyperlinks to a plain string
  //@XPathFunction
  def hyperlinkURLs(s: String, hyperlinks: Boolean): String =
    replaceURLs(s, if (hyperlinks) replaceWithHyperlink else replaceWithPlaceholder)

  // Custom filename (for PDF and TIFF output) for the detail page if specified and if evaluates to a non-empty name
  //@XPathFunction
  def filenameOrNull(format: String): String = (
    formRunnerProperty(s"oxf.fr.detail.$format.filename")(FormRunnerParams())
    flatMap trimAllToOpt
    flatMap (expr ⇒ process.SimpleProcess.evaluateString(expr).trimAllToOpt)
    map     (EscapeURI.escape(_, "-_.~").toString)
    orNull
  )
}

object FormRunnerPDF extends FormRunnerPDF