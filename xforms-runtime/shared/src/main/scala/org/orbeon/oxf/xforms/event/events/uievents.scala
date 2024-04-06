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

import org.orbeon.dom.QName
import org.orbeon.oxf.xforms.analysis.model.StaticBind
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.{EventCollector, XFormsEventTarget}
import org.orbeon.xforms.analysis.model.ValidationLevel
import shapeless.syntax.typeable._


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
  def this(target: XFormsEventTarget, includes: Set[QName], excludes: Set[QName]) =
    this(
      target,
      Map(
        "includes" -> Some(includes),
        "excludes" -> Some(excludes)
      )
    )

  def includes = propertyOrDefault[Set[QName]]("includes", Set.empty)
  def excludes = propertyOrDefault[Set[QName]]("excludes", Set.empty)
}

class XXFormsBlurEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsUIEvent(XXFORMS_BLUR, target.asInstanceOf[XFormsControl], properties, bubbles = false, cancelable = true) {
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
  def this(target: XFormsEventTarget, creation: Boolean) = this(target, XFormsCreationEvents.properties(creation))
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
  def this(target: XFormsEventTarget, creation: Boolean) = this(target, XFormsCreationEvents.properties(creation))
}

class XFormsRequiredEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsUIEvent(XFORMS_REQUIRED, target.asInstanceOf[XFormsControl], properties) {
  def this(target: XFormsEventTarget, creation: Boolean) = this(target, XFormsCreationEvents.properties(creation))
}

class XFormsOptionalEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsUIEvent(XFORMS_OPTIONAL, target.asInstanceOf[XFormsControl], properties) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsVisibleEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsUIEvent(XXFORMS_VISIBLE, target.asInstanceOf[XFormsControl], properties) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsHiddenEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsUIEvent(XXFORMS_HIDDEN, target.asInstanceOf[XFormsControl], properties) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsInitiallyDisabledEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsUIEvent(XXFORMS_INITIALLY_DISABLED, target.asInstanceOf[XFormsControl], properties) {
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
class XFormsValueChangedEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsUIEvent(XFORMS_VALUE_CHANGED, target.asInstanceOf[XFormsControl], properties) {
  def this(target: XFormsEventTarget) = this(target, XFormsValueChangedEvent.properties(target))
}

private object XFormsValueChangedEvent {

  val XXFValue = xxfName("value")

  def properties(target: XFormsEventTarget): PropertyGetter = {
    case XXFValue => target.cast[XFormsValueControl].flatMap(_.valueOpt(EventCollector.Throw))
  }
}

class XXFormsConstraintsChangedEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsUIEvent(XXFORMS_CONSTRAINTS_CHANGED, target.asInstanceOf[XFormsControl], properties) {
  def this(target: XFormsEventTarget, level: Option[ValidationLevel], previous: List[StaticBind.MIP], current: List[StaticBind.MIP]) =
    this(target, XXFormsConstraintsChangedEvent.properties(level, previous, current))
}

private object XXFormsConstraintsChangedEvent {

  def validationsForLevel(current: List[StaticBind.MIP], level: ValidationLevel) =
    current filter (_.level == level) map (_.id)

  def diffValidations(previous: List[StaticBind.MIP], current: List[StaticBind.MIP], level: ValidationLevel) = {
    val previousIds = previous.map(_.id).toSet
    validationsForLevel(current, level) filterNot previousIds
  }

  def properties(level: Option[ValidationLevel], previous: List[StaticBind.MIP], current: List[StaticBind.MIP]): PropertyGetter = {
    case "level"            => level map (_.entryName)
    case "constraints"      => Option(current map (_.id))
    case "errors"           => Some(validationsForLevel(current, ValidationLevel.ErrorLevel))
    case "warnings"         => Some(validationsForLevel(current, ValidationLevel.WarningLevel))
    case "infos"            => Some(validationsForLevel(current, ValidationLevel.InfoLevel))
    case "added-errors"     => Some(diffValidations(previous, current, ValidationLevel.ErrorLevel))
    case "removed-errors"   => Some(diffValidations(current, previous, ValidationLevel.ErrorLevel))
    case "added-warnings"   => Some(diffValidations(previous, current, ValidationLevel.WarningLevel))
    case "removed-warnings" => Some(diffValidations(current, previous, ValidationLevel.WarningLevel))
    case "added-infos"      => Some(diffValidations(previous, current, ValidationLevel.InfoLevel))
    case "removed-infos"    => Some(diffValidations(current, previous, ValidationLevel.InfoLevel))
  }
}
private object XFormsCreationEvents {

  val XXCreation = xxfName("creation")

  def properties(creation: Boolean): PropertyGetter = {
    case XXCreation => Some(creation)
  }
}