package org.orbeon.oxf.xforms.submission

import org.orbeon.dom.QName
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPathCache.XPathContext
import org.orbeon.oxf.util.{ContentTypes, NetUtils}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.event.events.{ErrorType, XFormsSubmitErrorEvent}
import org.orbeon.oxf.xforms.submission.RelevanceHandling.{Keep, Remove}
import org.orbeon.oxf.xforms.submission.SubmissionUtils._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

import scala.collection.JavaConverters._

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
          null
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
          null
        )
      )

    def filterQualifiedName(s: String) =
      Dom4jUtils.extractTextValueQName(staticSubmission.element, s, true).localName

    val resolvedReplace =
      staticSubmission.avtReplaceOpt    flatMap
        stringAvtTrimmedOpt             map
        filterQualifiedName             map // so that `xxf:binary` becomes `binary`
        ReplaceType.withNameInsensitive getOrElse
        ReplaceType.All

    val resolvedMethod = {
      val resolvedMethodQName =
        staticSubmission.avtMethod flatMap stringAvtTrimmedOpt getOrElse "get"

      Dom4jUtils.extractTextValueQName(
        staticSubmission.namespaceMapping.mapping.asJava,
        resolvedMethodQName,
        true
      ).clarkName
    }
    val actualHttpMethod     = actualHttpMethodFromXFormsMethodName(resolvedMethod)
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
          Keep
        else
          Remove
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
          stringAvtTrimmedOpt                 map (
            Dom4jUtils.extractTextValueQName(
              staticSubmission.namespaceMapping.mapping.asJava,
              _,
              true
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
        staticSubmission.element.elements(XFORMS_HEADER_QNAME).size == 0               // can't optimize if there are headers specified

    // TODO: use static for headers
    // In "Ajax portlet" mode, there is no deferred submission process
    // Also don't allow deferred submissions when the incoming method is a GET. This is an indirect way of
    // allowing things like using the XForms engine to generate a PDF with an HTTP GET.
    // NOTE: Method can be `null` e.g. in a portlet render request.
    val incomingMethod = NetUtils.getExternalContext.getRequest.getMethod

    val isAllowDeferredSubmission      = incomingMethod != HttpMethod.GET
    val isPossibleDeferredSubmission   = resolvedReplace == ReplaceType.All && ! isHandlingClientGetAll && ! containingDocument.isInitializing
    val isDeferredSubmission           = isAllowDeferredSubmission && isPossibleDeferredSubmission
    val isDeferredSubmissionFirstPass  = isDeferredSubmission && XFormsEvents.XFORMS_SUBMIT == eventNameOrNull
    val isDeferredSubmissionSecondPass = isDeferredSubmission && ! isDeferredSubmissionFirstPass // XXFORMS_SUBMIT

    SubmissionParameters(
      refContext                     = refContext,
      replaceType                    = resolvedReplace,
      xformsMethod                   = resolvedMethod,
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
            null
          )
        )
    }

  private def createRefContext(dynamicSubmission: XFormsModelSubmission): RefContext = {

    val staticSubmission   = dynamicSubmission.staticSubmission
    val containingDocument = dynamicSubmission.containingDocument

    val model = dynamicSubmission.getModel
    model.resetAndEvaluateVariables()

    val contextStack = model.getContextStack
    contextStack.pushBinding(staticSubmission.element, dynamicSubmission.getEffectiveId, model.getResolutionScope)

    val bindingContext = contextStack.getCurrentBindingContext

    RefContext(
      refNodeInfo                  = bindingContext.getSingleItem.asInstanceOf[NodeInfo],
      refInstanceOpt               = bindingContext.instance, // `None` if the document submitted is not part of an instance
      submissionElementContextItem = bindingContext.contextItem,
      xpathContext =
        new XPathContext(
          namespaceMapping   = staticSubmission.namespaceMapping,
          variableToValueMap = bindingContext.getInScopeVariables,
          functionLibrary    = containingDocument.getFunctionLibrary,
          functionContext    = model.getContextStack.getFunctionContext(dynamicSubmission.getEffectiveId),
          baseURI            = null,
          locationData       = staticSubmission.locationData
        )
    )
  }
}