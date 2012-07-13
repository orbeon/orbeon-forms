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
import org.orbeon.scaxon.XML._
import scala.collection.JavaConverters._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.oxf.xforms.function.xxforms.{XXFormsProperty, XXFormsPropertiesStartsWith}
import java.util.{Map ⇒ JMap, List ⇒ JList}
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.function.Random
import org.orbeon.oxf.util.{SecureUtils, NetUtils, XPathCache}
import org.apache.commons.lang.StringUtils
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.webapp.HttpStatusCodeException

object FormRunner {

    val NS = "http://orbeon.org/oxf/xml/form-runner"
    val XF = XFORMS_NAMESPACE_URI

    val propertyPrefix = "oxf.fr.authentication."

    val methodPropertyName = propertyPrefix + "method"
    val containerRolesPropertyName = propertyPrefix + "container.roles" // NOTE: this could be inferred from form-builder-permissions.xml, right?
    val headerUsernamePropertyName = propertyPrefix + "header.username"
    val headerRolesPropertyName = propertyPrefix + "header.roles"
    val headerRolesPropertyNamePropertyName = propertyPrefix + "header.roles.property-name"

    val NameValueMatch = "([^=]+)=([^=]+)".r

    private def properties = Properties.instance.getPropertySet

    type UserRoles = {
        def getRemoteUser(): String
        def isUserInRole(role: String): Boolean
    }

    /**
     * Get the username and roles from the request, based on the Form Runner configuration.
     */
    def getUserRoles(userRoles: UserRoles, getHeader: String ⇒ Option[Array[String]]): (Option[String], Option[Array[String]]) = {

        val propertySet = properties
        propertySet.getString(methodPropertyName, "container") match {
            case "container" ⇒

                val rolesString = propertySet.getString(containerRolesPropertyName)

                assert(rolesString != null)

                val username = Option(userRoles.getRemoteUser)

                val rolesArray = (
                    for {
                        role ← rolesString.split(""",|\s+""")
                        if userRoles.isUserInRole(role)
                    } yield
                        role)

                val roles = rolesArray match {
                    case Array() ⇒ None
                    case array ⇒ Some(array)
                }

                (username, roles)

            case "header" ⇒

                val headerPropertyName = propertySet.getString(headerRolesPropertyNamePropertyName, "").trim match {
                    case "" ⇒ None
                    case value ⇒ Some(value)
                }

                def headerOption(name: String) = Option(propertySet.getString(name)) flatMap (p ⇒ getHeader(p.toLowerCase))

                // Headers can be separated by comma or pipe
                def split1(value: String) = value split """(\s*[,\|]\s*)+"""
                // Then, if configured, a header can have the form name=value, where name is specified in a property
                def split2(value: String) = headerPropertyName match {
                    case Some(propertyName) ⇒
                        value match {
                            case NameValueMatch(`propertyName`, value) ⇒ Seq(value)
                            case _ ⇒ Seq()
                        }
                    case _ ⇒ Seq(value)
                }

                val username = headerOption(headerUsernamePropertyName) map (_.head)
                val roles = headerOption(headerRolesPropertyName) map (_ flatMap split1 flatMap (split2(_)))

                (username, roles)

            case other ⇒ throw new OXFException("Unsupported authentication method, check the '" + methodPropertyName + "' property:" + other)
        }
    }

    def getUserRolesAsHeaders(userRoles: UserRoles, getHeader: String ⇒ Option[Array[String]]) = {

        val (username, roles) = getUserRoles(userRoles, getHeader)

        val result = collection.mutable.Map[String, Array[String]]()

        username foreach (u ⇒ result += "orbeon-username" → Array(u))
        roles foreach (r ⇒ result += "orbeon-roles" → r)

        result.toMap
    }

