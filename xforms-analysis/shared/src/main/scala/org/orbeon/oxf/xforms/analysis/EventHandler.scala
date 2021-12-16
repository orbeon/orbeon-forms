/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.Logging.debug
import org.orbeon.oxf.util.{IndentedLogger, Modifier}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis.attSet
import org.orbeon.oxf.xforms.analysis.EventHandler.{ObserverIsPrecedingSibling, TargetIsObserver, isDispatchAction}
import org.orbeon.oxf.xforms.analysis.controls.{ActionTrait, RepeatIterationControl}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.{Perform, Propagate}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping


/**
 * XForms (or just plain XML Events) event handler implementation.
 *
 * All event-related information gathered is immutable (the only temporarily mutable information is the base class's
 * XPath analysis, which is unused here).
 */
class EventHandler(
  index             : Int,
  element           : Element,
  parent            : Option[ElementAnalysis],
  preceding         : Option[ElementAnalysis],
  staticId          : String,
  prefixedId        : String,
  namespaceMapping  : NamespaceMapping,
  scope             : Scope,
  containerScope    : Scope,

  val keyText               : Option[String],
  val keyModifiers          : Set[Modifier],
  val eventNames            : Set[String],
  //c.actualEventNames // unused
  val isAllEvents           : Boolean,
  val isCapturePhaseOnly    : Boolean,
  val isTargetPhase         : Boolean,
  val isBubblingPhase       : Boolean,
  val propagate             : Propagate,
  val isPerformDefaultAction: Perform,
  val isPhantom             : Boolean,
  val isIfNonRelevant       : Boolean,
  val isXBLHandler          : Boolean
) extends ElementAnalysis(
  index,
  element,
  parent,
  preceding,
  staticId,
  prefixedId,
  namespaceMapping,
  scope,
  containerScope
) with ActionTrait {

  // Set later via `analyzeEventHandler()` or deserialization
  var observersPrefixedIds: Set[String] = _ // Q: should we just point to the ElementAnalysis instead of using ids?
  var targetPrefixedIds   : Set[String] = _ // Q: should we just point to the ElementAnalysis instead of using ids?

  final def isMatchByName(eventName: String): Boolean =
    isAllEvents || eventNames(eventName)

  // Match if no target id is specified, or if any specified target matches
  private def isMatchTarget(targetPrefixedId: String) =
    targetPrefixedIds.isEmpty || targetPrefixedIds(targetPrefixedId)

  // Match both name and target
  final def isMatchByNameAndTarget(eventName: String, targetPrefixedId: String): Boolean =
    isMatchByName(eventName) && isMatchTarget(targetPrefixedId)

      // Analyze the handle, setting:
    // - `observersPrefixedIds`
    // - `targetPrefixedIds`
  def analyzeEventHandler()(implicit logger: IndentedLogger): Unit = {

    def unknownTargetId(id: String) = {
      debug("unknown id", List("id" -> id))
      Set.empty[String]
    }

    def ignoringHandler(attName: String) = {
      debug(
        s"`$attName` attribute present but does not refer to at least one valid id, ignoring event handler",
        List("element" -> element.toDebugString)
      )
      Set.empty[String]
    }

    // Resolver for tokens
    type TokenResolver = PartialFunction[String, Set[String]]

    val staticIdResolver: TokenResolver = {
      case id =>
        val prefixedId = scope.prefixedIdForStaticId(id)
        if (prefixedId ne null)
          Set(prefixedId)
        else
          unknownTargetId(id)
    }

    // 1. Resolve observer(s)

    // Resolve a token starting with a hash (#)
    val observerHashResolver: TokenResolver = {
      case ObserverIsPrecedingSibling =>
        preceding match {
          case Some(p) => Set(p.prefixedId)
          case None    => unknownTargetId(ObserverIsPrecedingSibling)
        }
    }

    // Support `ev:observer` and plain `observer`
    // NOTE: Supporting space-separated observer/target ids is an extension, which may make it into XML Events 2
    val observersTokens               = attSet(element, XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME) ++ attSet(element, XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME)
    val observersPrefixedIdsAndHashes = observersTokens flatMap (observerHashResolver orElse staticIdResolver)

    val ignoreDueToObservers = observersTokens.nonEmpty && observersPrefixedIdsAndHashes.isEmpty

    val observersPrefixedIds =
      if (ignoreDueToObservers)
        ignoringHandler("observer")
      else {
        if (observersPrefixedIdsAndHashes.nonEmpty)
          observersPrefixedIdsAndHashes
        else
          parent collect {
            case iteration: RepeatIterationControl =>
              // Case where the handler doesn't have an explicit observer and is within a repeat
              // iteration. As of 2012-05-18, the handler observes the enclosing repeat container.
              Set(iteration.parent.get.prefixedId)
            case parent: ElementAnalysis =>
              // Case where the handler doesn't have an explicit observer. It observes its parent.
              Set(parent.prefixedId)
          } getOrElse Set.empty[String]
      }

    // 2. Resolve target(s)

    // Resolve a token starting with a hash (#)
    val targetHashResolver: TokenResolver = {
      case TargetIsObserver => observersPrefixedIds
    }

    // Handle backward compatibility for <dispatch ev:event="…" ev:target="…" name="…" target="…">. In this case,
    // if the user didn't specify the `targetid` attribute, the meaning of the `target` attribute in no namespace is
    // the target of the dispatch action, not the incoming XML Events target. In this case to specify the incoming
    // XML Events target, the attribute must be qualified as `ev:target`.
    val isDispatchActionNoTargetId =
      isDispatchAction(element.getQName)   &&
        element.hasAttribute(TARGET_QNAME) &&
        (! element.hasAttribute(TARGETID_QNAME))

    val targetTokens                = attSet(element, XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME) ++ (if (isDispatchActionNoTargetId) Set.empty else attSet(element, XML_EVENTS_TARGET_ATTRIBUTE_QNAME))
    val targetsPrefixedIdsAndHashes = targetTokens flatMap (targetHashResolver orElse staticIdResolver)

    val ignoreDueToTarget = targetTokens.nonEmpty && targetsPrefixedIdsAndHashes.isEmpty
    if (ignoreDueToTarget)
      ignoringHandler("target")

    if (ignoreDueToObservers || ignoreDueToTarget) {
      this.observersPrefixedIds = Set.empty
      this.targetPrefixedIds    = Set.empty
    } else {
      this.observersPrefixedIds = observersPrefixedIds
      this.targetPrefixedIds    = targetsPrefixedIdsAndHashes
    }
  }
}

