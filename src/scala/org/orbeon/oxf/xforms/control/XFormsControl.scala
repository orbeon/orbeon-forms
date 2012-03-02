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

import org.dom4j.Element
import org.dom4j.QName
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.processor.converter.XHTMLRewrite
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms._
import control.Controls.AncestorIterator
import event.events.{XFormsHelpEvent, XXFormsBindingErrorEvent, XFormsFocusEvent, XXFormsRepeatFocusEvent}
import org.orbeon.oxf.xforms.analysis.ChildrenBuilderTrait
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.XPathDependencies
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.controls.XFormsActionControl
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEventObserver
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xml.ForwardingXMLReceiver
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.oxf.xml.dom4j.LocationData
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import scala.Option
import scala.collection.Seq
import scala.collection.JavaConverters._
import org.orbeon.oxf.xforms.BindingContext
import XFormsControl._
import org.apache.commons.lang.StringUtils
import collection.mutable.LinkedHashSet
import org.orbeon.oxf.xforms.XFormsConstants.LHHA
import java.util.{Collections, LinkedList, List ⇒ JList, Map ⇒ JMap, HashMap ⇒ JHashMap}
import org.orbeon.oxf.xml.XMLUtils.DebugXML
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.saxon.om.{NodeInfo, Item, ValueRepresentation}

/**
 * Represents an XForms control.
 */
class XFormsControl(
        val container: XBLContainer,
        var parent: XFormsControl,
        val element: Element,
        var effectiveId: String
    ) extends XPathSupport with AjaxSupport with LHHASupport with LocalSupport with ExtensionAttributesSupport
         with EventSupport with BindingSupport with XFormsEventTarget with XFormsEventObserver with ExternalCopyable with XMLDumpSupport {

    // Static information (never changes for the lifetime of the containing document)
    // TODO: Pass staticControl during construction (find which callers don't pass the necessary information)
    private val _staticControl = Option(container) map (_.getPartAnalysis.getControlAnalysis(XFormsUtils.getPrefixedId(effectiveId))) orNull
    def staticControl = _staticControl
    final val containingDocument = Option(container) map (_.getContainingDocument) orNull

    final val prefixedId = Option(staticControl) map (_.prefixedId) getOrElse XFormsUtils.getPrefixedId(effectiveId)
    final val _element = Option(staticControl) map (_.element) getOrElse element

    parent match {
        case container: XFormsContainerControl ⇒ container.addChild(this)
        case _ ⇒
    }

    final def getId = staticControl.staticId
    final def getPrefixedId = prefixedId

    def getScope(containingDocument: XFormsContainingDocument) = staticControl.scope
    final def getXBLContainer(containingDocument: XFormsContainingDocument) = container

    final def getName = staticControl.localName
    final def getControlElement = element

    // For cloning only!
    def setParent(parent: XFormsControl) = this.parent = parent

    def getContextStack = container.getContextStack
    def getIndentedLogger = containingDocument.getControls.getIndentedLogger

    final def getResolutionScope =
        container.getPartAnalysis.scopeForPrefixedId(prefixedId)

    def getChildElementScope(element: Element) =
        container.getPartAnalysis.scopeForPrefixedId(container.getFullPrefix + XFormsUtils.getElementId(element))

    // Update this control's effective id based on the parent's effective id
    def updateEffectiveId() {
        if (staticControl.isWithinRepeat) {
            val parentEffectiveId = parent.getEffectiveId
            val parentSuffix = XFormsUtils.getEffectiveIdSuffix(parentEffectiveId)
            effectiveId = XFormsUtils.getPrefixedId(effectiveId) + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + parentSuffix
            if (childrenActions ne null) {
                for (actionControl ← childrenActions.asScala)
                    actionControl.updateEffectiveId()
            }
        }
    }

    def getEffectiveId = effectiveId

    // Used by repeat iterations
    def setEffectiveId(effectiveId: String) =
        this.effectiveId = effectiveId

    def getLocationData =
        if (staticControl ne null) staticControl.locationData else if (element ne null) element.getData.asInstanceOf[LocationData] else null

    // TODO: from staticControl
    private var mediatype: String = null // could become more dynamic in the future

    // Semi-dynamic information (depends on the tree of controls, but does not change over time)
    private var childrenActions: JList[XFormsActionControl] = null

    // Dynamic information (changes depending on the content of XForms instances)
    private var previousEffectiveId: String = null

    // NOP, can be overridden
    def iterationRemoved(): Unit = ()

    // NOTE: As of 2011-11-22, this is here so that effective ids of nested actions can be updated when iterations
    // are moved around. Ideally anyway, iterations should not require effective id changes. An iteration tree should
    // probably have a uuid, and updating it should be done in constant time.
    // As of 2012-02-23, this is also here so that binding updates can take place on nested actions. However this too is
    // not really necessary, as an action's binding is just a copy of its parent binding as we don't want to needlessly
    // evaluate the actual binding before the action runs. But we have this so that the setBindingContext,
    // bindingContextForChild, bindingContextForFollowing operations work on nested actions too.
    // Possibly we could make *any* control able to have nested content. This would make sense from a class hierarchy
    // perspective.
    def addChildAction(actionControl: XFormsActionControl) {
        if (childrenActions == null)
            childrenActions = new LinkedList[XFormsActionControl]
        childrenActions.add(actionControl)
    }

    def getChildrenActions =
        if (childrenActions ne null) childrenActions else Collections.emptyList[XFormsActionControl]

    def previousEffectiveIdCommit() = {
        val result = previousEffectiveId
        previousEffectiveId = effectiveId
        result
    }

    def commitCurrentUIState() {
        wasRelevantCommit()
        previousEffectiveIdCommit()
    }

    def getAppearances = Controls.appearances(staticControl)
    def isStaticReadonly = false

    def getMediatype = {
        if (mediatype eq null)
            mediatype = getControlElement.attributeValue(XFormsConstants.MEDIATYPE_QNAME)
        mediatype
    }

    def getJavaScriptInitialization: (String, String, String) = null

    def getCommonJavaScriptInitialization = {
        val appearances = getAppearances
        // First appearance only (should probably handle all of them, but historically only one appearance was handled)
        val appearance = if (appearances.size > 0) Dom4jUtils.qNameToExplodedQName(appearances.iterator.next) else null
        (getName, Option(appearance) getOrElse getMediatype, getEffectiveId)
    }

    // Compare this control with another control, as far as the comparison is relevant for the external world.
    def equalsExternal(other: XFormsControl): Boolean = {
        if (other eq null)
            return false

        if (this eq other)
            return true

        compareRelevance(other) && compareLHHA(other) && compareExtensionAttributes(other)
    }

    final def evaluate() {
        try evaluateImpl()
        catch {
            case e: ValidationException ⇒ {
                throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData, "evaluating control", getControlElement, "element", Dom4jUtils.elementToDebugString(getControlElement)))
            }
        }
    }

    // Notify the control that some of its aspects (value, label, etc.) might have changed and require re-evaluation. It
    // is left to the control to figure out if this can be optimized.
    def markDirtyImpl(xpathDependencies: XPathDependencies) {
        markLHHADirty()
        markExtensionAttributesDirty()
    }

    // Evaluate this control.
    // TODO: move this method to XFormsValueControl and XFormsValueContainerControl?
    def evaluateImpl() {
        // TODO: these should be evaluated lazily
        // Evaluate standard extension attributes
        evaluateExtensionAttributes(STANDARD_EXTENSION_ATTRIBUTES)

        // Evaluate custom extension attributes
        Option(getExtensionAttributes) foreach
            (evaluateExtensionAttributes(_))
    }

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
        updateExtensionAttributesCopy(cloned)

        cloned
    }

    /**
     * Set the focus on this control.
     *
     * @return  true iif control accepted focus
     */
    // By default, a control doesn't accept focus
    def setFocus() = false

    // Build children controls if any, delegating the actual construction to the given `buildTree` function
    def buildChildren(buildTree: (XBLContainer, BindingContext, ElementAnalysis, Seq[Int]) ⇒ Option[XFormsControl], idSuffix: Seq[Int]) =
        staticControl match {
            case withChildren: ChildrenBuilderTrait ⇒ Controls.buildChildren(this, withChildren.children, buildTree, idSuffix)
            case _ ⇒
        }
}

