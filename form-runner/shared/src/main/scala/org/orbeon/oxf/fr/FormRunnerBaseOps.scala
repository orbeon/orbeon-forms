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

import enumeratum.*
import enumeratum.EnumEntry.Lowercase
import org.log4s
import org.orbeon.fr.rpc.Router
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.fr.Names.*
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.fr.definitions.ModeType
import org.orbeon.oxf.fr.library.FRComponentParamSupport
import org.orbeon.oxf.http.{Headers, HttpStatusCodeException}
import org.orbeon.oxf.properties.{Property, PropertySet}
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, DateUtils, HtmlParsing, IndentedLogger}
import org.orbeon.oxf.xforms.Loggers
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xml.{ElemFilter, SaxonUtils}
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.orbeon.xml.NamespaceMapping

import scala.collection.immutable


// The standard Form Runner parameters
case class FormRunnerParams(
  app         : String, // curiously we allow this to be `"*"`
  form        : String, // curiously we allow this to be `"*"`
  formVersion : Int,
  document    : Option[String],
  isDraft     : Option[Boolean],
  mode        : String
) {
  def appForm: AppForm =
    AppForm(app, form)

  def appFormVersion: FormRunnerParams.AppFormVersion =
    (appForm, formVersion)

  def modeType: ModeType =
    ModeType.unapply(mode).getOrElse(throw new IllegalArgumentException(s"Invalid mode: `$mode`"))
}

object FormRunnerParams {

  def apply(): FormRunnerParams =
    apply(frc.parametersInstance.get.rootElement)

  def apply(paramsRootElem: NodeInfo): FormRunnerParams =
    FormRunnerParams(
      app         = paramsRootElem.elemValue("app"),
      form        = paramsRootElem.elemValue("form"),
      formVersion = paramsRootElem.elemValue("form-version").toIntOption.getOrElse(1), // in `test` mode, for example, `form-version` is blank
      document    = paramsRootElem.elemValue("document").trimAllToOpt,
      isDraft     = paramsRootElem.elemValue("draft").trimAllToOpt.map(_ == "true"),
      mode        = paramsRootElem.elemValue("mode")
    )

  def apply(appForm: AppForm, mode: String): FormRunnerParams =
    FormRunnerParams(
      app         = appForm.app,
      form        = appForm.form,
      formVersion = 1,
      document    = None,
      isDraft     = None,
      mode        = mode
    )

  type AppFormVersion = (AppForm, Int)
}

// Like `FormRunnerParams`, but for cases where we want to support a potentially missing `fr-parameters-instance`,
// because we want that code to also be able to run outside of Form Runner.
object FormRunnerParamsOpt {

  def apply(): Option[FormRunnerParams] =
    frc.parametersInstance.map(_.rootElement).map(FormRunnerParams.apply)

}

case class AppForm(app: String, form: String) {

  val toList: List[String] = List(app, form)

  def isSpecificAppForm: Boolean = AppForm.isSpecificAppForm(app, form)

  def isNotLessSpecificThan(other: AppForm): Boolean = {
    (! AppForm.isSpecificPart(other.app)  || AppForm.isSpecificPart(app)) &&
    (! AppForm.isSpecificPart(other.form) || AppForm.isSpecificPart(form))
  }
}

object AppForm {
  val FormBuilder: AppForm = AppForm("orbeon", "builder")

  def isSpecificPart(part: String): Boolean =
    part != "*" && part.nonEmpty

  def isSpecificAppForm(app: String, form: String): Boolean =
    isSpecificPart(app) && isSpecificPart(form)

  def apply(appOpt: Option[String], formOpt: Option[String]): Option[AppForm] =
    (appOpt.flatMap(_.trimAllToOpt), formOpt.flatMap(_.trimAllToOpt)) match {
      case (None, None)            => None
      case (Some(app), Some(form)) => Some(AppForm(app, form))
      case _                       => throw new IllegalArgumentException("Both `app` and `form` should be defined or both should be `None`")
    }
}

