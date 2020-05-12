/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import scala.collection.mutable

// IdGenerator which uses a BitSet.
//
// The working assumption is that automatic ids will typically be allocated in a continuous way. Using a regular Set
// to hold this information is a waste. We also assume that there are at most a few thousand ids. Over that, ids are
// placed in a Set. Ids which are not automatically generated are also added to a Set.
class IdGenerator(var _nextSequenceNumber: Int = 1) {

  import IdGenerator._

  private val _bits   = mutable.BitSet()
  private val _others = mutable.Set[String]()

  def nextSequenceNumber = _nextSequenceNumber

  def ids = (_bits.iterator map (i => AutomaticIdPrefix + (i + 1).toString)) ++ _others.iterator

  def contains(id: String): Boolean =  id match {
    case AutomaticIdFormat(digits) if digits.toInt <= MaxBits =>
      _bits contains (digits.toInt - 1)
    case _ =>
      _others contains id
  }

  def add(id: String): Unit = id match {
    case AutomaticIdFormat(digits) if digits.toInt <= MaxBits =>
      _bits += digits.toInt - 1
    case _ =>
      _others += id
  }

  // Skip existing ids to handle these cases:
  //
  // - user uses attribute of the form xf-*
  // - XBL copies id attributes from bound element, so within template the id may be of the form xf-*
  def nextId(): String = {

    def containsAutomaticId(id: Int): Boolean =
      if (id <= MaxBits)
        _bits(id - 1)
      else
        _others.contains(AutomaticIdPrefix + id)

    while (containsAutomaticId(_nextSequenceNumber))
      _nextSequenceNumber += 1

    val result = AutomaticIdPrefix + _nextSequenceNumber
    _nextSequenceNumber += 1
    result
  }
}

private object IdGenerator {
  val MaxBytes = 1024         // 1024 means up to xf-8096
  val MaxBits  = MaxBytes * 8

  val AutomaticIdPrefix = "xf-"
  val AutomaticIdFormat = "xf-(\\d+)".r
}