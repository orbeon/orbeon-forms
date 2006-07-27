/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;

/**
 * Represents xforms:repeat iteration information.
 *
 * This is not really a control, but an abstraction for xforms:repeat branches.
 */
public class RepeatIterationControl extends XFormsControl {
    private int iteration;
    public RepeatIterationControl(XFormsContainingDocument containingDocument, XFormsControl parent, int iteration) {
        super(containingDocument, parent, null, "xxforms-repeat-iteration", parent.getEffectiveId());
        this.iteration = iteration;
    }

    public int getIteration() {
        return iteration;
    }

    public boolean equals(Object obj) {
        if (!super.equals(obj))
            return false;
        final RepeatIterationControl other = (RepeatIterationControl) obj;
        return this.iteration == other.iteration;
    }
}
