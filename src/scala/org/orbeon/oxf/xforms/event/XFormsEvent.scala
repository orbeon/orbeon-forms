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
package org.orbeon.oxf.xforms.event

import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.{XFormsUtils, XFormsContainingDocument}
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xml.XMLUtils.buildExplodedQName
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.om._
import XFormsEvent._
import collection.JavaConverters._
import org.orbeon.saxon.value.{Int64Value, BooleanValue, SequenceExtent, StringValue}

/**
 * XFormsEvent represents an XForms event passed to all events and actions.
 */
abstract class XFormsEvent(
        val containingDocument: XFormsContainingDocument,
        val name: String,
        val targetObject: XFormsEventTarget,
        val bubbles: Boolean,
        val cancelable: Boolean) {

    self ⇒

    require(containingDocument ne null)
    require(targetObject ne null)

    private var _currentPhase: Phase = _
    private var _currentObserver: XFormsEventObserver = _
    private var _custom: Map[String, SequenceExtent] = Map()

    def currentPhase: Phase = _currentPhase
    def currentPhase_=(currentPhase: Phase): Unit = _currentPhase = currentPhase

    def currentObserver: XFormsEventObserver = _currentObserver
    def currentObserver_=(currentObserver: XFormsEventObserver): Unit = _currentObserver = currentObserver

    def custom = _custom

    def setCustom(name: String, value: SequenceExtent): Unit =
        _custom += name → value

    def setCustomAsString(name: String, value: String): Unit =
        setCustom(name, new SequenceExtent(Array[Item](StringValue.makeStringValue(value))))

    // These methods can be overridden by subclasses
    def isDeprecated(name: String) = Deprecated.get(name)
    def getStandardAttribute(name: String): Option[this.type ⇒ SequenceIterator] = StandardAttributes.get(name)

    def default(): SequenceIterator = {
        // "If the event context information does not contain the property indicated by the string argument, then an
        // empty node-set is returned."
        indentedLogger.logWarning("", "Unsupported event context information for event('" + name + "').")
        EmptyIterator.getInstance
    }

    // This method is overridden by legacy event classes (remove when those have been rewritten)
    def getAttribute(name: String): SequenceIterator = {

        // Deprecation warning if needed
        def checkDeprecated() = isDeprecated(name) foreach { newName ⇒
            indentedLogger.logWarning("", "event('" + name + "') is deprecated. Use event('" + newName + "') instead.")
        }

        // Try custom attributes first, then standard attributes
        custom.get(name) map (_.iterate()) orElse
            { checkDeprecated(); getStandardAttribute(name) map (_(self)) } getOrElse
                default
    }

    def getAttributeAsString(name: String): String =
        Option(getAttribute(name).next()) map (_.getStringValue) orNull

    def indentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)
    def locationData: LocationData = null

    // Whether this event matches filters placed on the given event handler.
    // This is used for checking modifiers and text on keypress events.
    def matches(handler: EventHandler) = true
}

object XFormsEvent {

    // Event phases
    sealed abstract class Phase(val name: String)
    object Capture  extends Phase("capture")
    object Target   extends Phase("target")
    object Bubbling extends Phase("bubbling")

    import SingletonIterator.makeIterator
    import StringValue.makeStringValue

    def emptyIterator = EmptyIterator.getInstance
    def stringIterator (s: String,    cond: ⇒ Boolean = true) = if ((s ne null) && cond) makeIterator(makeStringValue(s)) else emptyIterator
    def booleanIterator(b: Boolean,   cond: ⇒ Boolean = true) = if (cond) makeIterator(BooleanValue.get(b)) else emptyIterator
    def longIterator   (l: Long,      cond: ⇒ Boolean = true) = if (cond) makeIterator(new Int64Value(l)) else emptyIterator
    def listIterator   (s: Seq[Item], cond: ⇒ Boolean = true) = if (cond) new ListIterator(s.asJava) else emptyIterator

    def xxformsName(name: String)   = buildExplodedQName(XXFORMS_NAMESPACE_URI, name)
    
    private val Deprecated = Map(
        "target"         → "xxforms:targetid",
        "event"          → "xxforms:type",
        "repeat-indexes" → "xxforms:repeat-indexes"
    )
    
    private val StandardAttributes = Map[String, XFormsEvent ⇒ SequenceIterator](
        "target"                            → (e ⇒ stringIterator(e.targetObject.getId)),
        xxformsName("target")               → (e ⇒ stringIterator(e.targetObject.getId)),
        xxformsName("targetid")             → (e ⇒ stringIterator(e.targetObject.getId)),
        xxformsName("absolute-targetid")    → (e ⇒ stringIterator(XFormsUtils.effectiveIdToAbsoluteId(e.targetObject.getEffectiveId))),
        "event"                             → (e ⇒ stringIterator(e.name)),
        xxformsName("type")                 → (e ⇒ stringIterator(e.name)),
        xxformsName("bubbles")              → (e ⇒ booleanIterator(e.bubbles)),
        xxformsName("cancelable")           → (e ⇒ booleanIterator(e.cancelable)),
        xxformsName("phase")                → (e ⇒ stringIterator(e.currentPhase.name)),
        xxformsName("observerid")           → (e ⇒ stringIterator(e.currentObserver.getId)),
        xxformsName("absolute-observerid")  → (e ⇒ stringIterator(XFormsUtils.effectiveIdToAbsoluteId(e.currentObserver.getEffectiveId))),
        "repeat-indexes"                    → repeatIndexes,
        xxformsName("repeat-indexes")       → repeatIndexes,
        xxformsName("repeat-ancestors")     → repeatAncestors,
        xxformsName("target-prefixes")      → targetPrefixes
    )

    private def repeatIndexes(e: XFormsEvent) = {
        val parts = getEffectiveIdSuffixParts(e.targetObject.getEffectiveId)
        listIterator(parts.toList map (index ⇒ StringValue.makeStringValue(index.toString)))
    }

    private def repeatAncestors(e: XFormsEvent) =
        if (hasEffectiveIdSuffix(e.targetObject.getEffectiveId)) {
            // There is a suffix so compute
            val ancestorRepeats = e.containingDocument.getStaticOps.getAncestorRepeats(getPrefixedId(e.targetObject.getEffectiveId), null)
            listIterator(ancestorRepeats map (prefixedId ⇒ StringValue.makeStringValue(getStaticIdFromId(prefixedId))))
        } else
            // No suffix
            emptyIterator

    private def targetPrefixes(e: XFormsEvent) = {
        val parts = getEffectiveIdPrefixParts(e.targetObject.getEffectiveId)
        listIterator(parts.toList map (StringValue.makeStringValue(_)))
    }
}