case class AppFormOpt(app: String, formOpt: Option[String])

object AppFormOpt {
  def apply(appOpt: Option[String], formOpt: Option[String]): Option[AppFormOpt] =
    (appOpt, formOpt) match {
      case (None, Some(_)) => throw new IllegalArgumentException("Application should be defined if form is defined")
      case _               => appOpt.map(AppFormOpt(_, formOpt))
    }
}

trait FormRunnerBaseOps extends FormRunnerPlatform {

  import FormRunnerBaseOps.*

  val LanguageParam                     = "fr-language"
  //@XPathFunction
  val FormVersionParam                  = "form-version"
  //@XPathFunction
  val AccessTokenParam                  = "fr-access-token"
  //@XPathFunction
  val InternalAuthorizedOperationsParam = "fr-internal-authorized-operations"
  //@XPathFunction
  val InternalAdminTokenParam           = "fr-internal-admin-token"
  //@XPathFunction
  val InternalWorkflowStageParam        = "fr-internal-workflow-stage"
  //@XPathFunction
  val InternalCreatedParam              = "fr-internal-created"
  //@XPathFunction
  val InternalLastModifiedParam         = "fr-internal-last-modified"
  //@XPathFunction
  val InternalETagParam                 = "fr-internal-etag"

  val LiferayLanguageHeader  = "orbeon-liferay-language"

  val DefaultIterationSuffix = "-iteration"
  val TemplateContentSuffix  = "-content"
  val TemplateSuffix         = "-template"

  // This logs configuration issues once only per server start
  private lazy val configCheckResult = {
    val tokens = configCheck().toList
    if (tokens.nonEmpty) {

      val lines =
        tokens.map {
          case ("password.general"         , _) => "The password for the `oxf.crypto.password` property is missing or not strong enough."
          case ("password.token"           , _) => "The password for the `oxf.fr.access-token.password` property is missing or not strong enough (optional)."
          case ("password.field-encryption", _) => "The password for the `oxf.fr.field-encryption.password` property is missing or not strong enough (optional)."
          case ("database.configuration"   , _) => "The database configuration is missing or incomplete."
          case (other                      , _) => s"The property `$other` is not defined."
        }

      val message =
        s"""The following Orbeon Forms configurations are incomplete:\n
           |${lines.map(l => s"- $l\n").mkString}
           |Please visit this page for more: https://doc.orbeon.com/installation/configuration-banner
           |""".stripMargin

      val hasErrorLevel =
        tokens.exists {
          case (_, log4s.Error) => true
          case _                => false
        }

      if (hasErrorLevel)
        CoreCrossPlatformSupport.logger.error(message)
      else
        CoreCrossPlatformSupport.logger.info(message)
    }
    tokens.map(_._1)
  }

  //@XPathFunction
  def configCheckJava(): SequenceIterator = configCheckResult

  // Get an id based on a name
  // NOTE: The idea as of 2011-06-21 is that we support reading indiscriminately the -control, -grid
  // suffixes, whatever type of actual control they apply to. The idea is that in the end we might decide to just use
  // -control. OTOH we must have distinct ids for binds, controls and templates, so the -bind, -control and -template
  // suffixes must remain.
  //@XPathFunction
  def bindId    (controlName: String): String = controlName + "-bind"
  def gridId    (gridName: String)   : String = gridName    + "-grid"
  def sectionId (sectionName: String): String = sectionName + "-section"
  def controlId (controlName: String): String = controlName + "-control"
  //@XPathFunction
  def templateId(gridName: String)   : String = gridName    + TemplateSuffix

  def defaultIterationName(repeatName: String): String =
    repeatName + DefaultIterationSuffix

