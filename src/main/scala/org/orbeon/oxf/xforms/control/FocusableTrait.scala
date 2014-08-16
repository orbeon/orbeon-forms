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

import org.orbeon.oxf.util.ScalaUtils._

// Trait indicating that the control can directly receive keyboard focus
// NOTE: This is different from supporting `setFocus()`. `setFocus()` can apply to groups, etc. which by themselves
// do not directly receive focus. Concretely, only leaf controls are focusable, but not all of them. For example,
// an output control is not focusable.
trait FocusableTrait extends VisitableTrait {

    self ⇒

    // Whether the control is actually focusable depending on relevance and readonliness
    override def isFocusable = self match {
        case single: XFormsSingleNodeControl ⇒ isRelevant && ! single.isReadonly
        case _                               ⇒ isRelevant
    }

    // Return None if inputOnly is true. XFormsInputControl must override this behavior.
    override def focusableControls: Iterator[XFormsControl] =
        isFocusable iterator self
}