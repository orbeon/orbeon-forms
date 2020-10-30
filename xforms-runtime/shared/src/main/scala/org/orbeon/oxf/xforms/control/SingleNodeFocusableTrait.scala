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
package org.orbeon.oxf.xforms.control


trait MaybeFocusableTrait {

  self: XFormsControl =>

  final def directlyFocusableControls: Iterator[XFormsControl] =
    recurseFocusableControls(self) filter (_.isDirectlyFocusable)

  final def directlyFocusableControlsMaybeWithToggle: Iterator[XFormsControl] =
    recurseFocusableControls(self) filter (_.isDirectlyFocusableMaybeWithToggle)

  final def isDirectlyFocusable: Boolean =
    isDirectlyFocusableMaybeWithToggle && ! Focus.isHidden(this)

  def isDirectlyFocusableMaybeWithToggle: Boolean = false
  def followDescendantsForFocus: Iterator[XFormsControl] = Iterator.empty

  private def recurseFocusableControls(t: XFormsControl): Iterator[XFormsControl] =
    Iterator(t) ++ (t.followDescendantsForFocus flatMap recurseFocusableControls)
}

trait SingleNodeFocusableTrait extends VisitableTrait with MaybeFocusableTrait {

  self: XFormsSingleNodeControl =>

  override def isDirectlyFocusableMaybeWithToggle: Boolean =
    self.isRelevant && ! self.isReadonly
}

trait ReadonlySingleNodeFocusableTrait extends VisitableTrait with MaybeFocusableTrait {

  self: XFormsSingleNodeControl =>

 override def isDirectlyFocusableMaybeWithToggle: Boolean =
   self.isRelevant
}