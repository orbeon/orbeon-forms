package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPathCache.XPathContext
import org.orbeon.oxf.util.{ContentTypes, NetUtils}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.{DocumentInfo, Item, NodeInfo}

case class RefContext(
  refNodeInfo                  : NodeInfo,
  refInstanceOpt               : Option[XFormsInstance],
  submissionElementContextItem : Item,
  xpathContext                 : XPathContext
)

// TODO: use enum for resolvedReplace, actualHttpMethod
case class SubmissionParameters(
  refContext                     : RefContext,
  resolvedReplace                : String,
  isReplaceAll                   : Boolean,
  isReplaceInstance              : Boolean,
  isReplaceText                  : Boolean,
  isReplaceNone                  : Boolean,
  resolvedMethod                 : String,
  actualHttpMethod               : String,
  resolvedMediatypeOpt           : Option[String],
  serializationOpt               : Option[String],
  serialize                      : Boolean,
  resolvedValidate               : Boolean,
  resolvedRelevanceHandling      : RelevanceHandling,
  resolvedXxfCalculate           : Boolean,
  resolvedXxfUploads             : Boolean,
  resolvedXxfAnnotate            : Set[String],
  isHandlingClientGetAll         : Boolean,
  isNoscript                     : Boolean,
  isDeferredSubmission           : Boolean,
  isDeferredSubmissionFirstPass  : Boolean,
  isDeferredSubmissionSecondPass : Boolean
)

object SubmissionParameters {

  def withUpdatedRefContext(p: SubmissionParameters, dynamicSubmission: XFormsModelSubmission): SubmissionParameters =
    p.copy(refContext = createRefContext(dynamicSubmission))