object XFormsControl {

    // By default all controls support HTML LHHA
    def DefaultLHHAHTMLSupport = Array.fill(4)(true)

    def controlSupportsRefreshEvents(control: XFormsControl) =
        (control ne null) && control.supportsRefreshEvents

    // Whether a given control has an associated xforms:label element.
    def hasLabel(containingDocument: XFormsContainingDocument, prefixedId: String) =
        containingDocument.getStaticOps.getLabel(prefixedId) ne null

    // Whether a given control has an associated xforms:hint element.
    def hasHint(containingDocument: XFormsContainingDocument, prefixedId: String) =
        containingDocument.getStaticOps.getHint(prefixedId) ne null

    // Whether a given control has an associated xforms:help element.
    def hasHelp(containingDocument: XFormsContainingDocument, prefixedId: String) =
        containingDocument.getStaticOps.getHelp(prefixedId) ne null

    // Whether a given control has an associated xforms:alert element.
    def hasAlert(containingDocument: XFormsContainingDocument, prefixedId: String) =
        containingDocument.getStaticOps.getAlert(prefixedId) ne null

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

    def addAjaxClass(attributesImpl: AttributesImpl, newlyVisibleSubtree: Boolean, control1: XFormsControl, control2: XFormsControl): Boolean = {
        var added = false
        val class1 = Option(control1) map (_.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME)) orNull
        val class2 = control2.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME)

        if (newlyVisibleSubtree || !XFormsUtils.compareStrings(class1, class2)) {
            // Custom MIPs changed
            val attributeValue =
                if (class1 eq null)
                    class2
                else {
                    val sb = new StringBuilder(100)

                    def tokenize(value: String) = LinkedHashSet(StringUtils.split(value): _*)

                    val classes1 = tokenize(class1)
                    val classes2 = tokenize(class2)

                    // Classes to remove
                    for (currentClass ← classes1) {
                        if (! classes2(currentClass)) {
                            if (sb.length > 0)
                                sb.append(' ')
                            sb.append('-')
                            sb.append(currentClass)
                        }
                    }

                    // Classes to add
                    for (currentClass ← classes2) {
                        if (! classes1(currentClass)) {
                            if (sb.length > 0)
                                sb.append(' ')
                            sb.append('+')
                            sb.append(currentClass)
                        }
                    }
                    sb.toString
                }
            if (attributeValue ne null)
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue == "")
        }
        added
    }

    def addOrAppendToAttributeIfNeeded(attributesImpl: AttributesImpl, name: String, value: String, isNewRepeatIteration: Boolean, isDefaultValue: Boolean) =
        if (isNewRepeatIteration && isDefaultValue)
            false
        else {
            XMLUtils.addOrAppendToAttribute(attributesImpl, name, value)
            true
        }

    def addAttributeIfNeeded(attributesImpl: AttributesImpl, name: String, value: String, isNewRepeatIteration: Boolean, isDefaultValue: Boolean) =
        if (isNewRepeatIteration && isDefaultValue)
            false
        else {
            attributesImpl.addAttribute("", name, name, ContentHandlerHelper.CDATA, value)
            true
        }

    val STANDARD_EXTENSION_ATTRIBUTES = Array(XFormsConstants.STYLE_QNAME, XFormsConstants.CLASS_QNAME)
    val ALLOWED_EXTERNAL_EVENTS = collection.immutable.Set(XFormsEvents.KEYPRESS).asJava
    val NULL_LHHA = new XFormsControl.NullLHHAProperty

    class XFormsControlLocal extends Cloneable {
        override def clone: AnyRef = super.clone
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

        def value(): T = {// NOTE: making this method final produces an AbstractMethodError with Java 5 (ok with Java 6)
            if (! isEvaluated) {
                _value =
                    if (isRelevant) {
                        notifyCompute()
                        evaluateValue()
                    } else
                        // NOTE: if the control is not relevant, nobody should ask about this in the first place
                        null
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

    // Control property for LHHA
    trait LHHAProperty extends ControlProperty[String] {
        def escapedValue(): String
        def isHTML: Boolean
    }

    // Immutable null LHHA property
    class NullLHHAProperty extends ImmutableControlProperty(null: String) with LHHAProperty {
        def escapedValue(): String = null
        def isHTML = false
    }
}

trait XPathSupport {

    self: XFormsControl ⇒

    private def getNamespaceMappings =
        if (staticControl ne null) staticControl.namespaceMapping else container.getNamespaceMappings(element)

    /**
     * Evaluate an attribute of the control as an AVT.
     *
     * @param attributeValue    value of the attribute
     * @return                  value of the AVT or null if cannot be computed
     */
    def evaluateAvt(attributeValue: String) = {
        if (! XFormsUtils.maybeAVT(attributeValue))
            // Definitely not an AVT
            attributeValue
        else {
            // Possible AVT

            // NOTE: the control may or may not be bound, so don't use getBoundItem()
            val contextNodeset = bindingContext.getNodeset
            if (contextNodeset.size == 0)
                null // TODO: in the future we should be able to try evaluating anyway
            else {
                // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
                // Reason is that XPath functions might use the context stack to get the current model, etc.
                getContextStack.setBinding(getBindingContext)
                // Evaluate
                try
                    XPathCache.evaluateAsAvt(contextNodeset, bindingContext.getPosition, attributeValue, getNamespaceMappings,
                        bindingContext.getInScopeVariables, XFormsContainingDocument.getFunctionLibrary, getFunctionContext, null, getLocationData)
                catch {
                    case e: Exception ⇒
                        // Don't consider this as fatal
                        XFormsError.handleNonFatalXPathError(containingDocument, e)
                        null
                } finally
                    // Restore function context to prevent leaks caused by context pointing to removed controls
                    returnFunctionContext()
            }
        }
    }

    /**
     * Evaluate an XPath expression as a string in the context of this control.
     *
     * @param xpathString       XPath expression
     * @return                  value, or null if cannot be computed
     */
    def evaluateAsString(xpathString: String, contextItems: JList[Item], contextPosition: Int) = {
        // NOTE: the control may or may not be bound, so don't use getBoundNode()
        if ((contextItems eq null) || contextItems.size == 0)
            null
        else {
            // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
            // Reason is that XPath functions might use the context stack to get the current model, etc.
            getContextStack.setBinding(getBindingContext)
            try
                XPathCache.evaluateAsString(contextItems, contextPosition, xpathString, getNamespaceMappings,
                    bindingContext.getInScopeVariables, XFormsContainingDocument.getFunctionLibrary, getFunctionContext, null, getLocationData)
            catch {
                case e: Exception ⇒
                    // Don't consider this as fatal
                    XFormsError.handleNonFatalXPathError(containingDocument, e)
                    null
            } finally
                // Restore function context to prevent leaks caused by context pointing to removed controls
                returnFunctionContext()
        }
    }

    /**
     * Evaluate an XPath expression as a string in the context of this control.
     *
     * @param contextItem           context item
     * @param xpathString           XPath expression
     * @param namespaceMapping      namespace mappings to use
     * @param variableToValueMap    variables to use
     * @return                      value, or null if cannot be computed
     */
    def evaluateAsString(contextItem: Item, xpathString: String, namespaceMapping: NamespaceMapping, variableToValueMap: JMap[String, ValueRepresentation]): String = {
        if (contextItem == null)
            null
        else {
            // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
            // Reason is that XPath functions might use the context stack to get the current model, etc.
            getContextStack.setBinding(getBindingContext)
            try
                XPathCache.evaluateAsString(contextItem, xpathString, namespaceMapping, variableToValueMap,
                    XFormsContainingDocument.getFunctionLibrary, getFunctionContext, null, getLocationData)
            catch {
                case e: Exception ⇒
                    // Don't consider this as fatal
                    XFormsError.handleNonFatalXPathError(containingDocument, e)
                    null
            } finally
                // Restore function context to prevent leaks caused by context pointing to removed controls
                returnFunctionContext()
        }
    }

    // Return an XPath function context having this control as source control.
    private def getFunctionContext =
        getContextStack.getFunctionContext(getEffectiveId)

    private def returnFunctionContext() =
        getContextStack.returnFunctionContext()
}

trait AjaxSupport {

    self: XFormsControl ⇒

    // Whether the control support Ajax updates
    def supportAjaxUpdates = true

    // Whether the control support full Ajax updates
    def supportFullAjaxUpdates = true

    def outputAjaxDiff(ch: ContentHandlerHelper, other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean) = ()

    def addAjaxAttributes(attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean, other: XFormsControl) = {
        var added = false

        // Control id
        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, getEffectiveId))

        // Class attribute
        added |= addAjaxClass(attributesImpl, isNewlyVisibleSubtree, other, this)

        // Label, help, hint, alert, etc.
        added |= addAjaxLHHA(attributesImpl, isNewlyVisibleSubtree, other, this)
        // Output control-specific attributes
        added |= addAjaxCustomAttributes(attributesImpl, isNewlyVisibleSubtree, other)

        added
    }


    private def addAjaxLHHA(attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean, control1: XFormsControl, control2: XFormsControl) = {
        var added = false

        for {
            lhhaType ← LHHA.values
            value1 = if (isNewlyVisibleSubtree) null else control1.getLHHA(lhhaType).value()
            lhha2 = control2.getLHHA(lhhaType)
            value2 = lhha2.value()
            if value1 != value2
            attributeValue = Option(lhha2.escapedValue()) getOrElse ""
        } yield
            added |= addOrAppendToAttributeIfNeeded(attributesImpl, lhhaType.name(), attributeValue, isNewlyVisibleSubtree, attributeValue == "")

        added
    }

    /**
     * Add attributes differences for custom attributes.
     *
     * @param attributesImpl        attributes to add to
     * @param isNewRepeatIteration  whether the current controls is within a new repeat iteration
     * @param other                 original control, possibly null
     * @return                      true if any attribute was added, false otherwise
     */
    def addAjaxCustomAttributes(attributesImpl: AttributesImpl, isNewRepeatIteration: Boolean, other: XFormsControl) = {
        // By default, diff only attributes in the xxforms:* namespace
        val extensionAttributes = getExtensionAttributes
        (extensionAttributes ne null) && addAttributesDiffs(other, attributesImpl, isNewRepeatIteration, extensionAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI)
    }

    private def addAttributesDiffs(control1: XFormsControl, attributesImpl: AttributesImpl, isNewRepeatIteration: Boolean, attributeQNames: Array[QName], namespaceURI: String) = {
        val control2 = this
        var added = false
        for {
            avtAttributeQName ← attributeQNames
            // Skip if namespace URI is excluded
            if (namespaceURI eq null) || namespaceURI == avtAttributeQName.getNamespaceURI
            value1 = if (control1 eq null) null else control1.getExtensionAttributeValue(avtAttributeQName)
            value2 = control2.getExtensionAttributeValue(avtAttributeQName)
            if value1 != value2
            attributeValue = if (value2 ne null) value2 else ""
        } yield
            // NOTE: For now we use the local name; may want to use a full name?
            added |= addAttributeIfNeeded(attributesImpl, avtAttributeQName.getName, attributeValue, isNewRepeatIteration, attributeValue == "")

        added
    }

    def addAjaxStandardAttributes(originalControl: XFormsSingleNodeControl, ch: ContentHandlerHelper, isNewRepeatIteration: Boolean) {
        val extensionAttributes = STANDARD_EXTENSION_ATTRIBUTES

        if (extensionAttributes ne null) {
            val control2 = this

            for {
                avtAttributeQName ← extensionAttributes
                if avtAttributeQName != XFormsConstants.CLASS_QNAME
                value1 = if (originalControl eq null) null else originalControl.getExtensionAttributeValue(avtAttributeQName)
                value2 = control2.getExtensionAttributeValue(avtAttributeQName)
                if value1 != value2
                attributeValue = if (value2 ne null) value2 else ""
                attributesImpl = new AttributesImpl
            } yield {
                attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, control2.getEffectiveId))
                addAttributeIfNeeded(attributesImpl, "for", control2.getEffectiveId, isNewRepeatIteration, false)
                addAttributeIfNeeded(attributesImpl, "name", avtAttributeQName.getName, isNewRepeatIteration, false)
                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", attributesImpl)
                ch.text(attributeValue)
                ch.endElement()
            }
        }
    }
}

