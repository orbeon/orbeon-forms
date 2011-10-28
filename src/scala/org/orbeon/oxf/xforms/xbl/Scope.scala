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
package org.orbeon.oxf.xforms.xbl

import collection.mutable.HashMap

class Scope(val parent: Scope, val scopeId: String) {

    assert((parent ne null) || scopeId == "")

    val fullPrefix = if (scopeId.length == 0) "" else scopeId + '$'

    private val idMap = new HashMap[String, String]

    if (parent eq null)
        idMap += "#document" → "#document"
    
    def isTopLevelScope = scopeId.length == 0

    // Return the prefixed id of the given control static id within this scope, null if not found
    def prefixedIdForStaticId(staticId: String) = idMap.get(staticId).orNull

    def contains(staticId: String) = idMap.contains(staticId)

    def += (kv: (String, String)) = kv match {
        case (staticId, prefixedId) ⇒
            // Index static id => prefixed id by scope
            idMap += staticId -> prefixedId
    }

    // Equality is defined purely based on the scope id
    override def hashCode = scopeId.hashCode

    override def equals(that: Any) = that match {
        case thatScope: Scope ⇒ scopeId == thatScope.scopeId
        case _ ⇒ false
    }

    override def toString = "Scope(" + scopeId + ")"
}