  // Find a view element by id, using the index if possible, otherwise traversing the document
  // NOTE: Searching by traversing if no index should be done directly in the selectID implementation.
  def findInViewTryIndex(staticId: String)(implicit ctx: FormRunnerDocContext): Option[NodeInfo] =
    ctx.bodyElemOpt.flatMap(findTryIndex(staticId, _, includeSelf = false))

  def findInBindsTryIndex(id: String)(implicit ctx: FormRunnerDocContext): Option[NodeInfo] =
    findTryIndex(id, ctx.topLevelBindElem.get, includeSelf = true)

  // NOTE: This is a rather crude way of testing the presence of the index! But we do know for now that this is
  // only called from the functions above, which search in a form's view, model, or binds, which implies the
  // existence of a form model.
  def formDefinitionHasIndex(inDoc: NodeInfo): Boolean =
    SaxonUtils.selectID(inDoc, FormModel).isDefined

  def isUnder(node: NodeInfo, under: NodeInfo, includeSelf: Boolean): Boolean =
    if (includeSelf)
      node ancestorOrSelf * contains under
    else
      node ancestor * contains under

  def findTryIndex(id: String, under: NodeInfo, includeSelf: Boolean): Option[NodeInfo] = {

    val hasIndex = formDefinitionHasIndex(under)

    def fromSearch =
      if (includeSelf)
        under descendantOrSelf * find (_.id == id)
      else
        under descendant * find (_.id == id)

    def fromIndex =
      SaxonUtils.selectID(under, id) match {
        case elemOpt @ Some(elem) if isUnder(elem, under, includeSelf) => elemOpt
        case Some(_)                                                   => fromSearch
        case None                                                      => None
      }

    if (hasIndex)
      fromIndex
    else
      fromSearch
  }

  // Get the body element assuming the structure of an XHTML document, annotated or not, OR the structure of `xbl:xbl`.
  // NOTE: `annotate.xpl` replaces `fr:body` with `xf:group[@class = 'fb-body']`.
  def getFormRunnerBodyElem(inDoc: NodeInfo): NodeInfo =
    findFormRunnerBodyElem(inDoc).get

  private val FRGridOrLegacyRepeatTest: Test = FRGridTest || FRRepeatTest

  def findFormRunnerBodyElem(inDoc: NodeInfo): Option[NodeInfo] = {

    def parentIsNotGridOrLegacyRepeat(n: NodeInfo) =
      (n parent FRGridOrLegacyRepeatTest).isEmpty

    def fromFbGroupId = SaxonUtils.selectID(inDoc, "fb-body")
    def fromGroup     = inDoc.rootElement / "*:body" descendant XFGroupTest find (_.id == "fb-body")
    def fromFRBody    = inDoc.rootElement / "*:body" descendant FRBodyTest  find parentIsNotGridOrLegacyRepeat
    def fromTemplate  = inDoc.rootElement firstChildOpt XBLTemplateTest

    fromFbGroupId orElse fromGroup orElse fromFRBody orElse fromTemplate
  }

  // Get the form model
  def getModelElem(inDoc: NodeInfo): NodeInfo =
    findModelElem(inDoc).head

  def findModelElem(inDoc: NodeInfo): Option[NodeInfo] = {

    def fromHead           = inDoc.rootElement / "*:head" / XFModelTest find (_.hasIdValue(FormModel))
    def fromImplementation = (inDoc.rootElement / XBLImplementationTest / XFModelTest).headOption

    fromHead orElse fromImplementation
  }

  // Find an xf:instance element
  def instanceElem(inDoc: NodeInfo, id: String): Option[NodeInfo] =
    findModelElem(inDoc) flatMap (instanceElemFromModelElem(_, id))

  def instanceElemFromModelElem(modelElem: NodeInfo, id: String): Option[NodeInfo] =
    modelElem / XFInstanceTest find (_.hasIdValue(id))

  // Find an inline instance's root element
  def inlineInstanceRootElem(inDoc: NodeInfo, id: String): Option[NodeInfo] =
    (instanceElem(inDoc, id).toList / *).headOption