trait LHHASupport {

    self: XFormsControl ⇒

    // Label, help, hint and alert (evaluated lazily)
    private var newLHHA = new Array[LHHAProperty](XFormsConstants.LHHACount)

    def markLHHADirty() {
        for (currentLHHA ← newLHHA)
            if (currentLHHA ne null)
                currentLHHA.handleMarkDirty()
    }

    // Copy LHHA if not null
    def updateLHHACopy(copy: XFormsControl) {
        copy.newLHHA = new Array[LHHAProperty](XFormsConstants.LHHACount)
        for {
            i ← 0 to newLHHA.size - 1
            currentLHHA = newLHHA(i)
            if currentLHHA ne null
        } yield {
            // Evaluate lazy value before copying
            currentLHHA.value()

            // Copy
            copy.newLHHA(i) = currentLHHA.copy.asInstanceOf[LHHAProperty]
        }
    }

    def getLHHA(lhhaType: XFormsConstants.LHHA) = {
        val index = lhhaType.ordinal
        Option(newLHHA(index)) getOrElse {
            val lhhaElement = container.getPartAnalysis.getLHHA(getPrefixedId, lhhaType.name)
            val result = Option(lhhaElement) map (new MutableLHHAProperty(_, lhhaHTMLSupport(index))) getOrElse NULL_LHHA
            newLHHA(index) = result
            result
        }
    }