  def apply(dynamicSubmission: XFormsModelSubmission, eventName: String): SubmissionParameters = {

    val staticSubmission   = dynamicSubmission.staticSubmission
    val containingDocument = dynamicSubmission.containingDocument

    val refContext = createRefContext(dynamicSubmission)

    // Result of `resolveAttributeValueTemplates` can be `None` if, e.g. you have an AVT like `resource="{()}"`!
    def stringAvtTrimmedOpt(value: String) =
      Option(
        XFormsUtils.resolveAttributeValueTemplates(
          containingDocument,
          refContext.xpathContext,
          refContext.refNodeInfo,
          value
        )
      ) flatMap (_.trimAllToOpt)

    def booleanAvtOpt(value: String) =
      stringAvtTrimmedOpt(value) map (_.toBoolean)

    // Check that we have a current node and that it is pointing to a document or an element
    if (refContext.refNodeInfo eq null)
      throw new XFormsSubmissionException(
        dynamicSubmission,
        s"Empty single-node binding on xf:submission for submission id: `${dynamicSubmission.getId}`",
        "getting submission single-node binding",
        new XFormsSubmitErrorEvent(
          dynamicSubmission,
          XFormsSubmitErrorEvent.NO_DATA,
          null
        )
      )

    if (! (refContext.refNodeInfo.isInstanceOf[DocumentInfo] || refContext.refNodeInfo.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE))
      throw new XFormsSubmissionException(
        dynamicSubmission,
        "xf:submission: single-node binding must refer to a document node or an element.",
        "getting submission single-node binding",
        new XFormsSubmitErrorEvent(
          dynamicSubmission,
          XFormsSubmitErrorEvent.NO_DATA,
          null
        )
      )

    val resolvedReplace = stringAvtTrimmedOpt(staticSubmission.avtReplace) getOrElse XFORMS_SUBMIT_REPLACE_ALL
    val isReplaceAll       = resolvedReplace == XFORMS_SUBMIT_REPLACE_ALL
    val isReplaceInstance  = resolvedReplace == XFORMS_SUBMIT_REPLACE_INSTANCE
    val isReplaceText      = resolvedReplace == XFORMS_SUBMIT_REPLACE_TEXT
    val isReplaceNone      = resolvedReplace == XFORMS_SUBMIT_REPLACE_NONE

    val resolvedMethod = {
      val resolvedMethodQName =
        staticSubmission.avtMethod flatMap stringAvtTrimmedOpt getOrElse "get"

      Dom4jUtils.qNameToExplodedQName(
        Dom4jUtils.extractTextValueQName(
          staticSubmission.namespaceMapping.mapping,
          resolvedMethodQName,
          true
        )
      )
    }
    val actualHttpMethod     = XFormsSubmissionUtils.getActualHttpMethod(resolvedMethod)
    val resolvedMediatypeOpt = staticSubmission.avtMediatypeOpt flatMap stringAvtTrimmedOpt

    val serializationOpt = staticSubmission.avtSerializationOpt flatMap stringAvtTrimmedOpt

    val serialize = serializationOpt match {
      case Some(serialization) ⇒ serialization != "none"
      case None                ⇒ ! (staticSubmission.serializeOpt contains false.toString) // backward compatibility
    }

    val resolvedValidateStringOpt = staticSubmission.avtValidateOpt flatMap booleanAvtOpt

    // "The default value is `false` if the value of serialization is "none" and "true" otherwise"
    val resolvedValidate = serialize && ! (resolvedValidateStringOpt contains false)

    val resolvedRelevanceHandling =
      RelevanceHandling.withNameAdjustForTrueAndFalse(staticSubmission.avtRelevantOpt flatMap stringAvtTrimmedOpt getOrElse true.toString)

    val resolvedXxfCalculate =
      serialize && ! (staticSubmission.avtXxfCalculateOpt flatMap booleanAvtOpt contains false)

    val resolvedXxfUploads =
      serialize && ! (staticSubmission.avtXxfUploadsOpt flatMap booleanAvtOpt contains false)

    val resolvedXxfAnnotate: Set[String] =
      if (serialize)
        staticSubmission.avtXxfAnnotateOpt flatMap stringAvtTrimmedOpt map stringToSet getOrElse Set.empty
      else
        Set.empty

    val isHandlingClientGetAll =
      containingDocument.isOptimizeGetAllSubmission                                 &&
        actualHttpMethod == "GET"                                                   &&
        isReplaceAll                                                                &&
        (
          resolvedMediatypeOpt.isEmpty ||
          ! (resolvedMediatypeOpt exists (_.startsWith(ContentTypes.SoapContentType)))
        )                                                                           && // can't let SOAP requests be handled by the browser
        staticSubmission.avtXxfUsernameOpt.isEmpty                                  && // can't optimize if there are authentication credentials
        staticSubmission.avtXxfTargetOpt.isEmpty                                    && // can't optimize if there is a target
        staticSubmission.element.elements(XFORMS_HEADER_QNAME).size == 0 // can't optimize if there are headers specified

    // TODO: use static for headers
    // In noscript mode, or in "Ajax portlet" mode, there is no deferred submission process
    // Also don't allow deferred submissions when the incoming method is a GET. This is an indirect way of
    // allowing things like using the XForms engine to generate a PDF with an HTTP GET.
    // NOTE: Method can be null e.g. in a portlet render request
    val incomingMethod   = NetUtils.getExternalContext.getRequest.getMethod

    val isNoscript                     = containingDocument.noscript
    val isAllowDeferredSubmission      = ! isNoscript && incomingMethod != "GET"
    val isPossibleDeferredSubmission   = isReplaceAll && ! isHandlingClientGetAll && ! containingDocument.isInitializing
    val isDeferredSubmission           = isAllowDeferredSubmission && isPossibleDeferredSubmission
    val isDeferredSubmissionFirstPass  = isDeferredSubmission && XFormsEvents.XFORMS_SUBMIT == eventName
    val isDeferredSubmissionSecondPass = isDeferredSubmission && ! isDeferredSubmissionFirstPass // here we get XXFORMS_SUBMIT

    SubmissionParameters(
      refContext                     = refContext,
      resolvedReplace                = resolvedReplace,
      isReplaceAll                   = isReplaceAll,
      isReplaceInstance              = isReplaceInstance,
      isReplaceText                  = isReplaceText,
      isReplaceNone                  = isReplaceNone,
      resolvedMethod                 = resolvedMethod,
      actualHttpMethod               = actualHttpMethod,
      resolvedMediatypeOpt           = resolvedMediatypeOpt,
      serializationOpt               = serializationOpt,
      serialize                      = serialize,
      resolvedValidate               = resolvedValidate,
      resolvedRelevanceHandling      = resolvedRelevanceHandling,
      resolvedXxfCalculate           = resolvedXxfCalculate,
      resolvedXxfUploads             = resolvedXxfUploads,
      resolvedXxfAnnotate            = resolvedXxfAnnotate,
      isHandlingClientGetAll         = isHandlingClientGetAll,
      isNoscript                     = isNoscript,
      isDeferredSubmission           = isDeferredSubmission,
      isDeferredSubmissionFirstPass  = isDeferredSubmissionFirstPass,
      isDeferredSubmissionSecondPass = isDeferredSubmissionSecondPass
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
      refInstanceOpt               = bindingContext.instance, // may be `None` if the document submitted is not part of an instance,
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