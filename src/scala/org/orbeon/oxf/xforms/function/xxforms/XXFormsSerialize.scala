/**
 *  Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.saxon.functions.Serialize
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.expr.PathMap

class XXFormsSerialize extends Serialize {
    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMapNodeSet) = {

        argument(0).addToPathMap(pathMap, pathMapNodeSet) match {
            case result: PathMap.PathMapNodeSet =>
                // TODO: This is a temporary fix: only the root node of the tree to serialize is marked as atomized so that
                // we can gather dependent values. But in fact the expression is in fact dependent on the entire subtree.
                result.setAtomized
            case _ => // NOP
        }

        // Don't forget the second argument
        argument(1).addToPathMap(pathMap, pathMapNodeSet)

        // We are an atomic type
        null
    }
}