    // Convenience accessors
    def getLabel = getLHHA(LHHA.label).value()
    def getEscapedLabel = getLHHA(LHHA.label).escapedValue()
    def isHTMLLabel = getLHHA(LHHA.label).isHTML
    def getHelp = getLHHA(LHHA.help).value()
    def getEscapedHelp = getLHHA(LHHA.help).escapedValue()
    def isHTMLHelp = getLHHA(LHHA.help).isHTML
    def getHint = getLHHA(LHHA.hint).value()
    def getEscapedHint = getLHHA(LHHA.hint).escapedValue()
    def isHTMLHint = getLHHA(LHHA.hint).isHTML
    def getAlert = getLHHA(LHHA.alert).value()
    def isHTMLAlert = getLHHA(LHHA.alert).isHTML
    def getEscapedAlert = getLHHA(LHHA.alert).escapedValue()

    // Whether we support HTML LHHA
    def lhhaHTMLSupport = DefaultLHHAHTMLSupport

    // Mutable LHHA property
    private class MutableLHHAProperty(lhhaAnalysis: LHHAAnalysis, supportsHTML: Boolean) extends MutableControlProperty[String] with LHHAProperty {

        require((lhhaAnalysis ne null) && (lhhaAnalysis.element ne null), "LHHA analysis/element can't be null")

