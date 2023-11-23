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

import org.orbeon.connection.{AsyncConnectionResult, ConnectionResult}
import org.orbeon.dom._
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.PathUtils.decodeSimpleQuery
import org.orbeon.oxf.util.StaticXPath.VirtualNodeType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger, StaticXPath, XPath}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.model.ModelDefs.Relevant
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.model.{BindNode, InstanceData}
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.om
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.model.ValidationLevel

import scala.collection.mutable
import scala.concurrent.Future


trait XFormsModelSubmissionSupportTrait {

  import Private._
  import RelevanceHandling._

  // Run the given submission. This must be for a `replace="all"` submission.
  // Called from `XFormsServer` only
  def runDeferredSubmissionForUpdate(future: Future[AsyncConnectResult], response: ExternalContext.Response): Unit

  def forwardResultToResponse(cxr: ConnectionResult, response: ExternalContext.Response): Unit = {
    SubmissionUtils.forwardStatusContentTypeAndHeaders(cxr, response)
    IOUtils.copyStreamAndClose(cxr.content.stream, response.getOutputStream)
  }

  // Prepare XML for submission
  //
  // - re-root if `ref` points to an element other than the root element
  // - annotate with `xxf:id` if requested
  // - prune or blank non-relevant nodes if requested
  // - annotate with alerts if requested
  def prepareXML(
    xfcd              : XFormsContainingDocument,
    ref               : om.NodeInfo,
    relevanceHandling : RelevanceHandling,
    namespaceContext  : Map[String, String],
    annotateWith      : Set[String],
    relevantAttOpt    : Option[QName]
  ): Document =
    ref match {
      case virtualNode: VirtualNodeType =>

        // "A node from the instance data is selected, based on attributes on the submission
        // element. The indicated node and all nodes for which it is an ancestor are considered for
        // the remainder of the submit process. "
        val copy =
          virtualNode.getUnderlyingNode match {
            case e: Element => e.createDocumentCopyParentNamespaces(detach = false)
            case n: Node    => Document(n.getDocument.getRootElement.createCopy)
            case _          => throw new IllegalStateException
          }

        val attributeNamesForTokens =
          annotateWith.iterator map { token =>
            decodeSimpleQuery(token).headOption match {
              case Some((name, value)) =>
                name -> {
                  value.trimAllToOpt flatMap
                    (Extensions.resolveQName(namespaceContext.get, _, unprefixedIsNoNamespace = true)) getOrElse
                    xxfQName(name)
                }
              case None =>
                throw new IllegalArgumentException(s"invalid format for `xxf:annotate` value: `$annotateWith`")
            }
          } toMap

        // Annotate ids before pruning so that it is easier for other code (Form Runner) to infer the same ids
        attributeNamesForTokens.get("id") foreach
          (annotateWithHashes(copy, _))

        processRelevant(
          doc                           = copy,
          relevanceHandling             = relevanceHandling,
          relevantAttOpt                = relevantAttOpt,
          relevantAnnotationAttQNameOpt = attributeNamesForTokens.get(Relevant.name)
        )

        val root = copy.getRootElement
        attributeNamesForTokens.map(_._2.namespace).toSet.foreach { (ns: Namespace) =>

          Option(root.getNamespaceForPrefix(ns.prefix)) match {
            case None =>
              root.add(ns)
            case Some(existingNs) if existingNs != ns =>
              throw new IllegalArgumentException(s"incompatible namespace prefix on root element: `${ns.prefix}` maps to `${existingNs.uri}` and `${ns.uri} is expected`")
            case _ =>
          }
        }

        annotateWithAlerts(
          xfcd             = xfcd,
          doc              = copy,
          levelsToAnnotate =
            attributeNamesForTokens.keySet collect
              ValidationLevel.LevelByName  map { level =>
                level -> attributeNamesForTokens(level.entryName)
            } toMap
        )

        copy

      // Submitting read-only instance backed by TinyTree (no MIPs to check)
      // TODO: What about re-rooting and annotations?
      case ref if ref.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE  =>
        StaticXPath.tinyTreeToOrbeonDom(ref)
      case ref =>
        StaticXPath.tinyTreeToOrbeonDom(ref.getRoot)
    }

