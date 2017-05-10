package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.XPathCache.XPathContext
import org.orbeon.oxf.util.{ContentTypes, NetUtils, XPathCache}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.{DocumentInfo, Item, NodeInfo}

class SubmissionParameters(val dynamicSubmission: XFormsModelSubmission, val eventName: String) {

  private[this] val staticSubmission   = dynamicSubmission.staticSubmission
  private[this] val containingDocument = dynamicSubmission.containingDocument

  private[submission] var refNodeInfo                  : NodeInfo = null
  private[submission] var refInstanceOpt               : Option[XFormsInstance] = None
  private[submission] var submissionElementContextItem : Item = null
  private[submission] var xpathContext                 : XPathContext = null

  initializeXPathContext(dynamicSubmission)

  // Check that we have a current node and that it is pointing to a document or an element
  if (refNodeInfo == null)
    throw new XFormsSubmissionException(
      dynamicSubmission,
      "Empty single-node binding on xf:submission for submission id: " + dynamicSubmission.getId,
      "getting submission single-node binding",
      new XFormsSubmitErrorEvent(
        dynamicSubmission,
        XFormsSubmitErrorEvent.NO_DATA,
        null
      )
    )

  if (! (refNodeInfo.isInstanceOf[DocumentInfo] || refNodeInfo.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE))
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

  val resolvedReplace   = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, staticSubmission.avtReplace)
  val isReplaceAll      = resolvedReplace == XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL
  val isReplaceInstance = resolvedReplace == XFormsConstants.XFORMS_SUBMIT_REPLACE_INSTANCE
  val isReplaceText     = resolvedReplace == XFormsConstants.XFORMS_SUBMIT_REPLACE_TEXT
  val isReplaceNone     = resolvedReplace == XFormsConstants.XFORMS_SUBMIT_REPLACE_NONE

  val resolvedMethod = {
    val resolvedMethodQName = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, staticSubmission.avtMethod)
    Dom4jUtils.qNameToExplodedQName(Dom4jUtils.extractTextValueQName(dynamicSubmission.staticSubmission.namespaceMapping.mapping, resolvedMethodQName, true))
  }
  val actualHttpMethod  = XFormsSubmissionUtils.getActualHttpMethod(resolvedMethod)
  val resolvedMediatype = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, staticSubmission.avtMediatype)

  val serialization = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, staticSubmission.avtSerialization)
  val serialize =
    if (serialization != null)
      ! (serialization == "none")
    else {
      // For backward compatibility only, support @serialize if there is no @serialization attribute (was in early XForms 1.1 draft)
      ! ("false" == dynamicSubmission.staticSubmission.element.attributeValue("serialize")) // TODO: use static
    }

  val resolvedValidateString = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, staticSubmission.avtValidate)

  // "The default value is "false" if the value of serialization is "none" and "true" otherwise"
  val resolvedValidate = serialize && ! ("false" == resolvedValidateString)
  val resolvedRelevant = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, staticSubmission.avtRelevant)

  val resolvedXXFormsCalculate = {
    val resolvedCalculateString = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, staticSubmission.avtXXFormsCalculate)
    serialize && ! ("false" == resolvedCalculateString)
  }

  val resolvedXXFormsUploads = {
    val resolvedUploadsString = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, staticSubmission.avtXXFormsUploads)
    serialize && ! ("false" == resolvedUploadsString)
  }
  val resolvedXXFormsAnnotate =
    if (serialize)
      XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, staticSubmission.avtXXFormsAnnotate)
    else
      null

  val isHandlingClientGetAll =
    containingDocument.isOptimizeGetAllSubmission                                                &&
      actualHttpMethod == "GET"                                                                  &&
      isReplaceAll                                                                               &&
      (resolvedMediatype == null || !resolvedMediatype.startsWith(ContentTypes.SoapContentType)) && // can't let SOAP requests be handled by the browser
      staticSubmission.avtXXFormsUsername == null                                                && // can't optimize if there are authentication credentials
      staticSubmission.avtXXFormsTarget == null                                                  && // can't optimize if there is a target
      dynamicSubmission.staticSubmission.element.elements(XFormsConstants.XFORMS_HEADER_QNAME).size == 0 // can't optimize if there are headers specified

  // TODO: use static for headers
  // In noscript mode, or in "Ajax portlet" mode, there is no deferred submission process
  // Also don't allow deferred submissions when the incoming method is a GET. This is an indirect way of
  // allowing things like using the XForms engine to generate a PDF with an HTTP GET.
  // NOTE: Method can be null e.g. in a portlet render request
  val method = NetUtils.getExternalContext.getRequest.getMethod
  val isNoscript = containingDocument.noscript
  val isAllowDeferredSubmission      = ! isNoscript && method != "GET"
  val isPossibleDeferredSubmission   = isReplaceAll && ! isHandlingClientGetAll && ! containingDocument.isInitializing
  val isDeferredSubmission           = isAllowDeferredSubmission && isPossibleDeferredSubmission
  val isDeferredSubmissionFirstPass  = isDeferredSubmission && XFormsEvents.XFORMS_SUBMIT == eventName
  val isDeferredSubmissionSecondPass = isDeferredSubmission && ! isDeferredSubmissionFirstPass // here we get XXFORMS_SUBMIT

  def initializeXPathContext(dynamicSubmission: XFormsModelSubmission): Unit = {

    val model = dynamicSubmission.getModel
    model.resetAndEvaluateVariables()

    val contextStack = model.getContextStack
    contextStack.pushBinding(dynamicSubmission.staticSubmission.element, dynamicSubmission.getEffectiveId, model.getResolutionScope)

    val bindingContext = contextStack.getCurrentBindingContext
    val functionContext = model.getContextStack.getFunctionContext(dynamicSubmission.getEffectiveId)

    refNodeInfo                  = bindingContext.getSingleItem.asInstanceOf[NodeInfo]
    submissionElementContextItem = bindingContext.contextItem
    refInstanceOpt               = bindingContext.instance // may be `None` if the document submitted is not part of an instance

    xpathContext =
      new XPathCache.XPathContext(
        dynamicSubmission.staticSubmission.namespaceMapping,
        bindingContext.getInScopeVariables,
        dynamicSubmission.containingDocument.getFunctionLibrary,
        functionContext,
        null,
        dynamicSubmission.staticSubmission.locationData
      )
  }
}