        private val lhhaElement = lhhaAnalysis.element
        private var _isHTML = false

        protected def isRelevant = self.isRelevant
        protected def wasRelevant = self.wasRelevant

        protected def evaluateValue() = {
            val tempContainsHTML = new Array[Boolean](1)
            val result = doEvaluateValue(tempContainsHTML)
            _isHTML = result != null && tempContainsHTML(0)
            result
        }

        def escapedValue() = {
            val rawValue = value()
            if (_isHTML)
                XFormsControl.getEscapedHTMLValue(getLocationData, rawValue)
            else
                XMLUtils.escapeXMLMinimal(rawValue)
        }

        def isHTML = {
            value()
            _isHTML
        }

        protected override def markDirty() {
            super.markDirty()
            _isHTML = false
        }

        protected def requireUpdate =
            containingDocument.getXPathDependencies.requireLHHAUpdate(lhhaElement.getName, getPrefixedId)

        protected def notifyCompute() =
            containingDocument.getXPathDependencies.notifyComputeLHHA()

        protected def notifyOptimized() =
            containingDocument.getXPathDependencies.notifyOptimizeLHHA()

        override def copy: MutableLHHAProperty =
            super.copy.asInstanceOf[MutableLHHAProperty]

        /**
         * Evaluate the value of a LHHA related to this control.
         *
         * @return string containing the result of the evaluation, null if evaluation failed
         */
        private def doEvaluateValue(tempContainsHTML: Array[Boolean]) = {
            val control = self
            val contextStack = control.getContextStack

            if (lhhaAnalysis.isLocal) {
                // LHHA is direct child of control, evaluate within context
                contextStack.setBinding(control.getBindingContext)
                contextStack.pushBinding(lhhaElement, control.effectiveId, control.getChildElementScope(lhhaElement))
                val result = XFormsUtils.getElementValue(control.container, contextStack, control.effectiveId, lhhaElement, supportsHTML, tempContainsHTML)
                contextStack.popBinding()
                result
            } else {
                // LHHA is somewhere else, assumed as a child of xforms:* or xxforms:*

                // Find context object for XPath evaluation
                val contextElement = lhhaElement.getParent
                val contextStaticId = XFormsUtils.getElementId(contextElement)
                val contextEffectiveId =
                    if (contextStaticId == null || (contextStaticId == "#document")) {
                        // Assume we are at the top-level
                        contextStack.resetBindingContext()
                        control.container.getFirstControlEffectiveId
                    } else {
                        // Not at top-level, find containing object
                        val ancestorContextControl = findAncestorContextControl(contextStaticId, XFormsUtils.getElementId(lhhaElement))
                        if (ancestorContextControl != null) {
                            contextStack.setBinding(ancestorContextControl.getBindingContext)
                            ancestorContextControl.effectiveId
                        } else
                            null
                    }

                if (contextEffectiveId != null) {
                    // Push binding relative to context established above and evaluate
                    contextStack.pushBinding(lhhaElement, contextEffectiveId, control.getResolutionScope)
                    val result = XFormsUtils.getElementValue(control.container, contextStack, control.effectiveId, lhhaElement, supportsHTML, tempContainsHTML)
                    contextStack.popBinding()
                    result
                } else
                    // Do as if there was no LHHA
                    null
            }
        }

