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

import java.util.{Map ⇒ JMap, List ⇒ JList}
import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.Headers._
import org.orbeon.oxf.util._
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.function.xxforms.{XXFormsProperty, XXFormsPropertiesStartsWith}
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om.{DocumentInfo, Item, NodeInfo}
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.resources.URLFactory
import collection.JavaConverters._
import scala.util.control.NonFatal

object FormRunner {

    val NS = "http://orbeon.org/oxf/xml/form-runner"
    val XF = XFORMS_NAMESPACE_URI

    val PropertyPrefix = "oxf.fr.authentication."

    val MethodPropertyName                  = PropertyPrefix + "method"
    val ContainerRolesPropertyName          = PropertyPrefix + "container.roles" // NOTE: this could be inferred from form-builder-permissions.xml, right?
    val HeaderUsernamePropertyName          = PropertyPrefix + "header.username"
    val HeaderRolesPropertyName             = PropertyPrefix + "header.roles"
    val HeaderGroupPropertyName             = PropertyPrefix + "header.group"
    val HeaderRolesPropertyNamePropertyName = PropertyPrefix + "header.roles.property-name"

    val NameValueMatch = "([^=]+)=([^=]+)".r

    def properties = Properties.instance.getPropertySet

    def buildPropertyName(name: String)(implicit p: FormRunnerParams) =
        name :: p.app :: p.form :: Nil mkString "."

    // Return a property using the form's app/name, None if the property is not defined
    def formRunnerProperty(name: String)(implicit p: FormRunnerParams) =
        Option(properties.getObject(buildPropertyName(name))) map (_.toString)

    // Return a boolean property using the form's app/name, false if the property is not defined
    def booleanFormRunnerProperty(name: String)(implicit p: FormRunnerParams) =
        Option(properties.getObject(buildPropertyName(name))) map (_.toString) exists (_ == "true")

    type UserRoles = {
        def getRemoteUser(): String
        def isUserInRole(role: String): Boolean
    }

