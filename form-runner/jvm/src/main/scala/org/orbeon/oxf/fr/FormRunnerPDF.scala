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

import java.{util => ju}

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.URLFinder
import org.orbeon.saxon.function.{PropertiesStartsWith, Property}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId

import scala.jdk.CollectionConverters._

trait FormRunnerPDF {

  val PdfFieldSeparator = "$"

  // Return mappings (formatName -> expression) for all PDF formats in the properties
  //@XPathFunction
  def getPDFFormats: ju.Map[String, String] = {

    def propertiesStartingWithIt(prefix: String) =
      PropertiesStartsWith.propertiesStartsWith(prefix).iterator map (_.getStringValue)

    val formatPairsIt =
      for {
        formatPropertyName <- propertiesStartingWithIt("oxf.fr.pdf.format")
        expression         <- Property.propertyAsString(formatPropertyName)
        formatName         = formatPropertyName split '.' last
      } yield
        formatName -> expression

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
        format     <- Property.propertyAsString(propertyName)
        expression <- Option(pdfFormats.get(format))
      } yield
        expression

    expressionOpt.orNull
  }

  // Build a PDF control id from the given HTML control
  // See also `findPdfFieldName`.
  //@XPathFunction
  def buildPDFFieldNameFromHTML(control: NodeInfo): String = {

    def isContainer(e: NodeInfo) = {
      val classes = e.attClasses
      classes("xbl-fr-section") || (classes("xbl-fr-grid") && (e descendant "table" exists (_.attClasses("fr-repeat"))))
    }

    def findControlName(e: NodeInfo) =
      XFormsId.getStaticIdFromId(e.id).trimAllToOpt flatMap FormRunner.controlNameFromIdOpt

    def ancestorContainers(e: NodeInfo) =
      control ancestor * filter isContainer reverse

    def suffixAsList(id: String) =
      XFormsId.getEffectiveIdSuffix(id).trimAllToOpt.toList

    // This only makes sense if we are passed a control with a name
    findControlName(control) map { controlName =>
      ((ancestorContainers(control) flatMap findControlName) :+ controlName) ++ suffixAsList(control.id) mkString PdfFieldSeparator
     } orNull
  }

  //@XPathFunction
  def optionFromMetadataOrPropertiesXPath(
    metadataInstanceRootElem : NodeInfo,
    featureName              : String,
    app                      : String,
    form                     : String,
    mode                     : String
  ): Option[String] =
    FormRunner.optionFromMetadataOrProperties(
      metadataInstanceRootElem = metadataInstanceRootElem,
      featureName              = featureName)(
      p                        = FormRunnerParams(app, form, 1, None, mode)
    )

  import URLFinder._

  // Add http/https/mailto hyperlinks to a plain string
  //@XPathFunction
  def hyperlinkURLs(s: String, hyperlinks: Boolean): String =
    replaceURLs(s, if (hyperlinks) replaceWithHyperlink else replaceWithPlaceholder)

}

object FormRunnerPDF extends FormRunnerPDF