        private def findAncestorContextControl(contextStaticId: String, lhhaStaticId: String): XFormsControl = {

            val control = self

            // NOTE: LHHA element must be in the same resolution scope as the current control (since @for refers to @id)
            val lhhaScope = control.getResolutionScope
            val lhhaPrefixedId = lhhaScope.prefixedIdForStaticId(lhhaStaticId)

            // Assume that LHHA element is within same repeat iteration as its related control
            val contextPrefixedId = XFormsUtils.getRelatedEffectiveId(lhhaPrefixedId, contextStaticId)
            val contextEffectiveId = contextPrefixedId + XFormsUtils.getEffectiveIdSuffixWithSeparator(control.effectiveId)

            var ancestorObject = control.container.getContainingDocument.getObjectByEffectiveId(contextEffectiveId)
            while (ancestorObject.isInstanceOf[XFormsControl]) {
                val ancestorControl = ancestorObject.asInstanceOf[XFormsControl]
                if (ancestorControl.getResolutionScope == lhhaScope) {
                    // Found ancestor in right scope
                    return ancestorControl
                }
                ancestorObject = ancestorControl.parent
            }

            null
        }
    }

    def compareLHHA(other: XFormsControl): Boolean = {
        for (lhhaType ← LHHA.values)
            if (getLHHA(lhhaType).value() != other.getLHHA(lhhaType).value())
                return false

        true
    }
}

trait LocalSupport {

    self: XFormsControl ⇒

    private var initialLocal: XFormsControl.XFormsControlLocal = null
    private var currentLocal: XFormsControl.XFormsControlLocal = null

    /**
     * Serialize this control's information which cannot be reconstructed from instances. The result is empty if no
     * serialization is needed, or a map of name/value pairs otherwise.
     */
    def serializeLocal = Collections.emptyMap[String, String]

    def updateLocalCopy(copy: XFormsControl) {
        if (this.currentLocal != null) {
            // There is some local data
            if (this.currentLocal ne this.initialLocal) {
                // The trees don't keep wasteful references
                copy.currentLocal = copy.initialLocal
                this.initialLocal = this.currentLocal
            } else {
                // The new tree must have its own copy
                // NOTE: We could implement a copy-on-write flag here
                copy.initialLocal = this.currentLocal.clone.asInstanceOf[XFormsControl.XFormsControlLocal]
                copy.currentLocal = copy.initialLocal
            }
        }
    }

    def setLocal(local: XFormsControl.XFormsControlLocal) {
        this.initialLocal = local
        this.currentLocal = local
    }

    def getLocalForUpdate = {
        if (containingDocument.isHandleDifferences) {
            // Happening during a client request where we need to handle diffs
            val controls = containingDocument.getControls
            if (controls.getInitialControlTree ne controls.getCurrentControlTree) {
                if (currentLocal ne initialLocal)
                    throw new OXFException("currentLocal != initialLocal")
            } else if (initialLocal eq currentLocal)
                currentLocal = initialLocal.clone.asInstanceOf[XFormsControl.XFormsControlLocal]
        } else {
            // Happening during initialization
            // NOP: Don't modify currentLocal
        }

        currentLocal
    }

    def getInitialLocal = initialLocal
    def getCurrentLocal = currentLocal

    def resetLocal() = initialLocal = currentLocal
}

trait ExtensionAttributesSupport {

    self: XFormsControl ⇒

    // Optional extension attributes supported by the control
    // TODO: must be evaluated lazily
    private var extensionAttributesValues: JMap[QName, String] = null

