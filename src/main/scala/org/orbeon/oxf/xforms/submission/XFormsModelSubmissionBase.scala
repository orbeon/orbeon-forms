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
package org.orbeon.oxf.xforms.submission

import enumeratum._
import org.orbeon.dom._
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent.XXFORMS_INTERNAL_ERROR
import org.orbeon.oxf.xforms.event.{Dispatch, ListenersTrait, XFormsEventObserver, XFormsEventTarget}
import org.orbeon.oxf.xforms.model.BindNode
import org.orbeon.oxf.xforms.{InstanceData, XFormsContainingDocument, XFormsInstance, XFormsModel}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.{NodeInfo, VirtualNode}

import scala.collection.mutable

sealed abstract class RelevanceHandling extends EnumEntry

object RelevanceHandling extends Enum[RelevanceHandling] {

  val values = findValues

  case object Keep  extends RelevanceHandling
  case object Prune extends RelevanceHandling
  case object Blank extends RelevanceHandling

  // For backward compatibility, the tokens `false` and `true` are still supported
  def withNameAdjustForTrueAndFalse(name: String): RelevanceHandling =
    super.withNameLowercaseOnlyOption(name) getOrElse {
      if (name == "false")
        Keep
      else
        Prune
    }
}

abstract class XFormsModelSubmissionBase
  extends ListenersTrait
     with XFormsEventTarget
     with XFormsEventObserver {

  thisSubmission: XFormsModelSubmission ⇒

  import XFormsModelSubmissionBase._

  def getModel: XFormsModel

  protected def sendSubmitError(throwable: Throwable, submissionResult: SubmissionResult): Unit =
    sendSubmitErrorWithDefault(
      throwable,
      new XFormsSubmitErrorEvent(thisSubmission, XXFORMS_INTERNAL_ERROR, submissionResult.getConnectionResult)
    )

  protected def sendSubmitError(throwable: Throwable, resolvedActionOrResource: String): Unit =
    sendSubmitErrorWithDefault(
      throwable,
      new XFormsSubmitErrorEvent(thisSubmission, Option(resolvedActionOrResource), XXFORMS_INTERNAL_ERROR, 0)
    )

  private def sendSubmitErrorWithDefault(throwable: Throwable, default: ⇒ XFormsSubmitErrorEvent): Unit = {

    // After a submission, the context might have changed
    getModel.resetAndEvaluateVariables()

    // Try to get error event from exception and if not possible create default event
    val submitErrorEvent =
      (throwable collect { case se: XFormsSubmissionException ⇒ Option(se.getXFormsSubmitErrorEvent) } flatten) getOrElse default

    // Dispatch event
    submitErrorEvent.logMessage(throwable)
    Dispatch.dispatchEvent(submitErrorEvent)
  }

  protected def createDocumentToSubmit(
    currentNodeInfo   : NodeInfo,
    currentInstance   : Option[XFormsInstance],
    validate          : Boolean,
    relevanceHandling : RelevanceHandling,
    annotateWith      : String)(implicit
    indentedLogger    : IndentedLogger
  ): Document = {

    // Revalidate instance
    // NOTE: We need to do this before pruning so that bind/@type works correctly. XForms 1.1 seems to say that this
    // must be done after pruning, but then it is not clear how XML Schema validation would work then.
    // Also, if validate="false" or if serialization="none", then we do not revalidate. Now whether this optimization
    // is acceptable depends on whether validate="false" only means "don't check the instance's validity" or also
    // don't even recalculate. If the latter, then this also means that type annotations won't be updated, which
    // can impact serializations that use type information, for example multipart. But in that case, here we decide
    // the optimization is worth it anyway.
    if (validate)
      currentInstance foreach (_.model.doRecalculateRevalidate())

    // Get selected nodes (re-root and handle relevance)
    val documentToSubmit =
      prepareXML(
        containingDocument,
        currentNodeInfo,
        relevanceHandling,
        annotateWith
      )

    // Check that there are no validation errors
    // NOTE: If the instance is read-only, it can't have MIPs at the moment, and can't fail validation/requiredness,
    // so we don't go through the process at all.
    val instanceSatisfiesValidRequired =
      currentInstance.exists(_.readonly) ||
      ! validate                         ||
      isSatisfiesValidity(documentToSubmit, relevanceHandling, recurse = true)

    if (! instanceSatisfiesValidRequired) {
      if (indentedLogger.isDebugEnabled) {
        val documentString = TransformerUtils.tinyTreeToString(currentNodeInfo)
        indentedLogger.logDebug("", "instance document or subset thereof cannot be submitted", "document", documentString)
      }
      throw new XFormsSubmissionException(
        thisSubmission,
        "xf:submission: instance to submit does not satisfy valid and/or required model item properties.",
        "checking instance validity",
        new XFormsSubmitErrorEvent(thisSubmission, XFormsSubmitErrorEvent.VALIDATION_ERROR, null)
      )
    }

    documentToSubmit
  }

}

