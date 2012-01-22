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
package org.orbeon.oxf.xforms.event;

import events.XXFormsActionErrorEvent
import org.orbeon.oxf.xforms._
import action.{XFormsActions, XFormsAPI, XFormsActionInterpreter}
import analysis.ElementAnalysis._
import analysis.{ElementAnalysis, StaticStateContext, SimpleElementAnalysis}
import control.XFormsComponentControl
import org.orbeon.oxf.xforms.XFormsConstants._

import collection.JavaConverters._
import org.dom4j.{QName, Element}
import xbl.Scope

/**
 * XForms (or just plain XML Events) event handler implementation.
 *
 * All event-related information gathered is immutable (the only temporarily mutable information is the base class's
 * XPath analysis, which is unused here).
 */
class EventHandlerImpl(
        staticStateContext: StaticStateContext,
        element: Element,
        parent: Option[ElementAnalysis],
        preceding: Option[ElementAnalysis],
        scope: Scope)
    extends SimpleElementAnalysis(
        staticStateContext,
        element,
        parent,
        preceding,
        scope)
    with EventHandler {

    self ⇒

    // NOTE: We use attribute local names so that we can catch names in the ev:*, xbl:*, or default namespace
    private def att(name: String): String = element.attributeValue(name)
    private def attOption(name: String): Option[String] = Option(att(name))

    val eventNames = attSet(element, XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME.getName)

    // NOTE: If #all is present, ignore all other specific events
    val (actualEventNames, isAllEvents) =
        if (eventNames(XXFORMS_ALL_EVENTS))
            (Set(XXFORMS_ALL_EVENTS), true)
        else
            (eventNames, false)

    private val phaseAtt = att(XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME.getName) // Q: is this going to be eliminated as a field?

    val isCapturePhaseOnly = phaseAtt == "capture"
    val isTargetPhase = (phaseAtt eq null) || Set("target", "default")(phaseAtt)
    val isBubblingPhase = (phaseAtt eq null) || Set("bubbling", "default")(phaseAtt)
    val isPropagate = att(XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME.getName) != "stop"                    // "true" means "continue", "false" means "stop"
    val isPerformDefaultAction = att(XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME.getName) != "cancel"  // "true" means "perform", "false" means "cancel"

    val keyModifiers = attOption(XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME.getName)
    val keyText = attOption(XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME.getName)

    // For Java callers
    def getKeyModifiers = keyModifiers.orNull
    def getKeyText = keyText.orNull

    val isXBLHandler = element.getQName == XBL_HANDLER_QNAME

    // Observers and targets

    // Temporarily mutable until after analyzeEventHandler() has run 
    private var _observersPrefixedIds: Set[String] = _
    private var _targetPrefixedIds: Set[String] = _

    // Question: should we just point to the ElementAnalysis instead of using ids?
    def observersPrefixedIds = _observersPrefixedIds
    def targetPrefixedIds = _targetPrefixedIds

    // Analyze the handler
    def analyzeEventHandler() {
        
        // This must run only once
        assert(_observersPrefixedIds eq null)
        assert(_targetPrefixedIds eq null)
        
        // Extract unique prefixed ids given an attribute name
        // NOTE: Supporting space-separated observer/target ids is an extension, which may make it into XML Events 2
        // TODO: error or warn if scope.prefixedIdForStaticId(id) eq null? or just ignore?
        def prefixedIds(name: String) = attSet(element, name) collect {
            case id if id.startsWith("#") ⇒ id
            case id if scope.prefixedIdForStaticId(id) ne null ⇒ scope.prefixedIdForStaticId(id)
        }

        def prefixedIdsByQName(name: QName) = attSet(element, name) collect {
            case id if id.startsWith("#") ⇒ id
            case id if scope.prefixedIdForStaticId(id) ne null ⇒ scope.prefixedIdForStaticId(id)
        }

        val observersPrefixedIds = prefixedIds(XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME.getName)
        
        // Handle backward compatibility for <dispatch ev:event="…" ev:target="…" name="…" target="…">. In this case,
        // if the user didn't specify the `targetid` attribute, the meaning of the `target` attribute in no namespace is
        // the target of the dispatch action, not the incoming XML Events target. In this case to specify the incoming
        // XML Events target, the attribute must be qualified as `ev:target`.
        val targetsPrefixedIds = 
            if (XFormsActions.isDispatchAction(element.getQName) && (element.attribute(TARGET_QNAME) ne null) && (element.attribute(TARGETID_QNAME) eq null))
                prefixedIdsByQName(XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME)
            else
                prefixedIds(XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME.getName)

        _observersPrefixedIds =
            if (observersPrefixedIds.nonEmpty)
                observersPrefixedIds
            else
                parent collect { case parent: ElementAnalysis ⇒ Set(parent.prefixedId) } getOrElse Set()

        _targetPrefixedIds = {
            // Special target id indicating that the target is the observer
            val targetIsObserver = "#observer"

            if (targetsPrefixedIds(targetIsObserver))
                targetsPrefixedIds - targetIsObserver ++ _observersPrefixedIds
            else
                targetsPrefixedIds
        }
    }

    /**
     * Process the event on the given observer.
     */
    def handleEvent(containingDocument: XFormsContainingDocument, eventObserver: XFormsEventObserver, event: XFormsEvent) {
        
        assert(_observersPrefixedIds ne null)
        assert(_targetPrefixedIds ne null)

        // Find dynamic context within which the event handler runs
        val (container, handlerEffectiveId, xpathContext) =
            if (isXBLHandler) {
                // Observer is the XBL component itself but from the inside
                eventObserver match {
                    case componentControl: XFormsComponentControl ⇒

                        val xblContainer = componentControl.getNestedContainer
                        xblContainer.getContextStack.resetBindingContext()
                        val stack = new XFormsContextStack(xblContainer, xblContainer.getContextStack.getCurrentBindingContext)

                        val handlerEffectiveId = xblContainer.getFullPrefix + staticId + XFormsUtils.getEffectiveIdSuffixWithSeparator(componentControl.getEffectiveId)

                        (xblContainer, handlerEffectiveId, stack)
                    case _ ⇒
                        throw new IllegalStateException
                }
            } else {
                // Regular observer
                // The handler is either within controls (XFormsActionControl) or within a model (XFormsModelAction)

                def findEventHandler(o: AnyRef) = o match {
                    case handler: XFormsEventHandler ⇒
                        val handlerContainer = handler.getXBLContainer(containingDocument)
                        val handlerEffectiveId = handler.getEffectiveId
                        val stack =  new XFormsContextStack(handlerContainer, handler.getBindingContext(containingDocument))

                        (handlerContainer, handlerEffectiveId, stack)
                    case _ ⇒
                        throw new IllegalStateException
                }

                if (eventObserver.getScope(containingDocument) == scope) {
                    // The scopes match so we can resolve the id relative to the observer
                    findEventHandler(eventObserver.getXBLContainer(containingDocument).resolveObjectByIdInScope(eventObserver.getEffectiveId, staticId, null))
                } else if (Some(eventObserver.getPrefixedId) == (parent map (_.prefixedId))) {
                    // Scopes don't match which implies that the event handler must be a child of the observer
                    val handlerEffectiveId = XFormsUtils.getRelatedEffectiveId(eventObserver.getEffectiveId, staticId)
                    findEventHandler(containingDocument.getObjectByEffectiveId(handlerEffectiveId))
                } else
                    throw new IllegalStateException
            }

        // Run the action within the context
        try {
            // Push binding for outermost action
            xpathContext.pushBinding(element, handlerEffectiveId, scope, false)

            val actionInterpreter = new XFormsActionInterpreter(container, xpathContext, element, handlerEffectiveId, event, eventObserver)
            XFormsAPI.withScalaAction(actionInterpreter) {
                actionInterpreter.runAction(self)
            }
        } catch {
            case e: Exception ⇒
                // Something bad happened while running the action
                // NOTE: Dispatch directly to the containing document. Ideally it would bubble and a default listener on the document would handle it.
                container.dispatchEvent(new XXFormsActionErrorEvent(container.getContainingDocument, container.getContainingDocument, e))
        }
    }

    def jObserversPrefixedIds = observersPrefixedIds.asJava

    def isMatchEventName(eventName: String) =
        isAllEvents || eventNames(eventName)

    // Match if no target id is specified, or if any specified target matches
    private def isMatchTarget(targetPrefixedId: String) =
        targetPrefixedIds.isEmpty || targetPrefixedIds(targetPrefixedId)

    def isMatch(event: XFormsEvent) =
            isMatchEventName(event.getName) &&
            isMatchTarget(XFormsUtils.getPrefixedId(event.getTargetObject.getEffectiveId)) &&
            event.matches(self)
}

object EventHandlerImpl {
    // Whether the element is an event handler (a known action element with @*:event)
    def isEventHandler(element: Element) =
        XFormsActions.isAction(element.getQName) && (element.attribute(XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME.getName) ne null)
}