    def compareExtensionAttributes(other: XFormsControl): Boolean = {
        if (extensionAttributesValues ne null)
            for ((currentName, currentValue) ←  extensionAttributesValues.asScala)
                if (currentValue != other.getExtensionAttributeValue(currentName))
                    return false

        true
    }

    def evaluateExtensionAttributes(attributeQNames: Array[QName]) {
        val controlElement = getControlElement
        for (avtAttributeQName ← attributeQNames) {
            val attributeValue = controlElement.attributeValue(avtAttributeQName)
            if (attributeValue ne null) {
                // NOTE: This can return null if there is no context
                val resolvedValue = evaluateAvt(attributeValue)
                if (extensionAttributesValues eq null)
                    extensionAttributesValues = new JHashMap[QName, String]
                extensionAttributesValues.put(avtAttributeQName, resolvedValue)
            }
        }
    }

    def markExtensionAttributesDirty() =
        Option(extensionAttributesValues) foreach
            (_.clear())

    // Return an optional static list of extension attribute QNames provided by the control. If present these
    // attributes are evaluated as AVTs and copied over to the outer control element.
    def getExtensionAttributes: Array[QName] = null

    def getExtensionAttributeValue(attributeName: QName) =
        Option(extensionAttributesValues) map (_.get(attributeName)) orNull

    /**
     * Add all non-null values of extension attributes to the given list of attributes.
     *
     * @param attributesImpl    attributes to add to
     * @param namespaceURI      restriction on namespace URI, or null if all attributes
     */
    def addExtensionAttributes(attributesImpl: AttributesImpl, namespaceURI: String) =
        if (extensionAttributesValues ne null) {
            for {
                (currentName, currentValue) ← extensionAttributesValues.asScala
                // Skip if namespace URI is excluded
                if (namespaceURI eq null) || namespaceURI == currentName.getNamespaceURI
                if currentName != XFormsConstants.CLASS_QNAME
                if currentValue ne null
                localName = currentName.getName
            } yield
                attributesImpl.addAttribute("", localName, localName, ContentHandlerHelper.CDATA, currentValue)
        }

    def updateExtensionAttributesCopy(copy: XFormsControl) {
        Option(extensionAttributesValues) foreach
            (e ⇒ copy.extensionAttributesValues = new JHashMap[QName, String](e))
    }
}

trait EventSupport {

    self: XFormsControl ⇒

    def performDefaultAction(event: XFormsEvent): Unit = event match {
        case ev @ (_: XXFormsRepeatFocusEvent | _: XFormsFocusEvent) ⇒
            // Try to update xforms:repeat indexes based on this

            // Find current path through ancestor xforms:repeat elements, if any
            val repeatIterationsToModify =
                new AncestorIterator(self) collect
                    { case ri: XFormsRepeatIterationControl if ! ri.isCurrentIteration ⇒ ri.getEffectiveId }

            if (repeatIterationsToModify.nonEmpty) {
                val controls = containingDocument.getControls
                // Find all repeat iterations and controls again
                for (repeatIterationEffectiveId ← repeatIterationsToModify) {
                    val repeatIterationControl = controls.getObjectByEffectiveId(repeatIterationEffectiveId).asInstanceOf[XFormsRepeatIterationControl]
                    val newRepeatIndex = repeatIterationControl.iterationIndex

                    val indentedLogger = controls.getIndentedLogger
                    if (indentedLogger.isDebugEnabled)
                        indentedLogger.logDebug("xforms:repeat", "setting index upon focus change", "new index", newRepeatIndex.toString)

                    repeatIterationControl.repeat.setIndex(newRepeatIndex)
                }
            }

            // Focus on current control if possible
            if (XFormsEvents.XFORMS_FOCUS == event.getName)
                setFocus()

        case _: XFormsHelpEvent ⇒
            containingDocument.setClientHelpEffectiveControlId(getEffectiveId)
        case ev: XXFormsBindingErrorEvent ⇒
            XFormsError.handleNonFatalSetvalueError(containingDocument, ev.locationData, ev.reason)
        case _ ⇒
    }

    def performTargetAction(container: XBLContainer, event: XFormsEvent) = ()

    /**
     * Check whether this concrete control supports receiving the external event specified.
     *
     * @param indentedLogger    logger
     * @param logType           log type
     * @param eventName         event name to check
     * @return                  true iif the event is supported
     */
    def allowExternalEvent(indentedLogger: IndentedLogger, logType: String, eventName: String) =
        if (getAllowedExternalEvents.contains(eventName) || ALLOWED_EXTERNAL_EVENTS.contains(eventName))
            true
        else {
            if (indentedLogger.isDebugEnabled)
                indentedLogger.logDebug(logType, "ignoring invalid client event on control", "control type", getName, "control id", getEffectiveId, "event name", eventName)

            false
        }

    def getAllowedExternalEvents = Collections.emptySet[String]

    // TODO LATER: should probably return true because most controls could then dispatch relevance events
    def supportsRefreshEvents = false

    // Consider that the parent of top-level controls is the containing document. This allows events to propagate to
    // the top-level.
    def getParentEventObserver(container: XBLContainer): XFormsEventObserver =
        Option(parent) getOrElse containingDocument

