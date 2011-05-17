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
import org.orbeon.oxf.xml._
import scala.collection.JavaConversions._
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.ScalaUtils._

object FormRunner {

    val propertyPrefix = "oxf.fr.authentication."

    val methodPropertyName = propertyPrefix + "method"
    val containerRolesPropertyName = propertyPrefix + "container.roles"
    val headerUsernamePropertyName = propertyPrefix + "header.username"
    val headerRolesPropertyName = propertyPrefix + "header.roles"

    type UserRoles = {
        def getRemoteUser(): String
        def isUserInRole(role: String): Boolean
    }

    /**
     * Get the username and roles from the request, based on the Form Runner configuration.
     */
    def getUserRoles(userRoles: UserRoles, getHeader: String => Option[Array[String]]): (Option[String], Option[Array[String]]) = {

        val propertySet = Properties.instance.getPropertySet
        propertySet.getString(methodPropertyName, "container") match {
            case "container" =>

                val rolesString = propertySet.getString(containerRolesPropertyName)

                assert(rolesString != null)

                val username = Option(userRoles.getRemoteUser)

                val rolesArray = (
                    for {
                        role <- rolesString.split(""",\s+""")
                        if userRoles.isUserInRole(role)
                    } yield
                        role)

                val roles = rolesArray match {
                    case Array() => None
                    case array => Some(array)
                }

                (username, roles)

            case "header" =>

                def headerOption(name: String) = Option(propertySet.getString(name)) flatMap (p => getHeader(p.toLowerCase))

                val username = headerOption(headerUsernamePropertyName) map (_.head)
                val roles = headerOption(headerRolesPropertyName) map (_ flatMap (_.split("""(\s*[,\|]\s*)+""")))

                (username, roles)

            case other => throw new OXFException("Unsupported authentication method, check the '" + methodPropertyName + "' property:" + other)
        }
    }

    def getUserRolesAsHeaders(userRoles: UserRoles, getHeader: String => Option[Array[String]]) = {

        val (username, roles) = FormRunner.getUserRoles(userRoles, getHeader)

        val result = collection.mutable.Map[String, Array[String]]()

        username foreach (u => result += ("orbeon-username" -> Array(u)))
        roles foreach (r => result += ("orbeon-roles" -> r))

        result.toMap
    }

    def getPersistenceURLHeaders(app: String, form: String, formOrData: String) = {

        require(app.nonEmpty)
        require(form.nonEmpty)
        require(Set("form", "data")(formOrData))

        val propertySet = Properties.instance.getPropertySet

        // Find provider
        def findProvider = {
            val providerProperty = Seq("oxf.fr.persistence.provider", app, form, formOrData) mkString "."
            propertySet.getString(providerProperty)
        }

        val provider = findProvider

        // Find provider URI
        def findProviderURL = {
            val uriProperty = Seq("oxf.fr.persistence", provider, "uri") mkString "."
            propertySet.getStringOrURIAsString(uriProperty)
        }

        val propertyPrefix = "oxf.fr.persistence." + provider

        // Build headers map
        val headers = (
            for {
                propertyName <- propertySet.getPropertiesStartsWith(propertyPrefix)
                lowerSuffix = propertyName.substring(propertyPrefix.length + 1)
                if lowerSuffix != "uri"
                headerName = "Orbeon-" + capitalizeHeader(lowerSuffix)
                headerValue = propertySet.getObject(propertyName).toString
            } yield
                (headerName -> headerValue)) toMap

        (findProviderURL, headers)
    }

    def getPersistenceHeadersAsXML(app: String, form: String, formOrData: String) = {

        val (uri, headers) = getPersistenceURLHeaders(app, form, formOrData)

        // Build headers document
        val headersXML =
            <headers>{
                for {
                    (name, value) <- headers
                } yield
                    <header><name>{XMLUtils.escapeXMLMinimal(name)}</name><value>{XMLUtils.escapeXMLMinimal(value)}</value></header>
            }</headers>.toString

        // Convert to TinyTree
        TransformerUtils.stringToTinyTree(XPathCache.getGlobalConfiguration, headersXML, false, false)
    }
}