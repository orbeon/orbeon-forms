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
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{IndentedLogger, Modifier}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis._
import org.orbeon.oxf.xforms.analysis.controls.{ActionTrait, RepeatIterationControl}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.{Perform, Propagate}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{EventNames, XFormsId}


/**
 * XForms (or just plain XML Events) event handler implementation.
 *
 * All event-related information gathered is immutable (the only temporarily mutable information is the base class's
 * XPath analysis, which is unused here).
 */
class EventHandler(
  part      : PartAnalysisImpl,
  index     : Int,
  element   : Element,
  parent    : Option[ElementAnalysis],
  preceding : Option[ElementAnalysis],
  scope     : Scope
) extends ElementAnalysis(
  part,
  index,
  element,
  parent,
  preceding,
  scope
) with ActionTrait {

  import EventHandler._

  // We check attributes in the `ev:*` or no namespace. We don't need to handle attributes in the `xbl:*` namespace.
  private def att   (name: QName)               : String         = element.attributeValue(name)
  private def attOpt(name: QName)               : Option[String] = element.attributeValueOpt(name)
  private def attOpt(name1: QName, name2: QName): Option[String] = attOpt(name1) orElse attOpt(name2)

  // These are only relevant when listening to keyboard events
  val keyText      : Option[String] = attOpt(XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME)
  val keyModifiers : Set[Modifier]  = parseKeyModifiers(attOpt(XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME))

  val eventNames: Set[String] = {

    val names =
      attSet(element, XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME) ++
        attSet(element, XML_EVENTS_EVENT_ATTRIBUTE_QNAME)

    // For backward compatibility, still support `keypress` even with modifiers, but translate that to `keydown`,
    // as modifiers require `keydown` in browsers.
    if (keyModifiers.nonEmpty)
      names map {
        case EventNames.KeyPress => EventNames.KeyDown
        case other               => other
      }
    else
      names
  }

  // NOTE: If #all is present, ignore all other specific events
  val (actualEventNames, isAllEvents) =
    if (eventNames(XXFORMS_ALL_EVENTS))
      (Set(XXFORMS_ALL_EVENTS), true)
    else
      (eventNames, false)

  val (
    isCapturePhaseOnly: Boolean,
    isTargetPhase     : Boolean,
    isBubblingPhase   : Boolean
  ) = {
    val phaseAsSet = attOpt(XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME, XML_EVENTS_PHASE_ATTRIBUTE_QNAME).toSet

    val capture  = phaseAsSet("capture")
    val target   = phaseAsSet.isEmpty || phaseAsSet.exists(TargetPhaseTestSet)
    val bubbling = phaseAsSet.isEmpty || phaseAsSet.exists(BubblingPhaseTestSet)

    (capture, target, bubbling)
  }

  val propagate: Propagate =
    if (attOpt(XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME, XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME) contains "stop")
      Propagate.Stop
    else
      Propagate.Continue

  val isPerformDefaultAction: Perform =
    if (attOpt(XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME, XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME) contains "cancel")
      Perform.Cancel
    else
      Perform.Perform

  val isPhantom             : Boolean = att(XXFORMS_EVENTS_PHANTOM_ATTRIBUTE_QNAME) == "true"
  val isIfNonRelevant       : Boolean = attOpt(XXFORMS_EVENTS_IF_NON_RELEVANT_ATTRIBUTE_QNAME) contains "true"
  val isXBLHandler          : Boolean = element.getQName == XBL_HANDLER_QNAME

  // Observers and targets

  // Temporarily mutable until after analyzeEventHandler() has run
  private var _observersPrefixedIds: Set[String] = _
  private var _targetPrefixedIds: Set[String] = _

  // Question: should we just point to the ElementAnalysis instead of using ids?
  def observersPrefixedIds: Set[String] = _observersPrefixedIds
  def targetPrefixedIds: Set[String] = _targetPrefixedIds

  // Analyze the handler
  def analyzeEventHandler(): Unit = {

    // This must run only once
    assert(_observersPrefixedIds eq null)
    assert(_targetPrefixedIds eq null)

    // Logging
    implicit val logger: IndentedLogger = part.getIndentedLogger

    def unknownTargetId(id: String) = {
      warn("unknown id", Seq("id" -> id))
      Set.empty[String]
    }

    def ignoringHandler(attName: String) = {
      warn(
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
        ! element.hasAttribute(TARGETID_QNAME)

    val targetTokens                = attSet(element, XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME) ++ (if (isDispatchActionNoTargetId) Set.empty else attSet(element, XML_EVENTS_TARGET_ATTRIBUTE_QNAME))
    val targetsPrefixedIdsAndHashes = targetTokens flatMap (targetHashResolver orElse staticIdResolver)

    val ignoreDueToTarget = targetTokens.nonEmpty && targetsPrefixedIdsAndHashes.isEmpty
    if (ignoreDueToTarget)
      ignoringHandler("target")

    if (ignoreDueToObservers || ignoreDueToTarget) {
      _observersPrefixedIds = Set.empty[String]
      _targetPrefixedIds    = Set.empty[String]
    } else {
      _observersPrefixedIds = observersPrefixedIds
      _targetPrefixedIds    = targetsPrefixedIdsAndHashes
    }
  }

  final def isMatchByName(eventName: String): Boolean =
    isAllEvents || eventNames(eventName)

  // Match if no target id is specified, or if any specified target matches
  private def isMatchTarget(targetPrefixedId: String) =
    targetPrefixedIds.isEmpty || targetPrefixedIds(targetPrefixedId)

  // Match both name and target
  final def isMatchByNameAndTarget(eventName: String, targetPrefixedId: String): Boolean =
    isMatchByName(eventName) && isMatchTarget(targetPrefixedId)
}

object EventHandler {

  import Private._

  // Special observer id indicating that the observer is the preceding sibling control
  val ObserverIsPrecedingSibling = "#preceding-sibling"

  // Special target id indicating that the target is the observer
  val TargetIsObserver = "#observer"

  private val TargetPhaseTestSet   = Set("target", "default")
  private val BubblingPhaseTestSet = Set("bubbling", "default")

  // Whether the element is an event handler (a known action element with @*:event)
  def isEventHandler(element: Element): Boolean =
    isAction(element.getQName) && element.hasAttribute(XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME.localName)

  def parseKeyModifiers(value: Option[String]): Set[Modifier] =
    value match {
      case Some(attValue) => Modifier.parseStringToSet(attValue)
      case None           => Set.empty[Modifier]
    }

  def isDispatchAction (qName: QName): Boolean = qName == DispatchAction
  def isAction         (qName: QName): Boolean = Actions(qName)
  def isContainerAction(qName: QName): Boolean = ContainerActions(qName)

  private object Private {

    val DispatchAction = xformsQName("dispatch")

    val ContainerActions =
      Set(
        xformsQName("action"),
        XBL_HANDLER_QNAME
      )

    val Actions =
      Set(
        // Standard XForms actions
        xformsQName("action"),
        DispatchAction,
        xformsQName("rebuild"),
        xformsQName("recalculate"),
        xformsQName("revalidate"),
        xformsQName("refresh"),
        xformsQName("setfocus"),
        xformsQName("load"),
        xformsQName("setvalue"),
        xformsQName("send"),
        xformsQName("reset"),
        xformsQName("message"),
        xformsQName("toggle"),
        xformsQName("insert"),
        xformsQName("delete"),
        xformsQName("setindex"),

        // Extension actions
        xxformsQName("script"),
        xxformsQName("show"),
        xxformsQName("hide"),
        xxformsQName("invalidate-instance"),
        xxformsQName("invalidate-instances"),
        xxformsQName("join-submissions"),
        xxformsQName("setvisited"),
        xxformsQName("update-validity"),

        // `xbl:handler` as action container working like `xf:action`
        XBL_HANDLER_QNAME
      )
  }
}
