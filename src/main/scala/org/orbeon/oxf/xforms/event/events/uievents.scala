/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event.events

import org.orbeon.oxf.xforms.control.{XFormsValueControl, XFormsControl}
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.analysis.model.StaticBind.{InfoLevel, WarningLevel, ValidationLevel, ErrorLevel}
import org.orbeon.oxf.xforms.analysis.model.StaticBind

class DOMActivateEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(DOM_ACTIVATE, target.asInstanceOf[XFormsControl], properties, bubbles = true, cancelable = true) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsHelpEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_HELP, target.asInstanceOf[XFormsControl], properties, bubbles = true, cancelable = true) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsHintEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_HINT, target.asInstanceOf[XFormsControl], properties, bubbles = true, cancelable = true) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsFocusEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_FOCUS, target.asInstanceOf[XFormsControl], properties, bubbles = false, cancelable = true) {
    def this(target: XFormsEventTarget, inputOnly: Boolean = false) = this(target, Map("input-only" → Some(inputOnly)))

    def inputOnly = propertyOrDefault[Boolean]("input-only", default = false)
}

class DOMFocusInEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(DOM_FOCUS_IN, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class DOMFocusOutEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(DOM_FOCUS_OUT, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsEnabledEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_ENABLED, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsDisabledEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_DISABLED, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsReadonlyEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_READONLY, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsReadwriteEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_READWRITE, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsValidEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_VALID, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsInvalidEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_INVALID, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsRequiredEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_REQUIRED, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsOptionalEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_OPTIONAL, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsVisitedEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XXFORMS_VISITED, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsUnvisitedEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XXFORMS_UNVISITED, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsValueChangeEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XFORMS_VALUE_CHANGED, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget) = this(target, XFormsValueChangeEvent.properties(target))
}

private object XFormsValueChangeEvent {

    val XXFValue = xxformsName("value")

    def properties(target: XFormsEventTarget): PropertyGetter = {
        case XXFValue ⇒ Option(target) collect { case v: XFormsValueControl ⇒ v.getValue }
    }
}

class XXFormsConstraintsChangedEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsUIEvent(XXFORMS_CONSTRAINTS_CHANGED, target.asInstanceOf[XFormsControl], properties) {
    def this(target: XFormsEventTarget, level: Option[ValidationLevel], previous: List[StaticBind#ConstraintXPathMIP], current: List[StaticBind#ConstraintXPathMIP]) =
        this(target, XXFormsConstraintsChangedEvent.properties(level, previous, current))
}

private object XXFormsConstraintsChangedEvent {

    def constraintsForLevel(current: List[StaticBind#ConstraintXPathMIP], level: ValidationLevel) =
        current filter (_.level == level) map (_.id)

    def diffConstraints(previous: List[StaticBind#ConstraintXPathMIP], current: List[StaticBind#ConstraintXPathMIP], level: ValidationLevel) = {
        val previousIds = previous.map(_.id).toSet
        constraintsForLevel(current, level) filterNot previousIds
    }

    def properties(level: Option[ValidationLevel], previous: List[StaticBind#ConstraintXPathMIP], current: List[StaticBind#ConstraintXPathMIP]): PropertyGetter = {
        case "level"            ⇒ level map (_.name)
        case "constraints"      ⇒ Option(current map (_.id))
        case "errors"           ⇒ Some(constraintsForLevel(current, ErrorLevel))
        case "warnings"         ⇒ Some(constraintsForLevel(current, WarningLevel))
        case "infos"            ⇒ Some(constraintsForLevel(current, InfoLevel))
        case "added-errors"     ⇒ Some(diffConstraints(previous, current, ErrorLevel))
        case "removed-errors"   ⇒ Some(diffConstraints(current, previous, ErrorLevel))
        case "added-warnings"   ⇒ Some(diffConstraints(previous, current, WarningLevel))
        case "removed-warnings" ⇒ Some(diffConstraints(current, previous, WarningLevel))
        case "added-infos"      ⇒ Some(diffConstraints(previous, current, InfoLevel))
        case "removed-infos"    ⇒ Some(diffConstraints(current, previous, InfoLevel))
    }
}