    def getPersistenceURLHeaders(app: String, form: String, formOrData: String) = {

        require(augmentString(app).nonEmpty)
        require(augmentString(form).nonEmpty)
        require(Set("form", "data")(formOrData))

        // Find provider
        val provider = {
            val providerProperty = Seq("oxf.fr.persistence.provider", app, form, formOrData) mkString "."
            properties.getString(providerProperty)
        }

        getPersistenceURLHeadersFromProvider(provider)
    }

    def getPersistenceURLHeadersFromProvider(provider: String) = {

        // Find provider URI
        def findProviderURL = {
            val uriProperty = Seq("oxf.fr.persistence", provider, "uri") mkString "."
            properties.getStringOrURIAsString(uriProperty)
        }

        val propertyPrefix = "oxf.fr.persistence." + provider

        // Build headers map
        val headers = (
            for {
                propertyName ← properties.getPropertiesStartsWith(propertyPrefix).asScala
                lowerSuffix = propertyName.substring(propertyPrefix.length + 1)
                if lowerSuffix != "uri"
                headerName = "Orbeon-" + capitalizeHeader(lowerSuffix)
                headerValue = properties.getObject(propertyName).toString
            } yield
                headerName → headerValue) toMap

        (findProviderURL, headers)
    }

    def getPersistenceHeadersAsXML(app: String, form: String, formOrData: String) = {

        val (_, headers) = getPersistenceURLHeaders(app, form, formOrData)

        // Build headers document
        val headersXML =
            <headers>{
                for {
                    (name, value) ← headers
                } yield
                    <header><name>{XMLUtils.escapeXMLMinimal(name)}</name><value>{XMLUtils.escapeXMLMinimal(value)}</value></header>
            }</headers>.toString

        // Convert to TinyTree
        TransformerUtils.stringToTinyTree(XPathCache.getGlobalConfiguration, headersXML, false, false)
    }

    /**
     * Given the metadata for a form, returns the sequence of operations that the current user is authorized to perform.
     * The sequence can contain just the "*" string to denote that the user is allowed to perform any operation.
     */
    def authorizedOperationsOnForm(metadata: NodeInfo): java.util.List[String] = {
        val request = NetUtils.getExternalContext.getRequest
        (metadata \ "permissions" match {
            case Seq() ⇒ Seq("*")                                                      // No permissions defined for this form, authorize any operation
            case ps ⇒ ( ps \ "permission"
                    filter (p ⇒
                        (p \ * isEmpty) ||                                              // No constraint on the permission, so it is automatically satisfied
                        (p \ "user-role" forall (r ⇒                                   // If we have user-role constraints, they must all pass
                            (r \@ "any-of" stringValue) split "\\s+"                    // Constraint is satisfied if user has at least one of the roles
                            map (_.replace("%20", " "))                                 // Unescape internal spaces as the roles used in Liferay are user-facing labels that can contain space (see also permissions.xbl)
                            exists request.isUserInRole)))
                    flatMap (p ⇒ (p \@ "operations" stringValue) split "\\s+")         // For the permissions that passed, return the list operations
                    distinct                                                            // Remove duplicate operations
                )
        }) asJava
    }

    def getFormBuilderPermissionsAsXML(formRunnerRoles: NodeInfo): NodeInfo = {
        val request = NetUtils.getExternalContext.getRequest
        // Whether in container or header mode, roles are parsed into the Orbeon-Roles header at this point
        getFormBuilderPermissionsAsXML(formRunnerRoles, Option(request.getHeaderValuesMap.get("orbeon-roles")) getOrElse Array[String]() toSet)
    }

    def getFormBuilderPermissionsAsXML(formRunnerRoles: NodeInfo, incomingRoleNames: Set[String]): NodeInfo = {

        val appForms = getFormBuilderPermissions(formRunnerRoles, incomingRoleNames)

        if (appForms.isEmpty)
            <apps has-roles="false" all-roles=""/>
        else
            // Result document contains a tree structure of apps and forms
            <apps has-roles="true" all-roles={incomingRoleNames mkString " "}>{
                appForms map { case (app, forms) ⇒
                    <app name={app}>{ forms map { form ⇒ <form name={form}/> } }</app>
                }
            }</apps>
    }