  def isTemplateId(id: String): Boolean = id endsWith TemplateSuffix

  // Find all template instances
  def templateInstanceElements(inDoc: NodeInfo): NodeColl =
    getModelElem(inDoc) / XFInstanceTest filter (e => isTemplateId(e.id))

  // Get the root element of instances
  //@XPathFunction
  def formInstanceRoot(inDoc: NodeInfo): NodeInfo = inlineInstanceRootElem(inDoc, FormInstance).get

  //@XPathFunction
  def metadataInstanceRootOpt(inDoc: NodeInfo): Option[NodeInfo] = inlineInstanceRootElem(inDoc, MetadataInstance)

  //@XPathFunction
  def resourcesInstanceRootOpt(inDoc: NodeInfo): Option[NodeInfo] = inlineInstanceRootElem(inDoc, FormResources)

  private val TopLevelBindIds = Set(Names.FormBinds, "fb-form-binds")

  // Find the top-level binds (marked with "fr-form-binds" or "fb-form-binds"), if any
  def findTopLevelBind(inDoc: NodeInfo): Option[NodeInfo] =
    findTopLevelBindFromModelElem(getModelElem(inDoc))

  def findTopLevelBindFromModelElem(modelElem: NodeInfo): Option[NodeInfo] =
    modelElem / XFBindTest find {
      // There should be an id, but for backward compatibility also support ref/nodeset pointing to fr-form-instance
      bind => TopLevelBindIds(bind.id) || frc.bindRefOpt(bind).contains("instance('fr-form-instance')")
    }

  def buildPropertyName(name: String)(implicit p: FormRunnerParams): String =
    if (p.appForm.isSpecificAppForm)
      buildPropertyName(name, AppForm(p.app, p.form))
    else
      name

  def buildPropertyName(name: String, appForm: AppForm): String =
    name :: appForm.app :: appForm.form :: Nil mkString "."

  //@XPathFunction
  def xpathFormRunnerStringProperty(name: String): Option[String] =
    formRunnerProperty(name)(FormRunnerParams())

  // Return a property using the form's app/name, None if the property is not defined
  def formRunnerProperty(name: String)(implicit p: FormRunnerParams): Option[String] =
    CoreCrossPlatformSupport.properties.getObjectOpt(buildPropertyName(name)) map (_.toString)

  def formRunnerProperty(name: String, appForm: AppForm): Option[String] =
    CoreCrossPlatformSupport.properties.getObjectOpt(buildPropertyName(name, appForm)) map (_.toString)

  def formRunnerRawProperty(name: String, appForm: AppForm): Option[Property] =
    CoreCrossPlatformSupport.properties.getPropertyOpt(buildPropertyName(name, appForm))

  def formRunnerRawProperty(name: String)(implicit p: FormRunnerParams): Option[Property] =
    CoreCrossPlatformSupport.properties.getPropertyOpt(buildPropertyName(name))

  def formRunnerPropertyWithNs(name: String)(implicit p: FormRunnerParams): Option[(String, NamespaceMapping)] =
    CoreCrossPlatformSupport.properties.getPropertyOpt(buildPropertyName(name)) map { p => (p.stringValue, p.namespaceMapping) }

  def intFormRunnerProperty(name: String)(implicit p: FormRunnerParams): Option[Int] =
    CoreCrossPlatformSupport.properties.getIntOpt(buildPropertyName(name))

  // Return a boolean property using the form's app/name, false if the property is not defined
  def booleanFormRunnerProperty(name: String)(implicit p: FormRunnerParams): Boolean =
    CoreCrossPlatformSupport.properties.getObjectOpt(buildPropertyName(name)) map (_.toString) contains "true"

  // We expect that the app/form are the last two parts of the property
  def trailingAppFormFromProperty(requestedName: String, property: Property): AppForm =
    property.name.substringAfter(requestedName).splitTo[List](".") match {
      case List(app, form) => AppForm(app, form)
      case _               => throw new IllegalArgumentException(s"cannot match property name `$requestedName` with `${property.name}`")
    }

