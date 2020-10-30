package org.orbeon.oxf.xforms.submission

import org.orbeon.dom.QName
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPathCache.XPathContext
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.event.events.{ErrorType, XFormsSubmitErrorEvent}
import org.orbeon.oxf.xforms.submission.SubmissionUtils._
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.{CrossPlatformSupport, RelevanceHandling, UrlType}


// Subset of `SubmissionParameters`
case class TwoPassSubmissionParameters(
  submissionEffectiveId  : String,
  showProgress           : Boolean,
  target                 : Option[String],
  isResponseResourceType : Boolean
)

object TwoPassSubmissionParameters {

  def apply(submissionEffectiveId: String, p: SubmissionParameters): TwoPassSubmissionParameters =
    TwoPassSubmissionParameters(
      submissionEffectiveId  = submissionEffectiveId,
      showProgress           = p.xxfShowProgress,
      target                 = p.xxfTargetOpt,
      isResponseResourceType = p.resolvedIsResponseResourceType
    )
}

case class SubmissionParameters(
  refContext                     : RefContext,
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
  isDeferredSubmissionFirstPass  : Boolean,
  isDeferredSubmissionSecondPass : Boolean,
  urlNorewrite                   : Boolean,
  urlType                        : UrlType,
  resolvedIsResponseResourceType : Boolean,
  xxfTargetOpt                   : Option[String],
  xxfShowProgress                : Boolean
)

object SubmissionParameters {

  def withUpdatedRefContext(
    p                 : SubmissionParameters)(implicit
    dynamicSubmission : XFormsModelSubmission
  ): SubmissionParameters =
    p.copy(refContext = createRefContext(dynamicSubmission))

  def apply(
    eventNameOrNull   : String)(implicit
    dynamicSubmission : XFormsModelSubmission
  ): SubmissionParameters = {

    val staticSubmission = dynamicSubmission.staticSubmission

    implicit val containingDocument = dynamicSubmission.containingDocument
    implicit val refContext         = createRefContext(dynamicSubmission)

    // Check that we have a current node and that it is pointing to a document or an element
    if (refContext.refNodeInfo eq null)
      throw new XFormsSubmissionException(
        submission       = dynamicSubmission,
        message          = s"Empty single-node binding on xf:submission for submission id: `${dynamicSubmission.getId}`",
        description      = "getting submission single-node binding",
        submitErrorEvent = new XFormsSubmitErrorEvent(
          dynamicSubmission,
          ErrorType.NoData,
          None
        )
      )

    if (! refContext.refNodeInfo.isDocument && ! refContext.refNodeInfo.isElement)
      throw new XFormsSubmissionException(
        submission       = dynamicSubmission,
        message          = "xf:submission: single-node binding must refer to a document node or an element.",
        description      = "getting submission single-node binding",
        submitErrorEvent = new XFormsSubmitErrorEvent(
          dynamicSubmission,
          ErrorType.NoData,
          None
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
          staticSubmission.namespaceMapping.mapping,
          resolvedMethodQName,
          unprefixedIsNoNamespace = true
        ) getOrElse
          QName("get")

      methodQName.clarkName
    }

    // TODO: We pass a Clark name, but we don't process this correctly!
    val actualHttpMethod     = actualHttpMethodFromXFormsMethodName(resolvedMethodClarkName)
    val resolvedMediatypeOpt = staticSubmission.avtMediatypeOpt flatMap stringAvtTrimmedOpt

    val serializationOpt = staticSubmission.avtSerializationOpt flatMap stringAvtTrimmedOpt

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
              staticSubmission.namespaceMapping.mapping,
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
        staticSubmission.element.jElements(XFORMS_HEADER_QNAME).size == 0               // can't optimize if there are headers specified

    // TODO: use static for headers
    // In "Ajax portlet" mode, there is no deferred submission process
    // Also don't allow deferred submissions when the incoming method is a GET. This is an indirect way of
    // allowing things like using the XForms engine to generate a PDF with an HTTP GET.
    // NOTE: Method can be `null` e.g. in a portlet render request.
    val incomingMethod = CrossPlatformSupport.externalContext.getRequest.getMethod

    val isAllowDeferredSubmission      = incomingMethod != HttpMethod.GET
    val isPossibleDeferredSubmission   = resolvedReplace == ReplaceType.All && ! isHandlingClientGetAll && ! containingDocument.initializing
    val isDeferredSubmission           = isAllowDeferredSubmission && isPossibleDeferredSubmission
    val isDeferredSubmissionFirstPass  = isDeferredSubmission && XFormsEvents.XFORMS_SUBMIT == eventNameOrNull
    val isDeferredSubmissionSecondPass = isDeferredSubmission && ! isDeferredSubmissionFirstPass // XXFORMS_SUBMIT

    SubmissionParameters(
      refContext                     = refContext,
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
      isDeferredSubmissionFirstPass  = isDeferredSubmissionFirstPass,
      isDeferredSubmissionSecondPass = isDeferredSubmissionSecondPass,
      urlNorewrite                   = resolvedUrlNorewrite,
      urlType                        = resolvedUrlType,
      resolvedIsResponseResourceType = resolvedIsResponseResourceType,
      xxfTargetOpt                   = resolvedXxfTargetOpt,
      xxfShowProgress                = resolvedXxfShowProgress
    )
  }

  def actualHttpMethodFromXFormsMethodName(
    methodName        : String)(implicit
    dynamicSubmission : XFormsModelSubmission
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
            dynamicSubmission,
            ErrorType.XXFormsMethodError,
            None
          )
        )
    }

  private def createRefContext(dynamicSubmission: XFormsModelSubmission): RefContext = {

    val staticSubmission   = dynamicSubmission.staticSubmission
    val containingDocument = dynamicSubmission.containingDocument

    val model = dynamicSubmission.model
    model.resetAndEvaluateVariables()

    val contextStack = model.getContextStack
    contextStack.pushBinding(staticSubmission.element, dynamicSubmission.getEffectiveId, model.getResolutionScope)

    val bindingContext = contextStack.getCurrentBindingContext

    RefContext(
      refNodeInfo                  = bindingContext.getSingleItemOrNull.asInstanceOf[NodeInfo],
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
}