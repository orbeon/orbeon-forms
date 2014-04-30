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

import collection.JavaConverters._
import java.util.{Map ⇒ JMap}
import org.orbeon.oxf.util.ScalaUtils.nonEmptyOrNone
import org.orbeon.oxf.util.URLFinder
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.function.xxforms.{XXFormsProperty, XXFormsPropertiesStartsWith}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

trait FormRunnerPDF {

    // Return mappings (formatName → expression) for all PDF formats in the properties
    def getPDFFormats = {

        def propertiesStartingWith(prefix: String) =
            XXFormsPropertiesStartsWith.propertiesStartsWith(prefix).asScala map (_.getStringValue)

        val formatPairs =
            for {
                formatPropertyName ← propertiesStartingWith("oxf.fr.pdf.format")
                expression ← Option(XXFormsProperty.property(formatPropertyName)) map (_.getStringValue)
                formatName = formatPropertyName split '.' last
            } yield
                formatName → expression

        formatPairs.toMap.asJava
    }

    // Return the PDF formatting expression for the given parameters
    def getPDFFormatExpression(pdfFormats: JMap[String, String], app: String, form: String, name: String, dataType: String) = {
        val propertyName = Seq("oxf.fr.pdf.map", app, form, name) ++ Option(dataType).toSeq mkString "."

        val expressionOption =
            for {
                format ← Option(XXFormsProperty.property(propertyName)) map (_.getStringValue)
                expression ← Option(pdfFormats.get(format))
            } yield
                expression

        expressionOption.orNull
    }

    // Build a PDF control id from the given HTML control
    def buildPDFFieldNameFromHTML(control: NodeInfo) = {

        def isContainer(e: NodeInfo) = {
            val classes = e.attClasses
            classes("xbl-fr-section") || (classes("xbl-fr-grid") && (e \\ "table" exists (_.attClasses("fr-repeat"))))
        }

        def getStaticId(e: NodeInfo) =
            getStaticIdFromId(e.id)

        def ancestorContainers(e: NodeInfo) =
            control ancestor * filter isContainer reverse

        def nameParts(e: NodeInfo) =
            ancestorContainers(e) :+ e map getStaticId map FormRunner.controlNameFromId

        def suffixAsSeq(e: NodeInfo) =
            nonEmptyOrNone(getEffectiveIdSuffix(e.id)).toList

        // Join everything with "$" for PDF
        nameParts(control) ++ suffixAsSeq(control) mkString "$"
    }

    import URLFinder._

    // Add HTTP/HTTPS hyperlinks to a plain string
    def hyperlinkURLs(s: String, hyperlinks: Boolean) =
        replaceURLs(s, if (hyperlinks) insertHyperlink else insertPlaceholderHyperlink)
}
