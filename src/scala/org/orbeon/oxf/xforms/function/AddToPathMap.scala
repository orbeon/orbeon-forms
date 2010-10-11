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
package org.orbeon.oxf.xforms.function

import org.orbeon.saxon.expr._
import scala.collection.JavaConversions._
import org.orbeon.saxon.`type`.AtomicType
import org.orbeon.saxon.functions.SystemFunction

// Rewrite of Saxon addToPathMap in Scala
trait AddToPathMap {

    this: SystemFunction =>

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {
        val dependsOnFocus = (getDependencies & StaticProperty.DEPENDS_ON_FOCUS) != 0
        val attachmentPoint = pathMapNodeSet match {
            case null if dependsOnFocus =>
                // Result is new ContextItemExpression
                val contextItemExpression = new ContextItemExpression
                contextItemExpression.setContainer(getContainer)
                new PathMap.PathMapNodeSet(pathMap.makeNewRoot(contextItemExpression))
            case _ =>
                // All other cases
                if (dependsOnFocus) pathMapNodeSet else null
        }

        val resultNodeSet = new PathMap.PathMapNodeSet
        for (child <- iterateSubExpressions)
            resultNodeSet.addNodeSet(child.asInstanceOf[Expression].addToPathMap(pathMap, attachmentPoint))

        // Handle result differently if result type is atomic or not
        getItemType(getExecutable.getConfiguration.getTypeHierarchy) match {
            case atomicType: AtomicType =>
                // NOTE: Thought it would be right to call setAtomized(), but it isn't! E.g. count() returns an atomic type,
                // but it doesn't mean the result of its argument expression is atomized. sum() does, but that's handled by
                // the atomization of the argument to sum().
//                resultNodeSet.setAtomized()
                // If expression returns an atomic value then any nodes accessed don't contribute to the result
                null
            case _ => resultNodeSet
        }
    }
}