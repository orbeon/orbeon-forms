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
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.processor.converter.XHTMLRewrite
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.ChildrenBuilderTrait
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.controls.XFormsActionControl
import org.orbeon.oxf.xforms.event.XFormsEventObserver
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.ForwardingXMLReceiver
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.oxf.xml.dom4j.LocationData
import org.xml.sax.Attributes
import scala.collection.Seq
import scala.collection.JavaConverters._
import org.orbeon.oxf.xforms.BindingContext
import org.dom4j.{QName, Element}
import org.orbeon.saxon.om.Item
import org.orbeon.oxf.xforms.analysis.controls.{RepeatControl, SingleNodeTrait, AppearanceTrait}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.util.ScalaUtils._
import Controls._

/**
 * Represents an XForms control.
 *
 * The implementation is split into a series of traits to make each chunk more palatable.
 */
class XFormsControl(
        val container: XBLContainer,
        var parent: XFormsControl,      // var just so we can null it upon clone
        val element: Element,
        var effectiveId: String)        // var because can be updated upon iteration change
    extends ControlXPathSupport
    with ControlAjaxSupport
    with ControlLHHASupport
    with ControlLocalSupport
    with ControlExtensionAttributesSupport
    with ControlEventSupport
    with ControlBindingSupport
    with ControlXMLDumpSupport
    with XFormsEventTarget
    with XFormsEventObserver
    with ExternalCopyable {

    // Type of the associated static control
    type Control <: ElementAnalysis // TODO: Use more specific to represent controls. Note that those can also be actions.

    require(container ne null)

    final val containingDocument = container.getContainingDocument

    // Static information (never changes for the lifetime of the containing document)
    // TODO: Pass staticControl during construction (find which callers don't pass the necessary information)
    final val staticControl: Control = container.getPartAnalysis.getControlAnalysis(XFormsUtils.getPrefixedId(effectiveId)).asInstanceOf[Control]

    final val prefixedId = Option(staticControl) map (_.prefixedId) getOrElse XFormsUtils.getPrefixedId(effectiveId)

    final def stateToRestore     = restoringControl(effectiveId)
    final def stateToRestoreJava = stateToRestore.orNull

    // Whether the control has been visited
    def visited = false

    parent match {
        case container: XFormsContainerControl ⇒ container.addChild(this)
        case _ ⇒
    }

    final def getId = staticControl.staticId
    final def getPrefixedId = prefixedId

    final def scope = staticControl.scope
    final def localName = staticControl.localName

    def getContextStack = container.getContextStack
    final def getIndentedLogger = containingDocument.getControls.getIndentedLogger

    final def getResolutionScope =
        container.getPartAnalysis.scopeForPrefixedId(prefixedId)

    // Resolve an object relative to this control
    final def resolve(staticId: String, contextItem: Item = null) =
        Option(container.resolveObjectByIdInScope(getEffectiveId, staticId, contextItem))

    final def getChildElementScope(element: Element) =
        container.getPartAnalysis.scopeForPrefixedId(container.getFullPrefix + XFormsUtils.getElementId(element))

    // Update this control's effective id based on the parent's effective id
    def updateEffectiveId() {
        if (staticControl.isWithinRepeat) {
            val parentEffectiveId = parent.getEffectiveId
            val parentSuffix = XFormsUtils.getEffectiveIdSuffix(parentEffectiveId)
            effectiveId = XFormsUtils.getPrefixedId(effectiveId) + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + parentSuffix
            if (_childrenActions.nonEmpty)
                for (actionControl ← _childrenActions)
                    actionControl.updateEffectiveId()
        }
    }

    def getEffectiveId = effectiveId

    // Used by repeat iterations
    def setEffectiveId(effectiveId: String) =
        this.effectiveId = effectiveId

    final def getLocationData =
        if (staticControl ne null) staticControl.locationData else if (element ne null) element.getData.asInstanceOf[LocationData] else null

    // Semi-dynamic information (depends on the tree of controls, but does not change over time)
    private var _childrenActions: List[XFormsActionControl] = Nil

    // Dynamic information (changes depending on the content of XForms instances)
    private var previousEffectiveId: String = null

    // NOP, can be overridden
    def iterationRemoved(): Unit = ()

    // Preceding control if any
    def preceding: Option[XFormsControl] = {
        // Unlike ElementAnalysis we don't store a `preceding` pointer so we have to search for it
        val siblingsOrSelf = parent.asInstanceOf[XFormsContainerControl].children
        val index          = siblingsOrSelf indexWhere (this eq)
        
        index > 0 option siblingsOrSelf(index - 1)
    }

    // NOTE: As of 2011-11-22, this is here so that effective ids of nested actions can be updated when iterations
    // are moved around. Ideally anyway, iterations should not require effective id changes. An iteration tree should
    // probably have a uuid, and updating it should be done in constant time.
    // As of 2012-02-23, this is also here so that binding updates can take place on nested actions. However this too is
    // not really necessary, as an action's binding is just a copy of its parent binding as we don't want to needlessly
    // evaluate the actual binding before the action runs. But we have this so that the setBindingContext,
    // bindingContextForChild, bindingContextForFollowing operations work on nested actions too.
    // Possibly we could make *any* control able to have nested content. This would make sense from a class hierarchy
    // perspective.
    final def addChildAction(actionControl: XFormsActionControl): Unit =
        _childrenActions ::= actionControl

    final def childrenActions = _childrenActions
    final def getChildrenActions = _childrenActions.asJava

    final def previousEffectiveIdCommit() = {
        val result = previousEffectiveId
        previousEffectiveId = effectiveId
        result
    }

    def commitCurrentUIState() {
        wasRelevantCommit()
        previousEffectiveIdCommit()
    }

    final def appearances    = XFormsControl.appearances(staticControl)
    final def getAppearances = XFormsControl.jAppearances(staticControl)
    def isStaticReadonly = false

    // Optional mediatype
    final def mediatype = staticControl match {
        case appearanceTrait: AppearanceTrait ⇒ appearanceTrait.mediatype
        case _ ⇒ None
    }

    def getJavaScriptInitialization: (String, String, String) = null

    final def getCommonJavaScriptInitialization = {
        val appearances = getAppearances
        // First appearance only (should probably handle all of them, but historically only one appearance was handled)
        val firstAppearance = if (! appearances.isEmpty) Some(Dom4jUtils.qNameToExplodedQName(appearances.iterator.next)) else None
        (localName, firstAppearance orElse mediatype orNull, getEffectiveId)
    }

    // Compare this control with another control, as far as the comparison is relevant for the external world.
    def equalsExternal(other: XFormsControl) =
        other match {
            case other if this eq other ⇒ true
            case other: XFormsControl ⇒
                isRelevant == other.isRelevant &&
                compareLHHA(other) &&
                compareExtensionAttributes(other)
            case _ ⇒ false
        }

    // Evaluate the control's value and metadata
    final def evaluate(): Unit =
        try evaluateImpl(relevant = true, parentRelevant = true)
        catch {
            case e: ValidationException ⇒ {
                throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData, "evaluating control", element, "element", Dom4jUtils.elementToDebugString(element)))
            }
        }

    // Called to clear the control's values when the control becomes non-relevant
    final def evaluateNonRelevant(parentRelevant: Boolean): Unit = {
        evaluateNonRelevantLHHA()
        evaluateNonRelevantExtensionAttribute()
        evaluateImpl(relevant = false, parentRelevant = parentRelevant)
    }

    // Notify the control that some of its aspects (value, label, etc.) might have changed and require re-evaluation. It
    // is left to the control to figure out if this can be optimized.
    def markDirtyImpl(): Unit = {
        markLHHADirty()
        markExtensionAttributesDirty()
    }

    // Evaluate this control
    // NOTE: LHHA and extension attributes are computed lazily
    def evaluateImpl(relevant: Boolean, parentRelevant: Boolean) = ()

    /**
     * Clone a control. It is important to understand why this is implemented: to create a copy of a tree of controls
     * before updates that may change control bindings. Also, it is important to understand that we clone "back", that
     * is the new clone will be used as the reference copy for the difference engine.
     */
    def getBackCopy: AnyRef = {
        // NOTE: this.parent is handled by subclasses
        val cloned = super.clone.asInstanceOf[XFormsControl]

        updateLHHACopy(cloned)
        updateLocalCopy(cloned)

        cloned
    }

    // Whether focus can be set to this control
    def isFocusable = false

    // Set the focus on this control and return true iif control accepted focus
    // By default, a control doesn't accept focus
    def setFocus(inputOnly: Boolean) = false

    // Build children controls if any, delegating the actual construction to the given `buildTree` function
    def buildChildren(buildTree: (XBLContainer, BindingContext, ElementAnalysis, Seq[Int]) ⇒ Option[XFormsControl], idSuffix: Seq[Int]) =
        staticControl match {
            case withChildren: ChildrenBuilderTrait ⇒ Controls.buildChildren(this, withChildren.children, buildTree, idSuffix)
            case _ ⇒
        }
}