  // Interrupt current processing and send an error code to the client.
  // NOTE: This could be done through ExternalContext
  //@XPathFunction
  def sendError(code: Int) = throw HttpStatusCodeException(code)
  def sendError(code: Int, resource: String) = throw HttpStatusCodeException(code, Option(resource))

  def formModelOpt: Option[XFormsModel] = topLevelModel(FormModel)

  // Return specific Form Runner instances
  def formInstance                : XFormsInstance         = topLevelInstance(FormModel,        FormInstance)                 get
  def metadataInstance            : Option[XFormsInstance] = topLevelInstance(FormModel,        MetadataInstance)
  def urlsInstance                : Option[XFormsInstance] = topLevelInstance(PersistenceModel, "fr-urls-instance")
  def formAttachmentsInstance     : Option[XFormsInstance] = topLevelInstance(FormModel,        "fr-form-attachments")

  def parametersInstance          : Option[XFormsInstance] = topLevelInstance(ParametersModel,   "fr-parameters-instance")
  def errorSummaryInstance        : XFormsInstance         = topLevelInstance(ErrorSummaryModel, "fr-error-summary-instance") get
  def persistenceInstance         : XFormsInstance         = topLevelInstance(PersistenceModel,  "fr-persistence-instance")   get

  def getMode: String =
    (parametersInstance.get.rootElement / "mode").stringValue

  def updateMode(newMode: String): Unit = {
    val modeElement = parametersInstance.get.rootElement / "mode"
    setvalue(modeElement, newMode)
  }

  private def authorizedOperationsInstance: XFormsInstance         = topLevelInstance(PersistenceModel,  "fr-authorized-operations")  get
  private def documentMetadataInstance    : XFormsInstance         = topLevelInstance(PersistenceModel,  "fr-document-metadata")      get

  // See also FormRunnerHome
  private val CreateOps = Set("*", "create")
  private val ReadOps   = Set("*", "read")
  private val UpdateOps = Set("*", "update")
  private val DeleteOps = Set("*", "delete")
  private val ListOps   = Set("*", "list")

  def authorizedOperations: Set[String] = authorizedOperationsInstance.rootElement.stringValue.splitTo[Set]()

  // Used by user XPath functions
  def canCreate: Boolean = (authorizedOperations intersect CreateOps).nonEmpty
  def canRead  : Boolean = (authorizedOperations intersect ReadOps)  .nonEmpty
  def canUpdate: Boolean = (authorizedOperations intersect UpdateOps).nonEmpty
  def canDelete: Boolean = (authorizedOperations intersect DeleteOps).nonEmpty
  def canList  : Boolean = (authorizedOperations intersect ListOps).nonEmpty

  private def documentMetadataStringOpt(header: String): Option[String] =
    documentMetadataInstance.rootElement.attValueOpt(header.toLowerCase).flatMap(_.trimAllToOpt)

  private def documentMetadataDateToInstant(name: String): Option[java.time.Instant] =
    documentMetadataStringOpt(name).flatMap(DateUtils.tryParseRFC1123ToInstant) // xxx check > 0L? how?

  def documentCreatedDateAsInstant : Option[java.time.Instant] = documentMetadataDateToInstant(Headers.Created)
  def documentModifiedDateAsInstant: Option[java.time.Instant] = documentMetadataDateToInstant(Headers.LastModified)
  def documentEtag                 : Option[String]            = documentMetadataStringOpt(Headers.ETag)

  private def documentMetadataWorkflowStageAtt: NodeInfo =
    documentMetadataInstance.rootElement.att(Names.WorkflowStage).head

  def documentWorkflowStage: Option[String] = documentMetadataWorkflowStageAtt.stringValue.trimAllToOpt
  def documentWorkflowStage_=(workflowStage: Option[String]): Unit = XFormsAPI.setvalue(documentMetadataWorkflowStageAtt, workflowStage.getOrElse(""))

