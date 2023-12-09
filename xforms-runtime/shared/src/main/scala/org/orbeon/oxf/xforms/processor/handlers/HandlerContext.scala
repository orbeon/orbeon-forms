package org.orbeon.oxf.xforms.processor.handlers

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.xforms.control.controls.{XFormsCaseControl, XFormsRepeatIterationControl, XXFormsDynamicControl}
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.PartAnalysis
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xml.dom.XmlLocationData
import org.orbeon.oxf.xml.{ElementHandlerController, XMLConstants}
import org.orbeon.xforms.Constants
import org.xml.sax.Attributes


/**
 * Context used when converting XHTML+XForms into XHTML.
 */
object HandlerContext {
  private case class RepeatContext(idPostfix: String, repeatSelected: Boolean)
}

class HandlerContext(
  val controller                 : ElementHandlerController[HandlerContext],
  val containingDocument         : XFormsContainingDocument,
  val externalContext            : ExternalContext,
  val topLevelControlEffectiveId : Option[String],
  val collector                  : ErrorEventCollector
) {

  // Computed during construction
  val documentOrder    : (List[String], List[String]) = containingDocument.lhhacOrder
  val labelElementName : String                       = containingDocument.getLabelElementName
  val hintElementName  : String                       = containingDocument.getHintElementName
  val helpElementName  : String                       = containingDocument.getHelpElementName
  val alertElementName : String                       = containingDocument.getAlertElementName

  val a11yFocusOnGroups: Boolean                      = containingDocument.a11yFocusOnGroups

  // Context information
  private var partAnalysisStack     : List[PartAnalysis]                 = List(containingDocument.staticState.topLevelPart)
  private var componentContextStack : List[String]                       = Nil
  private var repeatContextStack    : List[HandlerContext.RepeatContext] = Nil
  private var caseContextStack      : List[Boolean]                      = Nil

  def pushPartAnalysis(partAnalysis: PartAnalysis): Unit =
    partAnalysisStack ::= partAnalysis

  def popPartAnalysis(): Unit =
    partAnalysisStack = partAnalysisStack.tail

  def getPartAnalysis: PartAnalysis                             = partAnalysisStack.head

  def findXHTMLPrefix: String = {

    val prefix = controller.namespaceContext.getPrefix(XMLConstants.XHTML_NAMESPACE_URI)
    if (prefix != null)
      return prefix

    if (XMLConstants.XHTML_NAMESPACE_URI == controller.namespaceContext.getURI(""))
      return ""

    // TEMP: in this case, we should probably map our own prefix, or set
    // the default namespace and restore it on children elements
    throw new ValidationException("No prefix found for HTML namespace", XmlLocationData.createIfPresent(controller.locator))
  }

  private def findFormattingPrefix: String = {

    val prefix = controller.namespaceContext.getPrefix(XMLConstants.OPS_FORMATTING_URI)
    if (prefix ne null)
      return prefix

    if (XMLConstants.OPS_FORMATTING_URI == controller.namespaceContext.getURI(""))
      return ""

    null
  }

  def findFormattingPrefixDeclare: String = {
    var formattingPrefix: String = null
    var isNewPrefix = false
    val existingFormattingPrefix = findFormattingPrefix
    if ((existingFormattingPrefix eq null) || existingFormattingPrefix.isEmpty) {
      // No prefix is currently mapped
      formattingPrefix = findNewPrefix
      isNewPrefix = true
    } else {
      formattingPrefix = existingFormattingPrefix
      isNewPrefix = false
    }

    // Start mapping if needed
    if (isNewPrefix)
      controller.output.startPrefixMapping(formattingPrefix, XMLConstants.OPS_FORMATTING_URI)

    formattingPrefix
  }

  def findFormattingPrefixUndeclare(formattingPrefix: String): Unit = {
    val existingFormattingPrefix = findFormattingPrefix
    val isNewPrefix = (existingFormattingPrefix eq null) || existingFormattingPrefix.isEmpty
    // End mapping if needed
    if (isNewPrefix)
      controller.output.endPrefixMapping(formattingPrefix)
  }

  private def findNewPrefix: String = {
    var i = 0
    while (controller.namespaceContext.getURI("p" + i) ne null)
      i += 1
    "p" + i
  }

  def getPrefixedId(controlElementAttributes: Attributes): String = {
    val id = controlElementAttributes.getValue("id")
    if (id != null)
      getIdPrefix + id
    else
      null
  }

  def getEffectiveId(controlElementAttributes: Attributes): String = {
    val prefixedId = getPrefixedId(controlElementAttributes)
    if (prefixedId != null)
      prefixedId + getIdPostfix
    else
      null
  }

  /**
   * Return true iif the given control effective id is the same as the top-level control passed during construction.
   *
   * NOTE: This is used by the repeat handler to not output delimiters during full updates.
   *
   * @param effectiveId control effective id
   * @return true iif id matches the id passed during construction
   */
  def isFullUpdateTopLevelControl(effectiveId: String): Boolean =
     topLevelControlEffectiveId contains effectiveId

  def hasFullUpdateTopLevelControl: Boolean =
    topLevelControlEffectiveId.isDefined

  /**
   * Return location data associated with the current SAX event.
   *
   * @return LocationData, null if no Locator was found
   */
  def getLocationData: LocationData = XmlLocationData.createIfPresent(controller.locator)

  def getIdPrefix: String =
    if (componentContextStack.isEmpty)
      ""
    else
      componentContextStack.head

  def pushComponentContext(prefixedId: String): Unit =
    componentContextStack ::= prefixedId + Constants.ComponentSeparator

  def popComponentContext(): Unit =
    componentContextStack = componentContextStack.tail

  def pushCaseContext(visible: Boolean): Unit = {

    val currentVisible =
      if (caseContextStack.isEmpty)
        true
      else
        caseContextStack.head

    caseContextStack ::= currentVisible && visible
  }

  def popCaseContext(): Unit =
    caseContextStack = caseContextStack.tail

  def getIdPostfix: String =
    if (repeatContextStack.isEmpty)
      ""
    else
      repeatContextStack.head.idPostfix

  def isRepeatSelected: Boolean =
    if (repeatContextStack.isEmpty)
      false
    else
      repeatContextStack.head.repeatSelected

  def countParentRepeats: Int =
    repeatContextStack.size

  def pushRepeatContext(iteration: Int, repeatSelected: Boolean): Unit = {

    val currentIdPostfix = getIdPostfix

    // Create postfix depending on whether we are appending to an existing postfix or not
    val newIdPostfix =
      if (currentIdPostfix.isEmpty)
        Constants.RepeatSeparatorString + iteration
      else
        currentIdPostfix + Constants.RepeatIndexSeparatorString + iteration

    repeatContextStack ::= HandlerContext.RepeatContext(newIdPostfix, repeatSelected)
  }

  def popRepeatContext(): Unit =
    repeatContextStack = repeatContextStack.tail

  /**
   * Restore the handler state up to (but excluding) the given control.
   *
   * Used if the context is not used from the root of the control tree.
   *
   * This restores repeats and components state.
   */
  def restoreContext(control: XFormsControl): Unit = {

    val controlsFromRootToLeaf =
      (Iterator.iterate(control.parent)(_.parent) takeWhile (_ ne null)).toList.reverse

    for (currentControl <- controlsFromRootToLeaf)
      currentControl match {
        case repeatIteration: XFormsRepeatIterationControl =>
          val repeat           = repeatIteration.repeat
          val isTopLevel       = countParentRepeats == 0
          val selected         = isRepeatSelected || isTopLevel
          val currentIteration = repeatIteration.iterationIndex
          pushRepeatContext(currentIteration, selected || currentIteration == repeat.getIndex)
        case component: XFormsComponentControl =>
          pushComponentContext(component.getPrefixedId)
        case dynamic: XXFormsDynamicControl =>
          pushComponentContext(dynamic.getPrefixedId)
          pushPartAnalysis(dynamic.nested.get.partAnalysis)
        case control1: XFormsCaseControl => // not used as of 2012-04-16
          pushCaseContext(control1.isCaseVisible)
        case _ =>
      }
  }
}