object XFormsControl {

    def controlSupportsRefreshEvents(control: XFormsControl) =
        (control ne null) && control.supportsRefreshEvents

    // Whether the given item is allowed as a binding item for the given control
    // TODO: don't like pattern matching here and revisit hierarchy
    def isAllowedBoundItem(control: XFormsControl, item: Item) = control.staticControl match {
        case singleNode: SingleNodeTrait ⇒ singleNode.isAllowedBoundItem(item)
        case repeat: RepeatControl ⇒ DataModel.isAllowedBoundItem(item)
        case _ ⇒ false
    }

    // Rewrite an HTML value which may contain URLs, for example in @src or @href attributes. Also deals with closing element tags.
    def getEscapedHTMLValue(locationData: LocationData, rawValue: String): String = {

        if (rawValue eq null)
            return null

        val sb = new StringBuilder(rawValue.length * 2) // just an approx of the size it may take
        // NOTE: we do our own serialization here, but it's really simple (no namespaces) and probably reasonably efficient
        val rewriter = NetUtils.getExternalContext.getResponse
        XFormsUtils.streamHTMLFragment(new XHTMLRewrite().getRewriteXMLReceiver(rewriter, new ForwardingXMLReceiver {

            private var isStartElement = false

            override def characters(chars: Array[Char], start: Int, length: Int) {
                sb.append(XMLUtils.escapeXMLMinimal(new String(chars, start, length))) // NOTE: not efficient to create a new String here
                isStartElement = false
            }

            override def startElement(uri: String, localname: String, qName: String, attributes: Attributes) {
                sb.append('<')
                sb.append(localname)
                val attributeCount = attributes.getLength

                for (i ← 0 to attributeCount -1) {
                    val currentName = attributes.getLocalName(i)
                    val currentValue = attributes.getValue(i)
                    sb.append(' ')
                    sb.append(currentName)
                    sb.append("=\"")
                    sb.append(currentValue)
                    sb.append('"')
                }

                sb.append('>')
                isStartElement = true
            }

            override def endElement(uri: String, localname: String, qName: String) {
                if (! isStartElement || ! XFormsUtils.isVoidElement(localname)) {
                    // We serialize to HTML: don't close elements that just opened (will cover <br>, <hr>, etc.). Be sure not to drop closing elements of other tags though!
                    sb.append("</")
                    sb.append(localname)
                    sb.append('>')
                }
                isStartElement = false
            }

        }, true), rawValue, locationData, "xhtml")

        sb.toString
    }