object EventHandler {

  import Private._

  val PropertyQNames = Set(XFORMS_PROPERTY_QNAME, XXFORMS_CONTEXT_QNAME)

  // Special observer id indicating that the observer is the preceding sibling control
  val ObserverIsPrecedingSibling = "#preceding-sibling"

  // Special target id indicating that the target is the observer
  val TargetIsObserver = "#observer"

  // Whether the element is an event handler (a known action element with @*:event)
  def isEventHandler(element: Element): Boolean =
    isAction(element.getQName) && element.hasAttribute(XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME.localName)

  def parseKeyModifiers(value: Option[String]): Set[Modifier] =
    value match {
      case Some(attValue) => Modifier.parseStringToSet(attValue)
      case None           => Set.empty[Modifier]
    }

  def isDispatchAction  (qName: QName): Boolean = qName == DispatchAction
  def isAction          (qName: QName): Boolean = Actions(qName)
  def isContainerAction (qName: QName): Boolean = ContainerActions(qName)
  def isPropertiesAction(qName: QName): Boolean = PropertiesActions(qName)

  private object Private {

    val DispatchAction = xformsQName("dispatch")

    // For nested `<xf:property>`
    val PropertiesActions =
      Set(
        DispatchAction,
        xformsQName("send"),
        xxformsQName("show"),
        xxformsQName("hide"),
      )

    val ContainerActions =
      Set(
        xformsQName("action"),
        XBL_HANDLER_QNAME,    // `xbl:handler` as action container working like `xf:action`
      ) ++
        PropertiesActions

    val Actions =
      ContainerActions ++
        List(
          // Standard XForms actions
          xformsQName("rebuild"),
          xformsQName("recalculate"),
          xformsQName("revalidate"),
          xformsQName("refresh"),
          xformsQName("setfocus"),
          xformsQName("load"),
          xformsQName("setvalue"),
          xformsQName("reset"),
          xformsQName("message"),
          xformsQName("toggle"),
          xformsQName("insert"),
          xformsQName("delete"),
          xformsQName("setindex"),

          // Extension actions
          xxformsQName("script"),
          xxformsQName("invalidate-instance"),
          xxformsQName("invalidate-instances"),
          xxformsQName("join-submissions"),
          xxformsQName("setvisited"),
          xxformsQName("update-validity")
        )
  }
}