  private val NewOrEditModes = Set("new", "edit")
  def isNewOrEditMode(mode: String): Boolean = NewOrEditModes(mode)
  //@XPathFunction
  def isNewOrEditMode: Boolean = isNewOrEditMode(FormRunnerParams().mode)

  def optionFromMetadataOrProperties(
    metadataInstanceRootElemOpt: Option[NodeInfo],
    featureName                : String
  )(implicit
    formRunnerParams           : FormRunnerParams
  ): Option[String] =
    metadataInstanceRootElemOpt.flatMap(_.elemValueOpt(featureName))
      .orElse(formRunnerProperty(s"oxf.fr.detail.$featureName"))

  //@XPathFunction
  def optionFromMetadataOrPropertiesDynamicXPath(
    featureName: String,
    default    : String
  ): String = {
    val xfcd = inScopeContainingDocument
    implicit val indentedLogger: IndentedLogger = xfcd.getIndentedLogger("form-runner")
    FRComponentParamSupport.fromMetadataAndPropertiesEvaluateAvt(
      xfcd,
      featureName,
      s"oxf.fr.detail.$featureName"
    ).getOrElse(default)
  }

  def formTitleFromMetadata: Option[String] =
    metadataInstance  map
      (_.rootElement) flatMap
      (formTitleFromMetadataElem(_, frc.currentLang))

  //@XPathFunction
  def formTitleFromMetadataElem(metadataElem: NodeInfo, requestedLang: String): Option[String] =
    metadataElem.elemWithLangOpt("title", requestedLang) orElse
      metadataElem.firstChildOpt("title")                map
      (_.stringValue)

  // TODO: unify with above
  def formDescriptionFromMetadataElem(metadataElem: NodeInfo, requestedLang: String): Option[String] =
    metadataElem.elemWithLangOpt("description", requestedLang) orElse
      metadataElem.firstChildOpt("description")                map
      (_.stringValue)

  // Captcha support
  // 2024-08-14: I don't see any XPath uses.
  //@XPathFunction
  def captchaPassed: Boolean = persistenceInstance.rootElement / "captcha" === "true"

  // 2024-08-14: I don't see any XPath uses.
  //@XPathFunction
  def showCaptcha: Boolean = isNewOrEditMode(FormRunnerParams().mode) && ! captchaPassed

  //@XPathFunction
  def captchaComponent(app: String, form: String): Array[String] = {
    val logger                   = Loggers.logger // TODO: Form Runner logger?
    val captchaPropertyPrefix    = "oxf.fr.detail.captcha"
    val captchaPropertyShortName = captchaPropertyPrefix                :: app :: form :: Nil mkString "."
    val captchaPropertyLongName  = captchaPropertyPrefix :: "component" :: app :: form :: Nil mkString "."
    def property(name: String)   = CoreCrossPlatformSupport.properties.getPropertyOpt(name)
    val captchaPropertyOpt       = property(captchaPropertyLongName) orElse
                                   property(captchaPropertyShortName)
    captchaPropertyOpt match {
      case None => Array.empty
      case Some(captchaProperty) =>
        val propertyValue = captchaProperty.value.asInstanceOf[String]
        propertyValue match {
          case ""                                   => Array.empty
          case "reCAPTCHA"                          => Array(XMLNames.FR, "fr:recaptcha")          // token for backward compatibility only
          case "SimpleCaptcha" | "OnPremiseCaptcha" => Array(XMLNames.FR, "fr:on-premise-captcha") // same as above
          case captchaName  =>
            captchaName.splitTo[List](":") match {
              case List(prefix, _) =>
                captchaProperty.namespaces.get(prefix) match {
                  case Some(namespaceURI) =>
                    Array(namespaceURI, captchaName)
                  case None =>
                    logger.error(s"No namespace for captcha `$captchaName`")
                    Array.empty
                }
              case _ =>
                logger.error(s"Invalid reference to captcha component `$captchaName`")
                Array.empty
            }
        }
    }
  }