    // Base trait for a control property (label, itemset, etc.)
    trait ControlProperty[T >: Null] {
        def value(): T
        def handleMarkDirty()
        def copy: ControlProperty[T]
    }

    // Immutable control property
    class ImmutableControlProperty[T >: Null](val value: T) extends ControlProperty[T] {
        override def handleMarkDirty() = ()
        override def copy = this
    }

    // Mutable control property supporting optimization
    trait MutableControlProperty[T >: Null] extends ControlProperty[T] with Cloneable {

        private var _value: T = null
        private var isEvaluated = false
        private var isOptimized = false

        protected def isRelevant: Boolean
        protected def wasRelevant: Boolean
        protected def requireUpdate: Boolean
        protected def notifyCompute()
        protected def notifyOptimized()

        protected def evaluateValue(): T
        protected def nonRelevantValue: T = null

        def value(): T = {// NOTE: making this method final produces an AbstractMethodError with Java 5 (ok with Java 6)
            if (! isEvaluated) {
                _value =
                    if (isRelevant) {
                        notifyCompute()
                        evaluateValue()
                    } else {
                        // NOTE: if the control is not relevant, nobody should ask about this in the first place
                        // In practice, this can be called as of 2012-06-20
                        nonRelevantValue
                    }
                isEvaluated = true
            } else if (isOptimized) {
                // This is only for statistics: if the value was not re-evaluated because of the dependency engine
                // giving us the green light, the first time the value is asked we notify the dependency engine of that
                // situation.
                notifyOptimized()
                isOptimized = false
            }

            _value
        }

        def handleMarkDirty() {

            def isDirty = ! isEvaluated
            def markOptimized() = isOptimized = true

            if (! isDirty) {
                // don't do anything if we are already dirty
                if (isRelevant != wasRelevant) {
                    // Control becomes relevant or non-relevant
                    markDirty()
                } else if (isRelevant) {
                    // Control remains relevant
                    if (requireUpdate)
                        markDirty()
                    else
                        markOptimized() // for statistics only
                }
            }
        }

        protected def markDirty() {
            _value = null
            isEvaluated = false
            isOptimized = false
        }

        def copy = super.clone.asInstanceOf[MutableControlProperty[T]]
    }

    // Return the set of appearances for the given element, if any
    def appearances(elementAnalysis: ElementAnalysis) = elementAnalysis match {
        case appearanceTrait: AppearanceTrait ⇒ appearanceTrait.appearances
        case _                                ⇒ Set.empty[QName]
    }

    def jAppearances(elementAnalysis: ElementAnalysis) = appearances(elementAnalysis).asJava

    // Whether the given control has the text/html mediatype
    private val HTMLMediatype = Some("text/html")
    def isHTMLMediatype(control: XFormsControl) = control.mediatype == HTMLMediatype
}
