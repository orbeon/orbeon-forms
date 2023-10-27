package org.orbeon.oxf.xforms.submission

import org.orbeon.dom.QName
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.http.{BasicCredentials, HttpMethod}
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.util.XPathCache.XPathContext
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEvent.{ActionPropertyGetter, TunnelProperties}
import org.orbeon.oxf.xforms.event.events.{ErrorType, XFormsSubmitErrorEvent}
import org.orbeon.oxf.xforms.submission.SubmissionUtils._
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.oxf.xml.dom.Extensions.DomElemOps
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xforms.XFormsNames.XFORMS_HEADER_QNAME
import org.orbeon.xforms.{RelevanceHandling, UrlType, XFormsCrossPlatformSupport}


case class TwoPassSubmissionParameters(
  submissionEffectiveId: String,
  submissionParameters : SubmissionParameters
)

case class SubmissionParameters(

  // TODO: categorize?
  replaceType                    : ReplaceType,
  xformsMethod                   : String,
  httpMethod                     : HttpMethod,
  mediatypeOpt                   : Option[String],
  serializationOpt               : Option[String],
  serialize                      : Boolean,
  validate                       : Boolean,
  relevanceHandling              : RelevanceHandling,
  xxfCalculate                   : Boolean,
  xxfUploads                     : Boolean,
  xxfRelevantAttOpt              : Option[QName],
  xxfAnnotate                    : Set[String],
  isHandlingClientGetAll         : Boolean,
  isDeferredSubmission           : Boolean,
  urlNorewrite                   : Boolean,
  urlType                        : UrlType,
  resolvedIsResponseResourceType : Boolean,
  xxfTargetOpt                   : Option[String],
  xxfShowProgress                : Boolean,
  actionProperties               : Option[ActionPropertyGetter],

  actionOrResource               : String,
  isAsynchronous                 : Boolean,
  responseMustAwait              : Boolean,
  credentialsOpt                 : Option[BasicCredentials],

  // Serialization
  separator                      : String,
  encoding                       : String,

  // XML serialization
  versionOpt                     : Option[String],
  indent                         : Boolean,
  omitXmlDeclaration             : Boolean,
  standaloneOpt                  : Option[Boolean],

  // Response
  isHandleXInclude               : Boolean,
  isReadonly                     : Boolean,
  applyDefaults                  : Boolean,
  isCache                        : Boolean,
  timeToLive                     : Long
) {
  def tunnelProperties: Option[TunnelProperties] =
    actionProperties.map(_.tunnelProperties)
}

object SubmissionParameters {

  private val DefaultSeparator = "&" // XForms 1.1 changes back the default to `&` as of February 2009
  private val DefaultEncoding  = CharsetNames.Utf8
  private val Asynchronous     = "asynchronous"
  private val Application      = "application"
  private val CacheableMethods = Set(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT): Set[HttpMethod]

  val EventName = XFormsEvent.xxfName("submission-parameters")