  def isDesignTime(implicit p: FormRunnerParams)  : Boolean = AppForm(p.app, p.form) == AppForm.FormBuilder
  def isReadonlyMode(implicit p: FormRunnerParams): Boolean = ModeType.ReadonlyModes(p.mode)

  // https://github.com/orbeon/orbeon-forms/issues/5323
  // https://github.com/orbeon/orbeon-forms/issues/5325
  // https://github.com/orbeon/orbeon-forms/issues/5390
  def isEmbedded(embeddingType: Option[String]): Boolean =
    embeddingType match {
      case Some(Headers.GeneralEmbeddedClient)        => false // don't support `embedded` as its meaning is unclear
      case Some(v) if Headers.EmbeddedClientValues(v) => inScopeContainingDocument.embeddingTypeFromHeaders.contains(v)
      case Some(_)                                    => false
      case None                                       => inScopeContainingDocument.isEmbeddedFromHeaderOrUrlParam
    }

  def isServicePath(path: String): Boolean =
    path.startsWith("/fr/service/")

  def isServicePath(implicit xfc: XFormsFunction.Context): Boolean =
    isServicePath(xfc.containingDocument.getRequestPath)

  // For now restrict to `new` and `edit` modes. Make sure, if changing, to except `validate` and `import`,
  // probably, as they also need to send an XML response back.
  def isBackground(implicit xfc: XFormsFunction.Context, p: FormRunnerParams): Boolean =
    isServicePath && (p.mode == "new" || p.mode == "edit")

  // Display a success message
  // TODO: support `dialog` appearance, for symmetry with `error-message`
  //@XPathFunction
  def successMessage(message: String): Unit = {
    setvalue(persistenceInstance.rootElement / "message", message)
    toggle("fr-message-success")
  }

  // Display an error message
  //@XPathFunction
  def errorMessage(message: String): Unit = errorMessage(message, MessageAppearance.Dialog)
  def errorMessage(message: String, appearance: MessageAppearance): Unit =
    appearance match {
      case MessageAppearance.Dialog =>
        dispatch(
          name       = "fr-show",
          targetId   = "fr-error-dialog",
          properties = Map("message" -> Some(message), "message-is-html" -> Some("true"))
        )
      case MessageAppearance.Ephemeral =>
        setvalue(persistenceInstance.rootElement / "message", message)
        toggle("fr-message-error")
    }

  def formRunnerStandaloneBaseUrl(propertySet: PropertySet, req: ExternalContext.Request): String = {

    def baseUrlFromProperty: Option[String] =
      propertySet.getNonBlankString("oxf.fr.external-base-url")

    def baseUrlFromContext: String =
      XFormsCrossPlatformSupport.rewriteURL(
        req,
        "/",
        UrlRewriteMode.Absolute
      )

    (baseUrlFromProperty getOrElse baseUrlFromContext).appendSlash
  }

  //@XPathFunction
  def removeHtmlTags(html: String): String =
    HtmlParsing.sanitizeHtmlString(
      value           = html,
      extraElemFilter = _ => ElemFilter.Remove
    )

  //@XPathFunction
  def sanitizeHtml(html: String): String =
    HtmlParsing.sanitizeHtmlString(html)

  //@XPathFunction
  def processRequest(id: String, path: String, argsString: String): Unit =
    Router.processRequest(id, path, argsString)
}

object FormRunnerBaseOps {

  sealed trait MessageAppearance extends EnumEntry with Lowercase
  object MessageAppearance extends Enum[MessageAppearance] {
    val values: immutable.IndexedSeq[MessageAppearance] = findValues

    case object Dialog    extends MessageAppearance
    case object Ephemeral extends MessageAppearance
  }
}