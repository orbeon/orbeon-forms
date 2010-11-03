/**
 *   Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms

import org.orbeon.oxf.common.OXFException
import collection.mutable.{LinkedHashSet, LinkedHashMap}
import collection.generic.Growable

/**
 * Collection containing a number of distinct sets indexed by a map.
 */
class MapSet[A, B] extends Traversable[(A, B)] with Growable[(A, B)] {

    private val map = new LinkedHashMap[A, LinkedHashSet[B]]

    def put(a: A, b: B) {
        (map.get(a) match {
            case Some(set) => set
            case None =>
                val newSet = new LinkedHashSet[B]
                map.put(a, newSet)
                newSet
        }) += b
    }

    def intersects(other: MapSet[A, B]): Boolean = {

        val intersection = map.keySet & other.map.keySet

        if (intersection.isEmpty)
            return false

        for (key <- intersection)
            if ((map.get(key).get & other.map.get(key).get).nonEmpty)
                return true

        false
    }

    def keys = map.keys

    // NOTE: should use ++ operator, but harder to implement properly
    def combine(other: MapSet[A, B]): MapSet[A, B] = {
        val result = new MapSet[A, B]
        this foreach (entry => result.put(entry._1, entry._2))
        other foreach (entry => result.put(entry._1, entry._2))
        result
    }

    // TraversableLike
    // Iterate over all (A, B) tuples
    def foreach[U](f: ((A, B)) => U) = map foreach (mapEntry => mapEntry._2 foreach (setEntry => f((mapEntry._1, setEntry))))

    // Growable
    def +=(elem: (A, B)) = {put(elem._1, elem._2); this}
    def clear(): Unit = map.clear()
}

object MapSet {
    // TODO: This should be immutable instead of checking an exception at runtime!
    private object EmptyMapSet extends MapSet[Any, Any] {
        override def put(a: Any, b: Any) = throw new OXFException("Can't add items to empty MapSet")
    }

    def empty[A, B] = EmptyMapSet.asInstanceOf[MapSet[A, B]]
}