    def addListener(eventName: String, listener: org.orbeon.oxf.xforms.event.EventListener): Unit =
        throw new UnsupportedOperationException

    def removeListener(eventName: String, listener: org.orbeon.oxf.xforms.event.EventListener): Unit =
        throw new UnsupportedOperationException

    def getListeners(eventName: String): JList[org.orbeon.oxf.xforms.event.EventListener] = null
}

trait BindingSupport {

    self: XFormsControl ⇒

    // This control's binding context
    var bindingContext: BindingContext = null
    def getBindingContext: BindingContext = bindingContext
    def getBindingContext(containingDocument: XFormsContainingDocument): BindingContext = bindingContext

    // Relevance
    private var _isRelevant = false
    final def isRelevant = _isRelevant

    private var _wasRelevant = false
    def wasRelevant = _wasRelevant

    // Evaluate the control's binding, either during create or update
    final def evaluateBinding(parentContext: BindingContext, update: Boolean) = {
        pushBinding(parentContext, update)
        evaluateChildFollowingBinding()
    }

    // Refresh the control's binding during update, in case a re-evaluation is not needed
    final def refreshBinding(parentContext: BindingContext) = {
        // Make sure the parent is updated, as ancestor bindings might have changed, and it is important to
        // ensure that the chain of bindings is consistent
        setBindingContext(getBindingContext.copy(parent = parentContext))
        markDirtyImpl(containingDocument.getXPathDependencies)
        evaluateChildFollowingBinding()
    }

    protected final def pushBinding(parentContext: BindingContext, update: Boolean) = {
        pushBindingImpl(parentContext)

        if (update)
            markDirtyImpl(containingDocument.getXPathDependencies)
    }

    // Default behavior for pushing a binding
    protected def pushBindingImpl(parentContext: BindingContext) = {

        // Compute new binding
        val newBindingContext = {
            val contextStack = container.getContextStack
            contextStack.setBinding(parentContext)
            contextStack.pushBinding(element, effectiveId, staticControl.scope)
            contextStack.getCurrentBindingContext
        }

        // Set binding context
        setBindingContext(newBindingContext)

        newBindingContext
    }

    final protected def pushBindingCopy(context: BindingContext) = {
        // Compute new binding
        val newBindingContext = {
            val contextStack = container.getContextStack
            contextStack.setBinding(context)
            contextStack.pushCopy()
        }

        // Set binding context
        setBindingContext(newBindingContext)

        newBindingContext
    }

    // Update the bindings in effect within and after this control
    // Only variables modify the default behavior
    def evaluateChildFollowingBinding() = ()

    // Return the bindings in effect within and after this control
    def bindingContextForChild = bindingContext
    def bindingContextForFollowing = bindingContext.parent

    // Set this control's binding context and handle create/destroy/update lifecycle
    final def setBindingContext(bindingContext: BindingContext) {
        val oldBinding = this.bindingContext
        this.bindingContext = bindingContext

        // Relevance is a property of all controls
        val oldRelevant = this._isRelevant
        val newRelevant = computeRelevant

        if (! oldRelevant && newRelevant) {
            // Control is created
            this._isRelevant = newRelevant
            onCreate()
        } else if (oldRelevant && ! newRelevant) {
            // Control is destroyed
            onDestroy()
            this._isRelevant = newRelevant
        } else if (newRelevant)
            onBindingUpdate(oldBinding, bindingContext)
    }

    def onCreate() = _wasRelevant = false
    def onDestroy() = ()
    def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext) = ()

    def computeRelevant =
        // By default: if there is a parent, we have the same relevance as the parent, otherwise we are top-level so
        // we are relevant by default
        (parent eq null) || parent.isRelevant

    def wasRelevantCommit() = {
        val result = _wasRelevant
        _wasRelevant = _isRelevant
        result
    }

    def compareRelevance(other: XFormsControl) =
        _isRelevant == other._isRelevant
}

trait XMLDumpSupport extends DebugXML{

    self: XFormsControl ⇒

    def toXML(helper: ContentHandlerHelper, attributes: List[String])(content: ⇒ Unit) {

        def itemToString(i: Item) = i match {
            case atomic: AtomicValue ⇒ atomic.getStringValue
            case node: NodeInfo ⇒ node.getDisplayName
            case _ ⇒ throw new IllegalStateException
        }

        helper.startElement(getName, Array(
            "id", getId,
            "effectiveId", effectiveId,
            "isRelevant", isRelevant.toString,
            "wasRelevant", wasRelevant.toString,
            "binding-names", bindingContext.getNodeset.asScala map (itemToString(_)) mkString ("(", ", ", ")"),
            "binding-position", bindingContext.getPosition.toString
        ))
        content
        helper.endElement()
    }

    def toXML(helper: ContentHandlerHelper) = {
        helper.startDocument()
        toXML(helper, List.empty)()
        helper.endDocument()
    }

    def toXMLString =
        Dom4jUtils.domToPrettyString(XMLUtils.createDocument(this))

    def dumpXML() =
        println(toXMLString)
}