  def apply(
    dynamicSubmission: XFormsModelSubmission,
    actionProperties : Option[ActionPropertyGetter]
  )(implicit
    refContext: RefContext
  ): SubmissionParameters = {

    val staticSubmission = dynamicSubmission.staticSubmission

    implicit val containingDocument: XFormsContainingDocument = dynamicSubmission.containingDocument

    if (refContext.refNodeInfo eq null)
      throw new XFormsSubmissionException(
        submission       = dynamicSubmission,
        message          = s"Empty single-node binding on xf:submission for submission id: `${dynamicSubmission.getId}`",
        description      = "getting submission single-node binding",
        submitErrorEvent = new XFormsSubmitErrorEvent(
          target           = dynamicSubmission,
          errorType        = ErrorType.NoData,
          cxrOpt           = None,
          tunnelProperties = actionProperties.map(_.tunnelProperties)
        )
      )

    def filterQualifiedName(s: String) =
      staticSubmission.element.resolveStringQName(s, unprefixedIsNoNamespace = true) map (_.localName)

    val resolvedReplace =
      staticSubmission.avtReplaceOpt    flatMap
        stringAvtTrimmedOpt             flatMap
        filterQualifiedName             map // so that `xxf:binary` becomes `binary`
        ReplaceType.withNameInsensitive getOrElse
        ReplaceType.All

    val resolvedMethodClarkName = {
      val resolvedMethodQName =
        staticSubmission.avtMethod flatMap stringAvtTrimmedOpt getOrElse "get"

      val methodQName =
        Extensions.resolveQName(
          staticSubmission.namespaceMapping.mapping.get,
          resolvedMethodQName,
          unprefixedIsNoNamespace = true
        ) getOrElse
          QName("get")

      methodQName.clarkName
    }

    // TODO: We pass a Clark name, but we don't process this correctly!
    val actualHttpMethod     = actualHttpMethodFromXFormsMethodName(dynamicSubmission, resolvedMethodClarkName, actionProperties)
    val resolvedMediatypeOpt = staticSubmission.avtMediatypeOpt flatMap stringAvtTrimmedOpt

    val serializationOpt = staticSubmission.avtSerializationOpt flatMap stringAvtTrimmedOpt

    // For a binary serialization, we allow pointing to an attribute (and other nodes); otherwise we must point to a
    // document or element.
    if (! (serializationOpt.contains(ContentTypes.OctetStreamContentType) || refContext.refNodeInfo.isDocument || refContext.refNodeInfo.isElement))
      throw new XFormsSubmissionException(
        submission       = dynamicSubmission,
        message          = "xf:submission: single-node binding must refer to a document node or an element.",
        description      = "getting submission single-node binding",
        submitErrorEvent = new XFormsSubmitErrorEvent(
          target           = dynamicSubmission,
          errorType        = ErrorType.NoData,
          cxrOpt           = None,
          tunnelProperties = actionProperties.map(_.tunnelProperties)
        )
      )

    val serialize = serializationOpt match {
      case Some(serialization) => serialization != "none"
      case None                => ! (staticSubmission.serializeOpt contains false.toString) // backward compatibility
    }

    val resolvedValidateStringOpt = staticSubmission.avtValidateOpt flatMap booleanAvtOpt

    // "The default value is `false` if the value of serialization is "none" and "true" otherwise"
    val resolvedValidate = serialize && ! (resolvedValidateStringOpt contains false)

    def withNameAdjustForTrueAndFalse(name: String): RelevanceHandling =
      RelevanceHandling.withNameLowercaseOnlyOption(name) getOrElse {
        if (name == "false")
          RelevanceHandling.Keep
        else
          RelevanceHandling.Remove
      }

    // Use `nonrelevant` first and then `relevant` for backward compatibility
    val resolvedRelevanceHandling =
      staticSubmission.avtNonRelevantOpt              flatMap
        stringAvtTrimmedOpt                           flatMap
        RelevanceHandling.withNameLowercaseOnlyOption getOrElse
        withNameAdjustForTrueAndFalse(
          staticSubmission.avtRelevantOpt             flatMap
            stringAvtTrimmedOpt                       getOrElse
            true.toString
        )

    val resolvedXxfCalculate =
      serialize && ! (staticSubmission.avtXxfCalculateOpt flatMap booleanAvtOpt contains false)

    val resolvedXxfUploads =
      serialize && ! (staticSubmission.avtXxfUploadsOpt flatMap booleanAvtOpt contains false)

    val resolvedXxfRelevantAtt: Option[QName] =
      if (serialize)
        staticSubmission.avtXxfRelevantAttOpt flatMap
          stringAvtTrimmedOpt                 flatMap (
            Extensions.resolveQName(
              staticSubmission.namespaceMapping.mapping.get,
              _,
              unprefixedIsNoNamespace = true
            )
          )
      else
        None

    val resolvedXxfAnnotate: Set[String] =
      if (serialize)
        staticSubmission.avtXxfAnnotateOpt flatMap
          stringAvtTrimmedOpt              map
          (_.tokenizeToSet)                getOrElse
          Set.empty
      else
        Set.empty

    val resolvedUrlNorewrite =
      staticSubmission.avtUrlNorewrite flatMap booleanAvtOpt getOrElse false

    val resolvedUrlType =
      staticSubmission.avtUrlType         flatMap
        stringAvtTrimmedOpt               flatMap
        UrlType.withNameInsensitiveOption getOrElse
        UrlType.Render

    val resolvedIsResponseResourceType =
      staticSubmission.avtResponseUrlType flatMap
        stringAvtTrimmedOpt               flatMap
        UrlType.withNameInsensitiveOption contains
        UrlType.Resource

    val resolvedXxfTargetOpt =
      staticSubmission.avtXxfTargetOpt flatMap
      stringAvtTrimmedOpt

    val resolvedXxfShowProgress =
      staticSubmission.avtXxfShowProgressOpt flatMap
      booleanAvtOpt                          getOrElse
      true

    val isHandlingClientGetAll =
      containingDocument.isOptimizeGetAllSubmission                                 &&
        actualHttpMethod == HttpMethod.GET                                          &&
        resolvedReplace == ReplaceType.All                                          &&
        (
          resolvedMediatypeOpt.isEmpty ||
          ! (resolvedMediatypeOpt exists (_.startsWith(ContentTypes.SoapContentType)))
        )                                                                           && // can't let SOAP requests be handled by the browser
        staticSubmission.avtXxfUsernameOpt.isEmpty                                  && // can't optimize if there are authentication credentials
        staticSubmission.avtXxfTargetOpt.isEmpty                                    && // can't optimize if there is a target
        staticSubmission.element.jElements(XFORMS_HEADER_QNAME).size == 0              // can't optimize if there are headers specified

    // TODO: use static for headers
    val incomingMethod: HttpMethod = XFormsCrossPlatformSupport.externalContext.getRequest.getMethod

    val isAllowDeferredSubmission      = incomingMethod != HttpMethod.GET
    val isPossibleDeferredSubmission   = resolvedReplace == ReplaceType.All && ! isHandlingClientGetAll && ! containingDocument.initializing
    val isDeferredSubmission           = isAllowDeferredSubmission && isPossibleDeferredSubmission

    // Maybe: See if we can resolve `xml:base` early to detect absolute URLs early as well.
    // `actionOrResource = resolveXMLBase(containingDocument, getSubmissionElement(), NetUtils.encodeHRRI(temp, true)).toString`
    val actionOrResource =
      stringAvtTrimmedOpt(staticSubmission.avtActionOrResource) match {
        case Some(resolved) =>
          resolved.encodeHRRI(processSpace = true)
        case None =>
          throw new XFormsSubmissionException(
            submission  = dynamicSubmission,
            message     = s"xf:submission: mandatory `resource` or `action` evaluated to an empty sequence for attribute value: `${staticSubmission.avtActionOrResource}`",
            description = "resolving resource URI"
          )
      }

    val credentialsOpt =
      staticSubmission.avtXxfUsernameOpt flatMap
        stringAvtTrimmedOpt              map { username =>

        BasicCredentials(
          username       = username,
          password       =    staticSubmission.avtXxfPasswordOpt       flatMap stringAvtTrimmedOpt,
          preemptiveAuth = ! (staticSubmission.avtXxfPreemptiveAuthOpt flatMap stringAvtTrimmedOpt contains false.toString),
          domain         =    staticSubmission.avtXxfDomainOpt         flatMap stringAvtTrimmedOpt
        )
      }

    val isReadonly =
      staticSubmission.avtXxfReadonlyOpt flatMap booleanAvtOpt getOrElse false

    val isCache =
      staticSubmission.avtXxfCacheOpt flatMap booleanAvtOpt getOrElse {
        // For backward compatibility
        staticSubmission.avtXxfSharedOpt flatMap stringAvtTrimmedOpt contains Application
      }

    // Check read-only and cache hints
    if (isCache) {

      if (! CacheableMethods(actualHttpMethod))
        throw new XFormsSubmissionException(
          submission  = dynamicSubmission,
          message     = """xf:submission: `xxf:cache="true"` or `xxf:shared="application"` can be set only with `method="get|post|put"`.""",
          description = "checking read-only and shared hints"
        )

      if (resolvedReplace != ReplaceType.Instance)
        throw new XFormsSubmissionException(
          submission  = dynamicSubmission,
          message     = """xf:submission: `xxf:cache="true"` or `xxf:shared="application"` can be set only with `replace="instance"`.""",
          description = "checking read-only and shared hints"
        )

    } else if (isReadonly && resolvedReplace != ReplaceType.Instance)
      throw new XFormsSubmissionException(
        submission  = dynamicSubmission,
        message     = """xf:submission: `xxf:readonly="true"` can be `true` only with `replace="instance"`.""",
        description = "checking read-only and shared hints"
      )

    // NOTE: XForms 1.1 default to async, but we don't fully support async so we default to sync instead
    val isAsynchronous = {

      val isRequestedAsynchronousMode =
        staticSubmission.avtModeOpt flatMap stringAvtTrimmedOpt contains Asynchronous

      // For now we don't support `replace="all"`
      if (isRequestedAsynchronousMode && resolvedReplace == ReplaceType.All)
        throw new XFormsSubmissionException(
          submission  = dynamicSubmission,
          message     = """xf:submission: `mode="asynchronous"` cannot be `true` with `replace="all"`.""",
          description = "checking asynchronous mode"
        )

      resolvedReplace != ReplaceType.All && isRequestedAsynchronousMode
    }

    val responseMustAwait =
      staticSubmission.avtResponseMustAwaitOpt flatMap booleanAvtOpt getOrElse false

    SubmissionParameters(

      replaceType                    = resolvedReplace,
      xformsMethod                   = resolvedMethodClarkName,
      httpMethod                     = actualHttpMethod,
      mediatypeOpt                   = resolvedMediatypeOpt,
      serializationOpt               = serializationOpt,
      serialize                      = serialize,
      validate                       = resolvedValidate,
      relevanceHandling              = resolvedRelevanceHandling,
      xxfCalculate                   = resolvedXxfCalculate,
      xxfUploads                     = resolvedXxfUploads,
      xxfRelevantAttOpt              = resolvedXxfRelevantAtt,
      xxfAnnotate                    = resolvedXxfAnnotate,
      isHandlingClientGetAll         = isHandlingClientGetAll,
      isDeferredSubmission           = isDeferredSubmission,
      urlNorewrite                   = resolvedUrlNorewrite,
      urlType                        = resolvedUrlType,
      resolvedIsResponseResourceType = resolvedIsResponseResourceType,
      xxfTargetOpt                   = resolvedXxfTargetOpt,
      xxfShowProgress                = resolvedXxfShowProgress,
      actionProperties               = actionProperties,

      actionOrResource               = actionOrResource,
      isAsynchronous                 = isAsynchronous,
      responseMustAwait              = responseMustAwait,
      credentialsOpt                 = credentialsOpt,

      separator                      = staticSubmission.avtSeparatorOpt          flatMap stringAvtTrimmedOpt getOrElse DefaultSeparator,
      encoding                       = staticSubmission.avtEncodingOpt           flatMap stringAvtTrimmedOpt getOrElse DefaultEncoding,

      versionOpt                     = staticSubmission.avtVersionOpt            flatMap stringAvtTrimmedOpt,
      indent                         = staticSubmission.avtIndentOpt             flatMap booleanAvtOpt       getOrElse false,
      omitXmlDeclaration             = staticSubmission.avtOmitXmlDeclarationOpt flatMap booleanAvtOpt       getOrElse false,
      standaloneOpt                  = staticSubmission.avtStandalone            flatMap booleanAvtOpt,

      isHandleXInclude               = staticSubmission.avtXxfHandleXInclude     flatMap booleanAvtOpt       getOrElse false,
      isReadonly                     = isReadonly,
      applyDefaults                  = staticSubmission.avtXxfDefaultsOpt        flatMap booleanAvtOpt       getOrElse false,
      isCache                        = isCache,
      timeToLive                     = staticSubmission.timeToLive
    )
  }