    def getFormBuilderPermissions(formRunnerRoles: NodeInfo, incomingRoleNames: Set[String]): Map[String, Set[String]] = {

        val configuredRoles = formRunnerRoles.root \ * \ "role"
        if (configuredRoles.isEmpty) {
            // No role configured
            Map()
        } else {
            // Roles configured
            val allConfiguredRoleNames = configuredRoles map (_.attValue("name")) toSet
            val applicableRoleNames = allConfiguredRoleNames & incomingRoleNames
            val applicableRoles = configuredRoles filter (e ⇒ (applicableRoleNames + "*")(e.attValue("name")))
            val applicableAppNames = applicableRoles map (_.attValue("app")) toSet

            if (applicableAppNames("*")) {
                // User has access to all apps (and therefore all forms)
                Map("*" → Set("*"))
            } else {
                // User has access to certain apps only
                (for {
                    app ← applicableAppNames
                    forms = {
                        val applicableFormsForApp = applicableRoles filter (_.attValue("app") == app) map (_.attValue("form")) toSet

                        if (applicableFormsForApp("*")) Set("*") else applicableFormsForApp
                    }
                } yield
                    app → forms) toMap
            }
        }
    }

    private def isAuthorized(appForms: Map[String, Set[String]], app: String, form: String) = {
        // Authorized if access to all apps OR if access to current app AND (access to all forms in app OR to specific form in app)
        (appForms contains "*") || (appForms.get(app) map (_ & Set("*", form) nonEmpty) getOrElse false)
    }

    // Interrupt current processing and send an error code to the client.
    // NOTE: This could be done through ExternalContext
    def sendError(code: Int) = throw new HttpStatusCodeException(code)
    def sendError(code: Int, resource: String) = throw new HttpStatusCodeException(code, Option(resource))

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

    // Check whether a value correspond to an uploaded file
    //
    // For this to be true
    // - the protocol must be file:
    // - the URL must have a valid signature
    //
    // This guarantees that the local file was in fact placed there by the upload control, and not tampered with.
    def isUploadedFileURL(value: String): Boolean =
        value.startsWith("file:/") && XFormsUploadControl.verifyMAC(value)

    // Create a new attachment path
    def createAttachmentPath(app: String, form: String, document: String, localURL: String): String = {

        // Here we could decide to use a nicer extension for the file. But since initially the filename comes from the
        // client, it cannot be trusted, nor can its mediatype. A first step would be to do content-sniffing to
        // determine a more trusted mediatype. A second step would be to put in an API for virus scanning. For now, we
        // just use .bin as an extension.

        //val nonTrustedFilename = XFormsUploadControl.getParameter(localURL, "filename")
        //val nonTrustedMediatype = XFormsUploadControl.getParameter(localURL, "mediatype")

        val randomHex = SecureUtils.digestString(Random.evaluate(true).toString, "MD5", "hex")
        createFormDataAttachmentPath(app, form, document, randomHex + ".bin")
    }

    // Path for a form definition attachment
    def createFormDefinitionAttachmentPath(app: String, form: String, filename: String): String =
        "/fr/service/persistence/crud/" + app + '/' + form + "/form/" + filename

    // Path for a form data attachment
    def createFormDataAttachmentPath(app: String, form: String, document: String, filename: String): String =
        "/fr/service/persistence/crud/" + app + '/' + form + "/data/" + document + '/' + filename

    // Whether the given path is an attachment path
    def isDataAttachmentPath(app: String, form: String, document: String, path: String): Boolean =
        path.startsWith("/fr/service/persistence/crud/" + app + '/' + form + "/data/" + document + '/') &&
            path.endsWith(".bin")