  def processRelevant(
    doc                           : Document,
    relevanceHandling             : RelevanceHandling,
    relevantAttOpt                : Option[QName],
    relevantAnnotationAttQNameOpt : Option[QName]
  ): Unit = {
    // If we have `xxf:relevant-attribute="fr:relevant"`, say, then we use that attribute to also determine
    // the relevance of the element. See https://github.com/orbeon/orbeon-forms/issues/3568.
    val isNonRelevantSupportAnnotationIfPresent: Node => Boolean =
      relevantAttOpt                          map
        isLocallyNonRelevantSupportAnnotation getOrElse
        isLocallyNonRelevant _

    relevanceHandling match {
      case RelevanceHandling.Keep | RelevanceHandling.Empty =>

        if (relevanceHandling == RelevanceHandling.Empty)
          blankNonRelevantNodes(
            doc                  = doc,
            attsToPreserve       = relevantAnnotationAttQNameOpt.toSet ++ relevantAttOpt,
            isLocallyNonRelevant = isNonRelevantSupportAnnotationIfPresent
          )

        relevantAnnotationAttQNameOpt foreach { relevantAnnotationAttName =>
          annotateNonRelevantElements(
            doc                        = doc,
            relevantAnnotationAttQName = relevantAnnotationAttName,
            isNonRelevant              = isNonRelevantSupportAnnotationIfPresent
          )
        }

        if (relevantAnnotationAttQNameOpt != relevantAttOpt)
          relevantAttOpt foreach { relevantAtt =>
            removeNestedAnnotations(doc.getRootElement, relevantAtt, includeSelf = true)
          }

      case RelevanceHandling.Remove =>

        pruneNonRelevantNodes(doc, isNonRelevantSupportAnnotationIfPresent)

        // There can be leftover annotations, in particular attributes with value `true`!
        val attsToRemove = relevantAttOpt.toList :::
          (if (relevantAnnotationAttQNameOpt != relevantAttOpt) relevantAnnotationAttQNameOpt.toList else Nil)

        attsToRemove foreach { attQName =>
          removeNestedAnnotations(doc.getRootElement, attQName, includeSelf = true)
        }
    }
  }

  private def annotateWithHashes(doc: Document, attQName: QName): Unit = {
    val wrapper = new DocumentWrapper(doc, null, XPath.GlobalConfiguration)
    var annotated = false
    doc.accept(new VisitorSupport {
      override def visit(element: Element): Unit = {
        val hash = SubmissionUtils.dataNodeHash(wrapper.wrap(element))
        element.addAttribute(attQName, hash)
        annotated = true
      }
    })
    if (annotated)
      addRootElementNamespace(doc)
  }

