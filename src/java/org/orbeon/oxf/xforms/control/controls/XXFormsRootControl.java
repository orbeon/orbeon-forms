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
package org.orbeon.oxf.xforms.control.controls;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsNoSingleNodeContainerControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;

/**
 * Temporarily represents a root for other controls.
 */
public class XXFormsRootControl extends XFormsNoSingleNodeContainerControl implements XFormsPseudoControl {
    public XXFormsRootControl(XFormsContainingDocument containingDocument) {
        super(containingDocument, null, null, "root", null);
        // Call this to set initial relevance to true
        setBindingContext(null);
    }

    @Override
    public boolean wasRelevant() {
        // Root control is always relevant
        return true;
    }
}
