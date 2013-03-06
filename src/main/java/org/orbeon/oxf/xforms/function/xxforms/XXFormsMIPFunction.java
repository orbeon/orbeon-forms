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
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.PathMap;
import org.orbeon.saxon.expr.StaticProperty;

public abstract class XXFormsMIPFunction extends XFormsFunction {

    @Override
    public int getIntrinsicDependencies() {
        // Only depend on context if there is no parameter
        int d = super.getIntrinsicDependencies();
        if (argument.length == 0) {
            d |= StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
        }
        return d;
    }

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        return super.addToPathMap(pathMap, pathMapNodeSet);
        // TODO: function doesn't actually depend on the value but on the MIP of the node. Would need more complex
        // mechanism to handle this, OR we could have MIP changes behave as value changes, but that could have a
        // negative performance impact when MIP functions are not used.
//        PathMap.PathMapNodeSet result = argument[0].addToPathMap(pathMap, pathMapNodeSet);
//        if (result != null) {
//            result.setAtomized();
//        }
//        return null;
    }
}
