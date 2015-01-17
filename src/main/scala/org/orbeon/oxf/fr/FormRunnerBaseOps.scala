/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import XMLNames._

trait FormRunnerBaseOps {

    val XH = XHTML_NAMESPACE_URI
    val XF = XFORMS_NAMESPACE_URI
    val XS = XSD_URI
    val XBL = XBL_NAMESPACE_URI
    val FR = "http://orbeon.org/oxf/xml/form-runner"

    val NoscriptParam         = "fr-noscript"
    val LanguageParam         = "fr-language"
    val EmbeddableParam       = "orbeon-embeddable"

    val LiferayLanguageHeader = "orbeon-liferay-language"

    val ParametersModel       = "fr-parameters-model"
    val PersistenceModel      = "fr-persistence-model"
    val ResourcesModel        = "fr-resources-model"
    val FormModel             = "fr-form-model"
    val ErrorSummaryModel     = "fr-error-summary-model"
    val SectionsModel         = "fr-sections-model"

    val TemplateSuffix        = "-template"

    // Get an id based on a name
    // NOTE: The idea as of 2011-06-21 is that we support reading indiscriminately the -control, -grid
    // suffixes, whatever type of actual control they apply to. The idea is that in the end we might decide to just use
    // -control. OTOH we must have distinct ids for binds, controls and templates, so the -bind, -control and -template
    // suffixes must remain.
    def bindId(controlName: String)    = controlName + "-bind"
    def gridId(gridName: String)       = gridName    + "-grid"
    def controlId(controlName: String) = controlName + "-control"
    def templateId(gridName: String)   = gridName    + TemplateSuffix

    def defaultIterationName(repeatName: String) = repeatName + "-iteration"

    // Find a view element by id, using the index if possible, otherwise traversing the document
    // NOTE: Searching by traversing if no index should be done directly in the selectID implementation.
    def findInViewTryIndex(inDoc: NodeInfo, id: String) = {

        val bodyElement = findFRBodyElement(inDoc)

        def isUnderView(node: NodeInfo) =
            node ancestor * contains bodyElement

        Option(inDoc.getDocumentRoot.selectID(id)) filter isUnderView orElse (bodyElement descendant * find (_.id == id))
    }

    // Get the body element assuming the structure of an XHTML document, annotated or not, OR the structure of xbl:xbl.
    // NOTE: annotate.xpl replaces fr:body with xf:group[@class = 'fb-body']
    def findFRBodyElement(inDoc: NodeInfo) = {

        def fromGroup    = inDoc.rootElement \ "*:body" \\ XFGroupTest find (_.attClasses("fb-body"))
        def fromFRBody   = inDoc.rootElement \ "*:body" \\ FRBodyTest headOption
        def fromTemplate = inDoc.rootElement \ XBLTemplateTest headOption

        fromGroup orElse fromFRBody orElse fromTemplate get
    }

    // Get the form model
    def findModelElement(inDoc: NodeInfo) = {

        def fromHead           = inDoc.rootElement \ "*:head" \ XFModelTest find (hasIdValue(_, FormModel))
        def fromImplementation = inDoc.rootElement \ XBLImplementationTest \ XFModelTest headOption

        fromHead orElse fromImplementation head
    }

    // Find an xf:instance element
    def instanceElement(inDoc: NodeInfo, id: String) =
        findModelElement(inDoc) \ "*:instance" find (hasIdValue(_, id))

    // Find an inline instance's root element
    def inlineInstanceRootElement(inDoc: NodeInfo, id: String) =
        instanceElement(inDoc, id).toList \ * headOption

    // Find all template instances
    def templateInstanceElements(inDoc: NodeInfo) =
        findModelElement(inDoc) \ "*:instance" filter (_.id endsWith TemplateSuffix)

