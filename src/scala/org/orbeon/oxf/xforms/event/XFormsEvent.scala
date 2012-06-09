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
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsUtils
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

    def getAttribute(name: String): SequenceIterator =
        attribute(this, name, Deprecated, StandardAttributes) getOrElse EmptyIterator.getInstance

    def getAttributeAsString(name: String): String =
        Option(getAttribute(name).next()) map (_.getStringValue) orNull

    def indentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)
    def locationData: LocationData = null

    /**
     * Whether this event matches filters placed on the given event handler.
     *
     * This is used e.g. for checking modifiers and text on keypress.
     *
     * @param handler   event handler to check
     * @return          true iif the event matches the filter
     */
    def matches(handler: EventHandler) = true
}

object XFormsEvent {

    // Event phases
    sealed abstract class Phase(val name: String)
    object Capture  extends Phase("capture")
    object Target   extends Phase("target")
    object Bubbling extends Phase("bubbling")

    def stringIterator(s: String)   = SingletonIterator.makeIterator(StringValue.makeStringValue(s))
    def booleanIterator(b: Boolean) = SingletonIterator.makeIterator(BooleanValue.get(b))
    def longIterator(l: Long)       = SingletonIterator.makeIterator(new Int64Value(l))
    def xxformsName(name: String)   = buildExplodedQName(XXFORMS_NAMESPACE_URI, name)

    def attribute[A <: XFormsEvent](event: A, name: String, deprecated: Map[String, String], standard: Map[String, A ⇒ SequenceIterator]): Option[SequenceIterator] = {

        // Deprecation warning if needed
        deprecated.get(name) foreach { newName ⇒
            event.indentedLogger.logWarning("", "event('" + name + "') is deprecated. Use event('" + newName + "') instead.")
        }

        def default = {
            // "If the event context information does not contain the property indicated by the string argument, then an
            // empty node-set is returned."
            event.indentedLogger.logWarning("", "Unsupported event context information for event('" + name + "').")
            None
        }

        // Try custom attributes first, then standard attributes
        event.custom.get(name) map (_.iterate()) orElse
            (standard.get(name) map (_(event))) orElse
            default
    }
    
    private val Deprecated = Map(
        "target"            → "xxforms:targetid",
        "event"             → "xxforms:type",
        "repeat-indexes"    → "xxforms:repeat-indexes"
    )
    
    private val StandardAttributes = Map[String, XFormsEvent ⇒ SequenceIterator](
        "target"                             → (e ⇒ stringIterator(e.targetObject.getId)),
        xxformsName("target")                → (e ⇒ stringIterator(e.targetObject.getId)),
        xxformsName("targetid")              → (e ⇒ stringIterator(e.targetObject.getId)),
        xxformsName("effective-targetid")    → (e ⇒ stringIterator(e.targetObject.getEffectiveId)),
        "event"                              → (e ⇒ stringIterator(e.name)),
        xxformsName("type")                  → (e ⇒ stringIterator(e.name)),
        xxformsName("bubbles")               → (e ⇒ booleanIterator(e.bubbles)),
        xxformsName("cancelable")            → (e ⇒ booleanIterator(e.cancelable)),
        xxformsName("phase")                 → (e ⇒ stringIterator(e.currentPhase.name)),
        xxformsName("observerid")            → (e ⇒ stringIterator(e.currentObserver.getId)),
        xxformsName("effective-observerid")  → (e ⇒ stringIterator(e.currentObserver.getEffectiveId)),
        "repeat-indexes"                     → repeatIndexes,
        xxformsName("repeat-indexes")        → repeatIndexes,
        xxformsName("repeat-ancestors")      → repeatAncestors,
        xxformsName("target-prefixes")       → targetPrefixes
    )

    private def repeatIndexes(e: XFormsEvent) = {
        val parts = XFormsUtils.getEffectiveIdSuffixParts(e.targetObject.getEffectiveId)
        new ListIterator(parts.toList map (index ⇒ StringValue.makeStringValue(index.toString)) asJava)
    }

    private def repeatAncestors(e: XFormsEvent) =
        if (XFormsUtils.hasEffectiveIdSuffix(e.targetObject.getEffectiveId)) {
            // There is a suffix so compute
            val ancestorRepeats = e.containingDocument.getStaticOps.getAncestorRepeats(XFormsUtils.getPrefixedId(e.targetObject.getEffectiveId), null)
            new ListIterator(ancestorRepeats map (prefixedId ⇒ StringValue.makeStringValue(XFormsUtils.getStaticIdFromId(prefixedId))) asJava)
        } else
            // No suffix
            EmptyIterator.getInstance

    private def targetPrefixes(e: XFormsEvent) = {
        val parts = XFormsUtils.getEffectiveIdPrefixParts(e.targetObject.getEffectiveId)
        new ListIterator(parts.toList map (StringValue.makeStringValue(_)) asJava)
    }
}