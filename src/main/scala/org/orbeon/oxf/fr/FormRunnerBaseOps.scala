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
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

import scala.util.Try

trait FormRunnerBaseOps {

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
  def findInViewTryIndex(inDoc: NodeInfo, id: String) =
    findTryIndex(inDoc, id, findFRBodyElement(inDoc), includeSelf = false)

  def findInModelTryIndex(inDoc: NodeInfo, id: String) =
    findTryIndex(inDoc, id, findModelElement(inDoc), includeSelf = false)

  def findInBindsTryIndex(inDoc: NodeInfo, id: String) =
    findTryIndex(inDoc, id, findTopLevelBind(inDoc).get, includeSelf = true)

  private def findTryIndex(inDoc: NodeInfo, id: String, under: NodeInfo, includeSelf: Boolean): Option[NodeInfo] = {

    // NOTE: This is a rather crude way of testing the presence of the index! But we do know for now that this is
    // only called from the functions above, which search in a form's view, model, or binds, which implies the
    // existence of a form model.
    val hasIndex = inDoc.getDocumentRoot.selectID("fr-form-model") ne null

    def isUnder(node: NodeInfo) =
      if (includeSelf)
        node ancestorOrSelf * contains under
      else
        node ancestor * contains under

    def fromSearch =
      if (includeSelf)
        under descendantOrSelf * find (_.id == id)
      else
        under descendant * find (_.id == id)

    def fromIndex =
      Option(inDoc.getDocumentRoot.selectID(id)) match {
        case elemOpt @ Some(elem) if isUnder(elem) ⇒ elemOpt
        case Some(elem)                            ⇒ fromSearch
        case None                                  ⇒ None
      }

    if (hasIndex)
      fromIndex
    else
      fromSearch
  }

  // Get the body element assuming the structure of an XHTML document, annotated or not, OR the structure of xbl:xbl.
  // NOTE: annotate.xpl replaces fr:body with xf:group[@class = 'fb-body']
  def findFRBodyElement(inDoc: NodeInfo) = {

    def fromGroupById = Option(inDoc.getDocumentRoot.selectID("fb-body"))
    def fromGroup     = inDoc.rootElement \ "*:body" \\ XFGroupTest find (_.id == "fb-body")
    def fromFRBody    = inDoc.rootElement \ "*:body" \\ FRBodyTest headOption
    def fromTemplate  = inDoc.rootElement \ XBLTemplateTest headOption

    fromGroupById orElse fromGroup orElse fromFRBody orElse fromTemplate get
  }

  // Get the form model
  def findModelElement(inDoc: NodeInfo): NodeInfo = {

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

  private val TopLevelBindIds = Set("fr-form-binds", "fb-form-binds")

  // Find the top-level binds (marked with "fr-form-binds" or "fb-form-binds"), if any
  def findTopLevelBind(inDoc: NodeInfo): Option[NodeInfo] =
    findModelElement(inDoc) \ "*:bind" find {
      // There should be an id, but for backward compatibility also support ref/nodeset pointing to fr-form-instance
      bind ⇒ TopLevelBindIds(bind.id) || bindRefOrNodeset(bind).contains("instance('fr-form-instance')")
    }

  def properties = Properties.instance.getPropertySet

  def buildPropertyName(name: String)(implicit p: FormRunnerParams) =
    if (hasAppForm(p.app, p.form))
      name :: p.app :: p.form :: Nil mkString "."
    else
      name

  //@XPathFunction
  def xpathFormRunnerStringProperty(name: String) =
    formRunnerProperty(name)(FormRunnerParams())

  // Return a property using the form's app/name, None if the property is not defined
  def formRunnerProperty(name: String)(implicit p: FormRunnerParams) =
    Option(properties.getObject(buildPropertyName(name))) map (_.toString)

  // Return a boolean property using the form's app/name, false if the property is not defined
  def booleanFormRunnerProperty(name: String)(implicit p: FormRunnerParams) =
    Option(properties.getObject(buildPropertyName(name))) map (_.toString) contains "true"

  // Interrupt current processing and send an error code to the client.
  // NOTE: This could be done through ExternalContext
  //@XPathFunction
  def sendError(code: Int) = throw HttpStatusCodeException(code)
  def sendError(code: Int, resource: String) = throw HttpStatusCodeException(code, Option(resource))

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
  private val CreateOps    = Set("*", "create")
  private val ReadOps      = Set("*", "read")
  private val UpdateOps    = Set("*", "update")
  private val DeleteOps    = Set("*", "delete")

  def authorizedOperations = authorizedOperationsInstance.rootElement.stringValue.splitTo[Set]()

  def canCreate = authorizedOperations intersect CreateOps nonEmpty
  def canRead   = authorizedOperations intersect ReadOps   nonEmpty
  def canUpdate = authorizedOperations intersect UpdateOps nonEmpty
  def canDelete = authorizedOperations intersect DeleteOps nonEmpty

  // Captcha support
  def hasCaptcha    = formRunnerProperty("oxf.fr.detail.captcha")(FormRunnerParams()) exists Set("reCAPTCHA", "SimpleCaptcha")
  def captchaPassed = persistenceInstance.rootElement / "captcha" === "true"
  //@XPathFunction
  def showCaptcha   = hasCaptcha && Set("new", "edit")(FormRunnerParams().mode) && ! captchaPassed && ! isNoscript

  private val ReadonlyModes = Set("view", "pdf", "email", "controls")

  def isDesignTime   = FormRunnerParams().app == "orbeon" && FormRunnerParams().form == "builder"
  def isReadonlyMode = ReadonlyModes(FormRunner.FormRunnerParams().mode)

  def isNoscript     = inScopeContainingDocument.noscript
  def isEmbeddable   = inScopeContainingDocument.getRequestParameters.get(EmbeddableParam) map (_.head) contains "true"

  // The standard Form Runner parameters
  case class FormRunnerParams(app: String, form: String, formVersion: Int, document: Option[String], mode: String)

  object FormRunnerParams {
    def apply(): FormRunnerParams = {
      val params = parametersInstance.rootElement

      FormRunnerParams(
        app         = params elemValue "app",
        form        = params elemValue "form",
        formVersion = Try(params elemValue "form-version" toInt) getOrElse 1, // in `test` mode, for example, `form-version` is blank
        document    = params elemValue "document" trimAllToOpt,
        mode        = params elemValue "mode"
      )
    }
  }

  // Display a success message
  //@XPathFunction
  def successMessage(message: String): Unit = {
    setvalue(persistenceInstance.rootElement \ "message", message)
    toggle("fr-message-success")
  }

  // Display an error message
  //@XPathFunction
  def errorMessage(message: String): Unit =
    dispatch(name = "fr-show", targetId = "fr-error-dialog", properties = Map("message" → Some(message)))
}
