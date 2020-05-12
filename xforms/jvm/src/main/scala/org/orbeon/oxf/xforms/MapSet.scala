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

import scala.collection.generic.Growable
import scala.collection.{mutable => m}

/**
 * Collection containing a number of distinct sets indexed by a map.
 */
class MapSet[A, B] extends Iterable[(A, B)] with Growable[(A, B)] {

  val map = new m.LinkedHashMap[A, m.LinkedHashSet[B]]

  def put(a: A, b: B): Unit = {
    (map.get(a) match {
      case Some(set) => set
      case None =>
        val newSet = new m.LinkedHashSet[B]
        map.put(a, newSet)
        newSet
    }) += b
  }

  // NOTE: should use ++ operator, but harder to implement properly
  def combine(other: MapSet[A, B]): MapSet[A, B] = {
    val result = new MapSet[A, B]
    this foreach (entry => result.put(entry._1, entry._2))
    other foreach (entry => result.put(entry._1, entry._2))
    result
  }

  // Iterate over all (A, B) tuples
  def iterator: Iterator[(A, B)] = map.iterator flatMap { case (key, values) => values.iterator map ((key, _)) }

  // Growable
  def +=(elem: (A, B)): this.type = { put(elem._1, elem._2); this }
  def clear(): Unit = map.clear()
}

object MapSet {
  // TODO: This should be immutable instead of checking an exception at runtime!
  private object EmptyMapSet extends MapSet[Any, Any] {
    override def put(a: Any, b: Any): Unit = throw new OXFException("Can't add items to empty MapSet")
  }

  def empty[A, B]: MapSet[A, B] = EmptyMapSet.asInstanceOf[MapSet[A, B]]
}