object XFormsModelSubmissionBase {

  import Private._
  import XFormsSubmissionUtils._

  // Prepare XML for submission
  //
  // - re-root if `ref` points to an element other than the root element
  // - annotate with `xxf:id` if requested
  // - prune or blank non-relevant nodes if requested
  // - annotate with alerts if requested
  def prepareXML(
    xfcd              : XFormsContainingDocument,
    ref               : NodeInfo,
    relevanceHandling : RelevanceHandling,
    annotateWith      : String
  ): Document =
    ref match {
      case virtualNode: VirtualNode ⇒

        // "A node from the instance data is selected, based on attributes on the submission
        // element. The indicated node and all nodes for which it is an ancestor are considered for
        // the remainder of the submit process. "
        val copy =
          virtualNode.getUnderlyingNode match {
            case e: Element ⇒ Dom4jUtils.createDocumentCopyParentNamespaces(e)
            case n: Node    ⇒ Dom4jUtils.createDocumentCopyElement(n.getDocument.getRootElement)
            case _          ⇒ throw new IllegalStateException
          }

        val annotationTokens = stringToSet(annotateWith)

        // Annotate ids before pruning so that it is easier for other code (Form Runner) to infer the same ids
        if (annotationTokens("id"))
          annotateWithHashes(copy)

        // "Any node which is considered not relevant as defined in 6.1.4 is removed."
        relevanceHandling match {
          case RelevanceHandling.Keep  ⇒ // NOP
          case RelevanceHandling.Prune ⇒ pruneNonRelevantNodes(copy)
          case RelevanceHandling.Blank ⇒ blankNonRelevantNodes(copy)
        }

        annotateWithAlerts(xfcd, copy, annotationTokens collect LevelByName)
        copy

      // Submitting read-only instance backed by TinyTree (no MIPs to check)
      // TODO: What about re-rooting and annotations?
      case ref if ref.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE  ⇒
        TransformerUtils.tinyTreeToDom4j(ref)
      case ref ⇒
        TransformerUtils.tinyTreeToDom4j(ref.getRoot)
    }

  def pruneNonRelevantNodes(doc: Document): Unit =
    Iterator.iterateWhileDefined(findFirstNonRelevantElementOrAttribute(doc)) foreach (_.detach())

  def blankNonRelevantNodes(doc: Document): Unit =
    Iterator.iterateWhileDefined(findFirstNonRelevantSimpleElementOrAttribute(doc)) foreach (_.setText(""))

  def annotateWithHashes(doc: Document): Unit = {
    val wrapper = new DocumentWrapper(doc, null, XPath.GlobalConfiguration)
    var annotated = false
    doc.accept(new VisitorSupport() {
      override def visit(element: Element): Unit = {
        val hash = SubmissionUtils.dataNodeHash(wrapper.wrap(element))
        element.addAttribute(QName.get("id", XXFORMS_NAMESPACE_SHORT), hash)
        annotated = true
      }
    })
    if (annotated)
      addRootElementNamespace(doc)
  }

  // Annotate elements which have failed constraints with an xxf:error, xxf:warning or xxf:info attribute containing
  // the alert message. Only the levels passed in `annotate` are handled.
  def annotateWithAlerts(
    xfcd             : XFormsContainingDocument,
    doc              : Document,
    levelsToAnnotate : Set[ValidationLevel]
  ): Unit =
    if (levelsToAnnotate.nonEmpty) {

      val elementsToAnnotate = mutable.Map[ValidationLevel, mutable.Map[Set[String], Element]]()

      // Iterate data to gather elements with failed constraints
      doc.accept(new VisitorSupport() {
        override def visit(element: Element): Unit = {
          val failedValidations = BindNode.failedValidationsForAllLevelsPrioritizeRequired(element)
          for (level ← levelsToAnnotate) {
            // NOTE: Annotate all levels specified. If we decide to store only one level of validation
            // in bind nodes, then we would have to change this to take the highest level only and ignore
            // the other levels.
            val failedValidationsForLevel = failedValidations.getOrElse(level, Nil)
            if (failedValidationsForLevel.nonEmpty) {
              val map = elementsToAnnotate.getOrElseUpdate(level, mutable.Map[Set[String], Element]())
              map += (failedValidationsForLevel map (_.id) toSet) → element
            }
          }
        }
      })

      if (elementsToAnnotate.nonEmpty) {
        val controls = xfcd.getControls.getCurrentControlTree.effectiveIdsToControls

        val relevantLevels = elementsToAnnotate.keySet

        def controlsIterator =
          controls.iterator collect {
            case (_, control: XFormsSingleNodeControl)
              if control.isRelevant && control.alertLevel.toList.toSet.subsetOf(relevantLevels) ⇒ control
          }

        var annotated = false

        def annotateElementIfPossible(control: XFormsSingleNodeControl) = {
          // NOTE: We check on the whole set of constraint ids. Since the control reads in all the failed
          // constraints for the level, the sets of ids must match.
          for {
            level                ← control.alertLevel
            controlAlert         ← Option(control.getAlert)
            failedValidationsIds = control.failedValidations.map(_.id).toSet
            elementsMap          ← elementsToAnnotate.get(level)
            element              ← elementsMap.get(failedValidationsIds)
            qName                = QName.get(level.name, XXFORMS_NAMESPACE_SHORT)
          } locally {
            // There can be an existing attribute if more than one control bind to the same element
            Option(element.attribute(qName)) match {
              case Some(existing) ⇒ existing.setValue(existing.getValue + controlAlert)
              case None           ⇒ element.addAttribute(qName, controlAlert)
            }

            annotated = true
          }
        }

        // Iterate all controls with warnings and try to annotate the associated element nodes
        controlsIterator foreach annotateElementIfPossible

        // If there is any annotation, make sure the attribute's namespace prefix is in scope on the root
        // element
        if (annotated)
          addRootElementNamespace(doc)
      }
    }

