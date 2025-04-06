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

import org.orbeon.oxf.fr.XMLNames.FR

import java.util as ju
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.URLFinder
import org.orbeon.saxon.function.{PropertiesStartsWith, Property}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsId

import scala.jdk.CollectionConverters.*

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

  // Return a map of control names to their custom PDF field name, if present
  //@XPathFunction
  def getControlsMap(inDoc: NodeInfo): ju.Map[String, String] = {

    implicit val ctx: FormRunnerDocContext = new InDocFormRunnerDocContext(inDoc)

    val mapping =
      for {
        control      <- FormRunner.getAllControlsWithIdsExcludeContainers
        pdfFieldName <- control.attValueOpt(FR -> "pdf-template-field-name").flatMap(_.trimAllToOpt)
        controlName  = FormRunner.controlNameFromId(control.id)
      } yield {
        controlName -> pdfFieldName
      }

    mapping.toMap.asJava
  }

  // Build a PDF control id from the given HTML control.
  // See also `findPdfFieldName`.
  //@XPathFunction
  def buildPDFFieldNameFromHTML(
     htmlControlElem: NodeInfo,
     controlsMap    : ju.Map[String, String]
  ): String = {

    def findControlName(e: NodeInfo): Option[String] =
      XFormsId.getStaticIdFromId(e.id).trimAllToOpt.flatMap(FormRunner.controlNameFromIdOpt)

    val htmlControlName = findControlName(htmlControlElem)

    def fromControlMap: Option[String] =
      htmlControlName.flatMap(name => Option(controlsMap.get(name)))

    def isContainer(e: NodeInfo) = {
      val classes = e.attClasses
      classes("xbl-fr-section") || (classes("xbl-fr-grid") && (e descendant "table" exists (_.attClasses("fr-repeat"))))
    }

    def ancestorContainers(e: NodeInfo) =
      htmlControlElem ancestor * filter isContainer reverse

    def suffixAsList(id: String) =
      XFormsId.getEffectiveIdSuffix(id).trimAllToOpt.toList

    // This only makes sense if we are passed a control with a name
    def fromControlName: Option[String] =
      htmlControlName map { controlName =>
        ((ancestorContainers(htmlControlElem) flatMap findControlName) :+ controlName) ++ suffixAsList(htmlControlElem.id) mkString PdfFieldSeparator
      }

    fromControlMap.orElse(fromControlName).orNull
  }

  // Used by:
  //
  // - `print-pdf-notemplate.xsl`: `rendered-page-orientation`/`rendered-page-size`
  // - Form Builder: `html-page-layout`
  // - `view.xsl`: `html-page-layout`
  //
  //@XPathFunction
  def optionFromMetadataOrPropertiesXPath(
    metadataInstanceRootElemOrNull: NodeInfo,
    featureName                   : String,
    app                           : String,
    form                          : String,
    mode                          : String
  ): Option[String] =
    FormRunner.optionFromMetadataOrProperties(
      metadataInstanceRootElemOpt = Option(metadataInstanceRootElemOrNull),
      featureName                 = featureName
    )(
      formRunnerParams            = FormRunnerParams(app, form, 1, None, None, mode)
    )

  import URLFinder._

  // Add http/https/mailto hyperlinks to a plain string
  //@XPathFunction
  def hyperlinkURLs(s: String, hyperlinks: Boolean): String =
    replaceURLs(s, if (hyperlinks) replaceWithHyperlink else replaceWithPlaceholder)
}

object FormRunnerPDF extends FormRunnerPDF