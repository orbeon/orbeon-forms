/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.orbeon.oxf.properties.Properties
import scala.collection.JavaConversions._
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xml._

object FormRunner {

    def getHeaders(app: String, form: String, formOrData: String) = {

        require(app.nonEmpty)
        require(form.nonEmpty)
        require(Set("form", "data")(formOrData))

        val propertySet = Properties.instance.getPropertySet

        // Find provider
        val providerProperty = Seq("oxf.fr.persistence.provider", app, form, formOrData) mkString "."
        val provider = propertySet.getString(providerProperty)

        // Find provider URI
//        val uriProperty = Seq("oxf.fr.persistence", provider, "uri") mkString "."
//        val uri = propertySet.getObject(uriProperty)

        val propertyPrefix = "oxf.fr.persistence." + provider

        // Build headers document
        val headers =
            <headers>{
                for {
                    propertyName <- propertySet.getPropertiesStartsWith(propertyPrefix)
                    lowerSuffix = propertyName.substring(propertyPrefix.length + 1)
                    if lowerSuffix != "uri"
                    upperSuffix = lowerSuffix split '-' map (_.capitalize) mkString "-"
                    headerName = "Orbeon-" + upperSuffix
                    headerValue = propertySet.getObject(propertyName).toString
                } yield
                    <header><name>{XMLUtils.escapeXMLMinimal(headerName)}</name><value>{XMLUtils.escapeXMLMinimal(headerValue)}</value></header>
            }</headers>.toString

        // Convert to TinyTree
        TransformerUtils.stringToTinyTree(XPathCache.getGlobalConfiguration, headers, false, false)
    }
}