  def isSatisfiesValidity(
    startNode        : Node,
    relevantHandling : RelevanceHandling,
    recurse          : Boolean)(implicit
    indentedLogger   : IndentedLogger
  ): Boolean = {

    import RelevanceHandling._

    val checkInstanceData: Node ⇒ Boolean =
      relevantHandling match {
        case Keep | Prune ⇒ node ⇒ ! InstanceData.getValid(node)
        case Blank        ⇒ node ⇒ ! InstanceData.getValid(node) && InstanceData.getInheritedRelevant(node)
      }

    if (recurse) {
      findFirstElementOrAttributeWith(startNode, checkInstanceData) match {
        case Some(e: Element) ⇒
          indentedLogger.logDebug(
            "",
            "found invalid node",
            "element name",
            Dom4jUtils.elementToDebugString(e)
          )
          false
        case Some(a: Attribute) ⇒
          indentedLogger.logDebug(
            "",
            "found invalid attribute",
            "attribute name",
            Dom4jUtils.attributeToDebugString(a),
            "parent element",
            Dom4jUtils.elementToDebugString(a.getParent)
          )
          false
        case Some(_) ⇒
          throw new IllegalArgumentException
        case None ⇒
          true
      }
    } else {
      checkInstanceData(startNode)
    }
  }

  def defaultSerialization(xformsMethod: String): Option[String] =
    xformsMethod.trimAllToOpt collect {
      case "multipart-post"                             ⇒ "multipart/related"
      case "form-data-post"                             ⇒ "multipart/form-data"
      case "urlencoded-post"                            ⇒ "application/x-www-form-urlencoded"
      case method if isPost(method) || isPut(method)    ⇒ "application/xml"
      case method if isGet(method)  || isDelete(method) ⇒ "application/x-www-form-urlencoded"
    }

  def requestedSerialization(xformsSerialization: String, xformsMethod: String): Option[String] =
    xformsSerialization.trimAllToOpt orElse defaultSerialization(xformsMethod)

  def getRequestedSerializationOrNull(xformsSerialization: String, xformsMethod: String): String =
    requestedSerialization(xformsSerialization, xformsMethod).orNull

  private object Private {

    val breaks = new scala.util.control.Breaks
    import breaks._

    def findFirstNonRelevantElementOrAttribute(startNode: Node): Option[Node] =
      findFirstElementOrAttributeWith(startNode, node ⇒ ! InstanceData.getInheritedRelevant(node))

    def findFirstNonRelevantSimpleElementOrAttribute(startNode: Node): Option[Node] =
      findFirstElementOrAttributeWith(startNode, {
        case e: Element ⇒ ! e.containsElement && ! InstanceData.getInheritedRelevant(e)
        case n          ⇒ ! InstanceData.getInheritedRelevant(n)
      })

    def findFirstElementOrAttributeWith(startNode: Node, check: Node ⇒ Boolean): Option[Node] = {

      var foundNode: Node = null

      tryBreakable[Option[Node]] {
        startNode.accept(
          new VisitorSupport {
            override def visit(element: Element)     = checkNodeAndBreakIfFail(element)
            override def visit(attribute: Attribute) = checkNodeAndBreakIfFail(attribute)

            def checkNodeAndBreakIfFail(node: Node) =
              if (check(node)) {
                foundNode = node
                break()
              }
          }
        )
        None
      } catchBreak {
        Some(foundNode)
      }
    }

    def addRootElementNamespace(doc: Document) =
      doc.getRootElement.addNamespace(XXFORMS_NAMESPACE_SHORT.prefix, XXFORMS_NAMESPACE_SHORT.uri)
  }
}