  // Annotate elements which have failed constraints with an xxf:error, xxf:warning or xxf:info attribute containing
  // the alert message. Only the levels passed in `annotate` are handled.
  private def annotateWithAlerts(
    xfcd             : XFormsContainingDocument,
    doc              : Document,
    levelsToAnnotate : Map[ValidationLevel, QName]
  ): Unit =
    if (levelsToAnnotate.nonEmpty) {

      val elementsToAnnotate = mutable.Map[ValidationLevel, mutable.Map[Set[String], Element]]()

      // Iterate data to gather elements with failed constraints
      doc.accept(new VisitorSupport {
        override def visit(element: Element): Unit = {
          val failedValidations = BindNode.failedValidationsForAllLevelsPrioritizeRequired(element)
          for (level <- levelsToAnnotate.keys) {
            // NOTE: Annotate all levels specified. If we decide to store only one level of validation
            // in bind nodes, then we would have to change this to take the highest level only and ignore
            // the other levels.
            val failedValidationsForLevel = failedValidations.getOrElse(level, Nil)
            if (failedValidationsForLevel.nonEmpty) {
              val map = elementsToAnnotate.getOrElseUpdate(level, mutable.Map[Set[String], Element]())
              map += (failedValidationsForLevel map (_.id) toSet) -> element
            }
          }
        }
      })

      if (elementsToAnnotate.nonEmpty) {
        val controls = xfcd.controls.getCurrentControlTree.effectiveIdsToControls

        val relevantLevels = elementsToAnnotate.keySet

        def controlsIterator: Iterator[XFormsSingleNodeControl] =
          controls.iterator collect {
            case (_, control: XFormsSingleNodeControl)
              if control.isRelevant && control.alertLevel.toList.toSet.subsetOf(relevantLevels) => control
          }

        var annotated = false

        def annotateElementIfPossible(control: XFormsSingleNodeControl): Unit = {
          // NOTE: We check on the whole set of constraint ids. Since the control reads in all the failed
          // constraints for the level, the sets of ids must match.
          for {
            level                <- control.alertLevel
            controlAlert         <- Option(control.getAlert)
            failedValidationsIds = control.failedValidations.map(_.id).toSet
            elementsMap          <- elementsToAnnotate.get(level)
            element              <- elementsMap.get(failedValidationsIds)
            qName                <- levelsToAnnotate.get(level)
          } locally {
            // There can be an existing attribute if more than one control bind to the same element
            Option(element.attribute(qName)) match {
              case Some(existing) => existing.setValue(existing.getValue + controlAlert)
              case None           => element.addAttribute(qName, controlAlert)
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
    relevantHandling : RelevanceHandling)(implicit
    indentedLogger   : IndentedLogger
  ): Boolean =
    findFirstElementOrAttributeWith(
      startNode,
      relevantHandling match {
        case Keep | Remove => node => ! InstanceData.getValid(node)
        case Empty         => node => ! InstanceData.getValid(node) && InstanceData.getInheritedRelevant(node)
      }
    ) match {
      case Some(e: Element) =>
        logInvalidNode(e)
        false
      case Some(a: Attribute) =>
        logInvalidNode(a)
        false
      case Some(_) =>
        throw new IllegalArgumentException
      case None =>
        true
    }

  private def logInvalidNode(node: Node)(implicit indentedLogger: IndentedLogger): Unit =
    if (indentedLogger.debugEnabled)
      node match {
        case e: Element =>
            debug(
              "found invalid node",
              List("element name" -> e.toDebugString)
            )
        case a: Attribute =>
          debug(
            "found invalid attribute",
            List(
              "attribute name" -> a.toDebugString,
              "parent element" -> a.getParent.toDebugString
            )
          )
        case _ =>
          throw new IllegalArgumentException
      }

  def requestedSerialization(
    xformsSerialization : Option[String],
    xformsMethod        : String,
    httpMethod          : HttpMethod
  ): Option[String] =
      xformsSerialization flatMap (_.trimAllToOpt) orElse defaultSerialization(xformsMethod, httpMethod)

  private object Private {

    def defaultSerialization(xformsMethod: String, httpMethod: HttpMethod): Option[String] =
      xformsMethod.trimAllToOpt collect {
        case "multipart-post"                                                      => "multipart/related"
        case "form-data-post"                                                      => "multipart/form-data"
        case "urlencoded-post"                                                     => "application/x-www-form-urlencoded"
        case _ if httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT ||
                  httpMethod == HttpMethod.LOCK || httpMethod == HttpMethod.UNLOCK => ContentTypes.XmlContentType
        case _ if httpMethod == HttpMethod.GET  || httpMethod == HttpMethod.DELETE => "application/x-www-form-urlencoded"
      }

    def isLocallyNonRelevant(node: Node): Boolean =
      ! InstanceData.getLocalRelevant(node)

    // NOTE: Optimize by not calling `getInheritedRelevant`, as we go from root to leaf. Also, we know
    // that MIPs are not stored on `Document` and other nodes.
    def isLocallyNonRelevantSupportAnnotation(attQname: QName): Node => Boolean = {
      case e: Element   => ! InstanceData.getLocalRelevant(e) || (e.attributeValueOpt(attQname) contains false.toString)
      case a: Attribute => ! InstanceData.getLocalRelevant(a)
      case _            => false
    }

    def pruneNonRelevantNodes(doc: Document, isLocallyNonRelevant: Node => Boolean): Unit = {

      def processElement(e: Element): List[Node] =
        if (isLocallyNonRelevant(e)) {
          List(e)
        } else {
          e.attributes.filter(isLocallyNonRelevant) ++:
            e.elements.toList.flatMap(processElement)
        }

      processElement(doc.getRootElement) foreach (_.detach())
    }

    def blankNonRelevantNodes(
      doc                  : Document,
      attsToPreserve       : Set[QName],
      isLocallyNonRelevant : Node => Boolean
    ): Unit = {

      def processElement(e: Element, parentNonRelevant: Boolean): Unit = {

        val elemNonRelevant = parentNonRelevant || isLocallyNonRelevant(e)

        // NOTE: Make sure not to blank attributes corresponding to annotations if present!
        e.attributeIterator                                                                   filter
          (a => ! attsToPreserve(a.getQName) && (elemNonRelevant || isLocallyNonRelevant(a))) foreach
          (_.setValue(""))

        if (e.containsElement)
          e.elements foreach (processElement(_, elemNonRelevant))
        else if (elemNonRelevant)
          e.setText("")
      }

      processElement(doc.getRootElement, parentNonRelevant = false)
    }

    def annotateNonRelevantElements(
      doc                        : Document,
      relevantAnnotationAttQName : QName,
      isNonRelevant              : Node => Boolean
    ): Unit = {

      def processElem(e: Element): Unit =
        if (isNonRelevant(e)) {
          e.addAttribute(relevantAnnotationAttQName, false.toString)
          removeNestedAnnotations(e, relevantAnnotationAttQName, includeSelf = false)
        } else {
          e.removeAttribute(relevantAnnotationAttQName)
          e.elements foreach processElem
        }

      processElem(doc.getRootElement)
    }

    def removeNestedAnnotations(startElem: Element, attQname: QName, includeSelf: Boolean): Unit = {

      def processElem(e: Element): Unit = {
        e.removeAttribute(attQname)
        e.elements foreach processElem
      }

      if (includeSelf)
        processElem(startElem)
      else
        startElem.elements foreach processElem
    }

    def findFirstElementOrAttributeWith(startNode: Node, check: Node => Boolean): Option[Node] = {

      val breaks = new scala.util.control.Breaks
      import breaks._

      var foundNode: Node = null

      tryBreakable[Option[Node]] {
        startNode.accept(
          new VisitorSupport {

            override def visit(element: Element)    : Unit = checkNodeAndBreakIfFail(element)
            override def visit(attribute: Attribute): Unit = checkNodeAndBreakIfFail(attribute)

            def checkNodeAndBreakIfFail(node: Node): Unit =
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

    def addRootElementNamespace(doc: Document): Unit =
      doc.getRootElement.addNamespace(XXFORMS_NAMESPACE_SHORT.prefix, XXFORMS_NAMESPACE_SHORT.uri)
  }
}