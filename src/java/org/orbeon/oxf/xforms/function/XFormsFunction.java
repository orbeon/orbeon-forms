/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.saxon.functions.SystemFunction;

/**
 * Base class for all XForms functions.
 */
abstract public class XFormsFunction extends SystemFunction {

    private XFormsContainingDocument xformsContainingDocument;
    private XFormsModel xformsModel;
    private XFormsControls xformsControls;

    protected XFormsFunction() {
    }

    public XFormsModel getXFormsModel() {
        return xformsModel;
    }

    public void setXFormsModel(XFormsModel xFormsModel) {
        this.xformsModel = xFormsModel;
        this.xformsContainingDocument = xFormsModel.getContainingDocument();
    }

    public XFormsControls getXFormsControls() {
        return xformsControls;
    }

    public void setXFormsControls(XFormsControls xFormsControls) {
        this.xformsControls = xFormsControls;
        this.xformsContainingDocument = xFormsControls.getContainingDocument();
    }

    public XFormsContainingDocument getXFormsContainingDocument() {
        return xformsContainingDocument;
    }
}
