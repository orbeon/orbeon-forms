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

import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.dom.DocumentWrapper
import org.orbeon.saxon.om.{VirtualNode, NodeInfo}

import collection.JavaConverters._
import collection.mutable
import org.orbeon.dom._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.event.ListenersTrait
import org.orbeon.oxf.xforms.{InstanceData, XFormsContainingDocument}
import org.orbeon.oxf.xforms.model.BindNode

abstract class XFormsModelSubmissionBase extends ListenersTrait

object XFormsModelSubmissionBase {

  import Private._

  // Prepare XML for submission
  //
  // - re-root if `ref` points to an element other than the root element
  // - annotate with `xxf:id` if requested
  // - prune non-relevant nodes if requested
  // - annotate with alerts if requested
  def prepareXML(
    xfcd         : XFormsContainingDocument,
    ref          : NodeInfo,
    prune        : Boolean,
    annotateWith : String
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
        if (prune)
          pruneNonRelevantNodes(copy)

        annotateWithAlerts(xfcd, copy, annotationTokens collect LevelByName)
        copy

      // Submitting read-only instance backed by TinyTree (no MIPs to check)
      // TODO: What about re-rooting and annotations?
      case nodeInfo if ref.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE  ⇒
        TransformerUtils.tinyTreeToDom4j(ref)
      case nodeInfo ⇒
        TransformerUtils.tinyTreeToDom4j(ref.getRoot)
    }

  def pruneNonRelevantNodes(doc: Document): Unit =
    Iterator.iterateWhileDefined(findNextNodeToDetach(doc)) foreach (_.detach())

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
        val controls = xfcd.getControls.getCurrentControlTree.getEffectiveIdsToControls.asScala

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
            failedValidationsIds = (control.failedValidations map (_.id) toSet)
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

  import XFormsSubmissionUtils._

  def defaultSerialization(xformsMethod: String): Option[String] =
    xformsMethod.trimAllToOpt collect {
      case "multipart-post"                             ⇒ "multipart/related"
      case "form-data-post"                             ⇒ "multipart/form-data"
      case "urlencoded-post"                            ⇒ "application/x-www-form-urlencoded"
      case method if isPost(method) || isPut(method)    ⇒ "application/xml"
      case method if isGet(method)  || isDelete(method) ⇒ "application/x-www-form-urlencoded"
    }

  def requestedSerialization(xformsSerialization: String, xformsMethod: String) =
    xformsSerialization.trimAllToOpt orElse defaultSerialization(xformsMethod)

  def getRequestedSerializationOrNull(xformsSerialization: String, xformsMethod: String) =
    requestedSerialization(xformsSerialization, xformsMethod).orNull

  private object Private {

    val processBreaks = new scala.util.control.Breaks
    import processBreaks._

    def findNextNodeToDetach(doc: Document) = {

      var nodeToDetach: Node = null

      tryBreakable[Option[Node]] {
        doc.accept(
          new VisitorSupport {
            override def visit(element: Element) =
              checkInstanceData(element)

            override def visit(attribute: Attribute) =
              checkInstanceData(attribute)

            private def checkInstanceData(node: Node) =
              if (! InstanceData.getInheritedRelevant(node)) {
                nodeToDetach = node
                break()
              }
          }
        )
        None
      } catchBreak {
        Some(nodeToDetach)
      }
    }

    def addRootElementNamespace(doc: Document) =
      doc.getRootElement.addNamespace(XXFORMS_NAMESPACE_SHORT.prefix, XXFORMS_NAMESPACE_SHORT.uri)
  }
}