    /**
     * Get the username and roles from the request, based on the Form Runner configuration.
     */
    def getUserGroupRoles(userRoles: UserRoles, getHeader: String ⇒ Option[Array[String]]): (Option[String], Option[String], Option[Array[String]]) = {

        val propertySet = properties
        propertySet.getString(MethodPropertyName, "container") match {
            case "container" ⇒

                val username    = Option(userRoles.getRemoteUser)
                val rolesString = propertySet.getString(ContainerRolesPropertyName)

                if (rolesString eq null) {
                    (username, None, None)
                } else {

                    // Wrap exceptions as Liferay throws if the role is not available instead of returning false
                    def isUserInRole(role: String) =
                        try userRoles.isUserInRole(role)
                        catch { case NonFatal(_) ⇒ false}

                    val rolesArray = (
                        for {
                            role ← rolesString.split(""",|\s+""")
                            if isUserInRole(role)
                        } yield
                            role)

                    val roles = rolesArray match {
                        case Array() ⇒ None
                        case array   ⇒ Some(array)
                    }

                    (username, rolesArray.headOption, roles)
                }

            case "header" ⇒

                val headerPropertyName = propertySet.getString(HeaderRolesPropertyNamePropertyName, "").trim match {
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

                val username = headerOption(HeaderUsernamePropertyName) map (_.head)
                val group    = headerOption(HeaderGroupPropertyName) map (_.head)
                val roles    = headerOption(HeaderRolesPropertyName) map (_ flatMap split1 flatMap (split2(_)))

                (username, group, roles)

            case other ⇒ throw new OXFException("Unsupported authentication method, check the '" + MethodPropertyName + "' property:" + other)
        }
    }

    def getUserRolesAsHeaders(userRoles: UserRoles, getHeader: String ⇒ Option[Array[String]]): Map[String, Array[String]] = {

        val (username, group, roles) = getUserGroupRoles(userRoles, getHeader)
        Seq(
            username map ("orbeon-username" → Array(_)),
            group    map ("orbeon-group"    → Array(_)),
            roles    map ("orbeon-roles"    → _)
        ).flatten.toMap
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
                propertyName ← properties.propertiesStartsWith(propertyPrefix)
                lowerSuffix = propertyName.substring(propertyPrefix.length + 1)
                if lowerSuffix != "uri"
                headerName = "Orbeon-" + capitalizeSplitHeader(lowerSuffix)
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
    def authorizedOperationsOnForm(permissionsElement: NodeInfo): java.util.List[String] = {
        val request = NetUtils.getExternalContext.getRequest

        val permissions =
            if (permissionsElement eq null)
                Seq("*")                                                                // No permissions defined for this form, authorize any operation
            else
                (permissionsElement \ "permission"
                    filter (p ⇒
                        (p \ * isEmpty) ||                                              // No constraint on the permission, so it is automatically satisfied
                        (p \ "user-role" forall (r ⇒                                   // If we have user-role constraints, they must all pass
                            (r \@ "any-of" stringValue) split "\\s+"                    // Constraint is satisfied if user has at least one of the roles
                            map (_.replace("%20", " "))                                 // Unescape internal spaces as the roles used in Liferay are user-facing labels that can contain space (see also permissions.xbl)
                            exists request.isUserInRole)))                              // TODO: Remove limitation of only using container roles
                    flatMap (p ⇒ (p \@ "operations" stringValue) split "\\s+")         // For the permissions that passed, return the list operations
                    distinct)                                                           // Remove duplicate operations

        permissions.asJava
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

        createFormDataAttachmentPath(app, form, document, SecureUtils.randomHexId + ".bin")
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

        selectLangUseDefault(app, form, actualRequestedLang, availableFormLangs).orNull
    }

    // Get the Form Runner language
    // If possible, try to match the form language, otherwise
    def selectFormRunnerLang(app: String, form: String, formLang: String, formRunnerLangs: JList[String]): String =
        selectLangUseDefault(app, form, Option(formLang), formRunnerLangs.asScala.toList).orNull

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
    def summaryLanguage(name: String, resources: NodeInfo): String = {
        def resourceLabelOpt = (resources \ name \ "label" map (_.getStringValue)).headOption
        resourceLabelOpt getOrElse '[' + name + ']'
    }

    // Append a query string to a URL
    def appendQueryString(urlString: String, queryString: String) = NetUtils.appendQueryString(urlString, queryString)

    private def selectLangUseDefault(app: String, form: String, requestedLang: Option[String], availableLangs: List[String]) = {
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

    // Return specific Form Runner instances
    def formInstance         = topLevelInstance("fr-form-model",          "fr-form-instance")          get
    def parametersInstance   = topLevelInstance("fr-parameters-model",    "fr-parameters-instance")    get
    def errorSummaryInstance = topLevelInstance("fr-error-summary-model", "fr-error-summary-instance") get
    def persistenceInstance  = topLevelInstance("fr-persistence-model",   "fr-persistence-instance")   get

    def currentFRResources   = asNodeInfo(topLevelModel("fr-resources-model").get.getVariable("fr-fr-resources"))
    def currentFormResources = asNodeInfo(topLevelModel("fr-resources-model").get.getVariable("fr-form-resources"))

    // Whether the form data is valid
    // We use instance('fr-error-summary-instance')/valid and not xxf:valid() because the instance validity may not be
    // reflected with the use of XBL components.
    def dataValid = errorSummaryInstance.rootElement \ "valid" === "true"

    // Whether the form has a captcha
    def hasCaptcha = formRunnerProperty("oxf.fr.detail.captcha")(FormRunnerParams()) exists Set("reCAPTCHA", "SimpleCaptcha")

    // The standard Form Runner parameters
    case class FormRunnerParams(app: String, form: String, document: Option[String], mode: String)

    object FormRunnerParams {
        def apply(): FormRunnerParams = {
            val params = parametersInstance.rootElement

            FormRunnerParams(
                app      = params \ "app",
                form     = params \ "form",
                document = nonEmptyOrNone(params \ "document"),
                mode     = params \ "mode"
            )
        }
    }

    // Retrieves a form from the persistence layer
    def readPublishedForm(appName: String, formName: String)(implicit logger: IndentedLogger): Option[DocumentInfo] = {
        val uri = "/fr/service/persistence/crud/" + appName + "/" + formName + "/form/form.xhtml"
        val urlString = URLRewriterUtils.rewriteServiceURL(NetUtils.getExternalContext.getRequest, uri, URLRewriter.REWRITE_MODE_ABSOLUTE)
        val url = URLFactory.createURL(urlString)

        val headers = Connection.buildConnectionHeaders(None, Map(), Option(Connection.getForwardHeaders))
        val connectionResult = Connection("GET", url, credentials = None, messageBody = None, headers = headers, loadState = true, logBody = false).connect(saveState = true)

        // Libraries are typically not present. In that case, the persistence layer should return a 404 (thus the first test),
        // but the MySQL persistence layer returns a [200 with an empty body][1] (thus the second test).
        //   [1]: https://github.com/orbeon/orbeon-forms/issues/771
        (connectionResult.statusCode == 200 && connectionResult.hasContent) option
            useAndClose(connectionResult.getResponseInputStream) { inputStream ⇒
                // do process XInclude, so FB's model gets included
                TransformerUtils.readTinyTree(XPathCache.getGlobalConfiguration, inputStream, url.toString, true, false)
            }
    }

    // Display a success message
    def successMessage(message: String): Unit = {
        setvalue(persistenceInstance.rootElement \ "message", message)
        toggle("fr-message-success")
    }

    // Display an error message
    def errorMessage(message: String): Unit =
        dispatch(name = "fr-show", targetId = "fr-error-dialog", properties = Map("message" → Some(message)))
}