    // Get the root element of instances
    def formInstanceRoot(inDoc: NodeInfo)      = inlineInstanceRootElement(inDoc, "fr-form-instance").get
    def metadataInstanceRoot(inDoc: NodeInfo)  = inlineInstanceRootElement(inDoc, "fr-form-metadata")
    def resourcesInstanceRoot(inDoc: NodeInfo) = inlineInstanceRootElement(inDoc, "fr-form-resources").get

    // Find the top-level binds (marked with "fr-form-binds" or "fb-form-binds"), if any
    def findTopLevelBind(inDoc: NodeInfo): Option[NodeInfo] =
        findModelElement(inDoc) \ "*:bind" find {
            // There should be an id, but for backward compatibility also support ref/nodeset pointing to fr-form-instance
            bind ⇒ Set("fr-form-binds", "fb-form-binds")(bind.id)             ||
                bindRefOrNodeset(bind) == Some("instance('fr-form-instance')")
        }

    def properties = Properties.instance.getPropertySet

    def buildPropertyName(name: String)(implicit p: FormRunnerParams) =
        if (hasAppForm(p.app, p.form))
            name :: p.app :: p.form :: Nil mkString "."
        else
            name

    // Return a property using the form's app/name, None if the property is not defined
    def formRunnerProperty(name: String)(implicit p: FormRunnerParams) =
        Option(properties.getObject(buildPropertyName(name))) map (_.toString)

    // Return a boolean property using the form's app/name, false if the property is not defined
    def booleanFormRunnerProperty(name: String)(implicit p: FormRunnerParams) =
        Option(properties.getObject(buildPropertyName(name))) map (_.toString) exists (_ == "true")

    // Interrupt current processing and send an error code to the client.
    // NOTE: This could be done through ExternalContext
    def sendError(code: Int) = throw new HttpStatusCodeException(code)
    def sendError(code: Int, resource: String) = throw new HttpStatusCodeException(code, Option(resource))

    // Append a query string to a URL
    def appendQueryString(urlString: String, queryString: String) = NetUtils.appendQueryString(urlString, queryString)

    // Return specific Form Runner instances
    def formInstance                 = topLevelInstance(FormModel,         "fr-form-instance")          get
    def metadataInstance             = topLevelInstance(FormModel,         "fr-form-metadata")

    def parametersInstance           = topLevelInstance(ParametersModel,   "fr-parameters-instance")    get
    def errorSummaryInstance         = topLevelInstance(ErrorSummaryModel, "fr-error-summary-instance") get
    def persistenceInstance          = topLevelInstance(PersistenceModel,  "fr-persistence-instance")   get
    def authorizedOperationsInstance = topLevelInstance(PersistenceModel,  "fr-authorized-operations")  get

    // See also FormRunnerHome
    private val UpdateOps    = Set("*", "update")

    def authorizedOperations = split[Set](authorizedOperationsInstance.rootElement.stringValue)
    def supportsUpdate       = authorizedOperations intersect UpdateOps nonEmpty

    // Captcha support
    def hasCaptcha    = formRunnerProperty("oxf.fr.detail.captcha")(FormRunnerParams()) exists Set("reCAPTCHA", "SimpleCaptcha")
    def captchaPassed = persistenceInstance.rootElement / "captcha" === "true"
    def showCaptcha   = hasCaptcha && Set("new", "edit")(FormRunnerParams().mode) && ! captchaPassed && ! isNoscript

    def isNoscript    = containingDocument.noscript
    def isEmbeddable  = containingDocument.getRequestParameters.get(EmbeddableParam) map (_.head) exists (_ == "true")

    // The standard Form Runner parameters
    case class FormRunnerParams(app: String, form: String, formVersion: String, document: Option[String], mode: String)

    object FormRunnerParams {
        def apply(): FormRunnerParams = {
            val params = parametersInstance.rootElement

            FormRunnerParams(
                app         = params \ "app",
                form        = params \ "form",
                formVersion = params \ "form-version",
                document    = nonEmptyOrNone(params \ "document"),
                mode        = params \ "mode"
            )
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