  def createRefContext(dynamicSubmission: XFormsModelSubmission): RefContext = {

    val staticSubmission   = dynamicSubmission.staticSubmission
    val containingDocument = dynamicSubmission.containingDocument

    val model = dynamicSubmission.model
    model.resetAndEvaluateVariables()

    val contextStack = model.getContextStack
    contextStack.pushBinding(staticSubmission.element, dynamicSubmission.getEffectiveId, model.getResolutionScope)

    val bindingContext = contextStack.getCurrentBindingContext

    RefContext(
      refNodeInfo                  = bindingContext.getSingleItemOrNull.asInstanceOf[om.NodeInfo],
      refInstanceOpt               = bindingContext.instance, // `None` if the document submitted is not part of an instance
      submissionElementContextItem = bindingContext.contextItem,
      xpathContext =
        new XPathContext(
          namespaceMapping   = staticSubmission.namespaceMapping,
          variableToValueMap = bindingContext.getInScopeVariables,
          functionLibrary    = containingDocument.functionLibrary,
          functionContext    = model.getContextStack.getFunctionContext(dynamicSubmission.getEffectiveId),
          baseURI            = null,
          locationData       = staticSubmission.locationData
        )
    )
  }

  private def actualHttpMethodFromXFormsMethodName(
    dynamicSubmission: XFormsModelSubmission,
    methodName       : String,
    actionProperties : Option[ActionPropertyGetter]
  ): HttpMethod =
    HttpMethod.withNameInsensitiveOption(methodName) getOrElse {
      if (methodName.endsWith("-post"))
        HttpMethod.POST
      else
        throw new XFormsSubmissionException(
          submission       = dynamicSubmission,
          message          = s"Invalid method name: `$methodName`",
          description      = "getting submission method",
          submitErrorEvent = new XFormsSubmitErrorEvent(
            target           = dynamicSubmission,
            errorType        = ErrorType.XXFormsMethodError,
            cxrOpt           = None,
            tunnelProperties = actionProperties.map(_.tunnelProperties)
          )
        )
    }
}