    // For a given attachment path, return the filename
    def getAttachmentPathFilename(path: String): String = path.split('/').last

    // List of available languages for the given form
    // Empty if the form doesn't have resources
    // If all of the form's resources are filtered via property, return the first language of the form, if any.
    def getFormLangSelection(app: String, form: String, formLanguages: JList[String]): JList[String] = {

        val allowedFormLanguages = formLanguages.asScala.toList filter isAllowedLang(app, form)
        val defaultLanguage = getDefaultLang(app, form)

        val withDefaultPrepended =
            if (allowedFormLanguages contains defaultLanguage)
                defaultLanguage :: (allowedFormLanguages filterNot (_ == defaultLanguage))
            else
                allowedFormLanguages

        withDefaultPrepended.asJava
    }

    // Find the best match for the current form language
    // Can be null (empty sequence) if there are no resources (or no allowed resources) in the form
    def selectFormLang(app: String, form: String, requestedLang: String, formLangs: JList[String]): String = {

        val availableFormLangs  = getFormLangSelection(app, form, formLangs).asScala.toList
        val actualRequestedLang = findRequestedLang(app, form, requestedLang) filter isAllowedLang(app, form)

        selectLang(app, form, actualRequestedLang, availableFormLangs).orNull
    }

    // Get the Form Runner language
    // If possible, try to match the form language, otherwise
    def selectFormRunnerLang(app: String, form: String, formLang: String, formRunnerLangs: JList[String]): String =
        selectLang(app, form, Option(formLang), formRunnerLangs.asScala.toList).orNull

    // Get the default language for the given app/form
    // If none is configured, return the global default "en"
    // Public for unit tests
    def getDefaultLang(app: String, form: String): String =
        Option(properties.getString(Seq("oxf.fr.default-language", app, form) mkString ".")) getOrElse "en"

    // Return a predicate telling whether a language is allowed for the given form, based on properties
    // Public for unit tests
    def isAllowedLang(app: String, form: String): String ⇒ Boolean = {
        val set = stringOptionToSet(Option(properties.getString(Seq("oxf.fr.available-languages", app, form) mkString ".")))
        // If none specified via property or property contains a wildcard, all languages are considered available
        if (set.isEmpty || set("*")) (_ ⇒ true) else set
    }

    // The requested language, trying a few things in order (given parameter, request, session, default)
    // Public for unit tests
    def findRequestedLang(app: String, form: String, requestedLang: String): Option[String] = {
        val request = NetUtils.getExternalContext.getRequest

        def fromRequest = Option(request.getParameterMap.get("fr-language")) flatMap (_.lift(0)) map (_.toString)
        def fromSession = stringFromSession(request, "fr-language")

        Option(StringUtils.trimToNull(requestedLang)) orElse
            fromRequest orElse
            fromSession orElse
            Option(getDefaultLang(app, form))
    }

    // Get a field's label for the summary page
    def summaryLanguage(name: String, resources: NodeInfo, inlineLabel: String): String = {
        def resourceLabelOpt = resources \ name \ "label" map (_.getStringValue) headOption
        def inlineLabelOpt   = nonEmptyOrNone(inlineLabel)

        resourceLabelOpt orElse inlineLabelOpt getOrElse '[' + name + ']'
    }

    private def selectLang(app: String, form: String, requestedLang: Option[String], availableLangs: List[String]) = {
        def matchingLanguage = availableLangs intersect requestedLang.toList headOption
        def defaultLanguage  = availableLangs intersect List(getDefaultLang(app, form)) headOption
        def firstLanguage    = availableLangs headOption

        matchingLanguage orElse defaultLanguage orElse firstLanguage
    }

    private def stringFromSession(request: Request, name: String) =
        Option(request.getSession(false)) flatMap
            (s ⇒ Option(s.getAttributesMap.get("fr-language"))) map {
                case item: Item ⇒ item.getStringValue
                case other ⇒ other.toString
            }
}