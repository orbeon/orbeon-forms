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
package org.orbeon.xforms.xbl

import collection.mutable
import org.orbeon.xforms.Constants.ComponentSeparator

/**
 * Represent an XBL scope, that is a set of ids associated with elements that can see each other.
 *
 * - within a scope, static ids must be unique
 * - static ids might have prefixed ids in different containing scopes
 *
 * 2021-03-01: This is not a very good representation. It contains a lot of data while in most cases,
 * ids are within the scope directly. See also `XFormsStaticStateSerializer`.
 */
class Scope(val parent: Option[Scope], val scopeId: String) {

  require(parent.isDefined && scopeId.nonEmpty || parent.isEmpty && scopeId.isEmpty)

  private val _idMap = mutable.HashMap[String, String]()
  def idMap: collection.Map[String, String] = _idMap

  val fullPrefix: String = if (isTopLevelScope) "" else scopeId + ComponentSeparator

  def isTopLevelScope                           : Boolean        = scopeId.isEmpty
  def prefixedIdForStaticId(staticId: String)   : String         = _idMap.get(staticId).orNull
  def prefixedIdForStaticIdOpt(staticId: String): Option[String] = _idMap.get(staticId)
  def contains(staticId: String)                : Boolean        = _idMap.contains(staticId)

  // Add a static id -> prefixed id mapping
  def += (kv: (String, String)): Unit = kv match {
    case (staticId, prefixedId) =>
      _idMap += staticId -> prefixedId
  }

  // Remove a mapping by static id
  def -= (staticId: String): Unit =
    _idMap -= staticId

  // Equality is defined purely based on the scope id
  override def hashCode: Int = scopeId.hashCode

  override def equals(that: Any): Boolean = that match {
    case thatScope: Scope => scopeId == thatScope.scopeId
    case _ => false
  }

  override def toString: String = "Scope(" + scopeId + ")"
}