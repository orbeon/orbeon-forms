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

import org.orbeon.oxf.xforms.function.xxforms.{XXFormsProperty, XXFormsPropertiesStartsWith}
import java.util.{Map ⇒ JMap}
import collection.JavaConverters._

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
}
