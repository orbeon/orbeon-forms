/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xforms

import org.orbeon.oxf.util.StringUtils._

import scala.collection.compat._


// Structured representation of an id
case class XFormsId(staticId: String, containers: List[String], iterations: List[Int]) {

  def toEffectiveId: String =
    (containers :+ staticId).mkString(Constants.ComponentSeparatorString) +
      (if (iterations.isEmpty) "" else Constants.RepeatSeparatorString + iterations.mkString(Constants.RepeatIndexSeparatorString))

  def isSamePrefixedId(other: XFormsId): Boolean =
    staticId == other.staticId && containers == other.containers

  def isRepeated: Boolean =
    iterations.nonEmpty

  def isRepeatNeighbor(other: XFormsId): Boolean =
    isRepeated && other.isRepeated &&
    isSamePrefixedId(other)        &&
    iterations.init == other.iterations.init
}

// Utilities for handling XForms ids. For historical reasons, we manipulate id parts as strings in most cases. This
// is probably not efficient in many cases. Also, we use `Array` as this was converted from Java. We should consider
// converting to `List`.
object XFormsId {

  def fromEffectiveId(effectiveId: String): XFormsId =
    XFormsId(
      getStaticIdFromId(effectiveId),
      getEffectiveIdPrefixParts(effectiveId).to(List),
      getEffectiveIdSuffixParts(effectiveId).to(List)
    )

  /**
    * Return the prefix of an effective id, e.g. "" or "foo$bar$". The prefix returned does end with a separator.
    *
    * @param effectiveId effective id to check
    * @return prefix if any, "" if none, null if effectiveId was null
    */
  def getEffectiveIdPrefix(effectiveId: String): String = {

    if (effectiveId eq null)
      return null

    val prefixIndex = effectiveId.lastIndexOf(Constants.ComponentSeparator)

    if (prefixIndex != -1)
      effectiveId.substring(0, prefixIndex + 1)
    else
      ""
  }

  /**
    * Return whether the effective id has a suffix.
    *
    * @param effectiveId effective id to check
    * @return true iif the effective id has a suffix
    */
  def hasEffectiveIdSuffix(effectiveId: String): Boolean =
    (effectiveId ne null) && effectiveId.indexOf(Constants.RepeatSeparator) != -1

  /**
    * Return the suffix of an effective id, e.g. "" or "2-5-1". The suffix returned does not start with a separator.
    *
    * @param effectiveId effective id to check
    * @return suffix if any, "" if none, null if effectiveId was null
    */
  def getEffectiveIdSuffix(effectiveId: String): String = {

    if (effectiveId eq null)
      return null

    val suffixIndex = effectiveId.indexOf(Constants.RepeatSeparator)

    if (suffixIndex != -1)
      effectiveId.substring(suffixIndex + 1)
    else
      ""
  }

  /**
    * Return the suffix of an effective id, e.g. "" or ".2-5-1". The suffix returned starts with a separator.
    *
    * @param effectiveId effective id to check
    * @return suffix if any, "" if none, null if effectiveId was null
    */
  def getEffectiveIdSuffixWithSeparator(effectiveId: String): String = {

    if (effectiveId eq null)
      return null

    val suffixIndex = effectiveId.indexOf(Constants.RepeatSeparator)

    if (suffixIndex != -1)
      effectiveId.substring(suffixIndex)
    else
      ""
  }

  /**
    * Return an effective id's prefixed id, i.e. the effective id without its suffix, e.g.:
    *
    *   `foo$bar$my-input.1-2` => `foo$bar$my-input`
    *
    * @param effectiveId effective id to check
    * @return effective id without its suffix, null if effectiveId was null
    */
  def getPrefixedId(effectiveId: String): String = {

    if (effectiveId eq null)
      return null

    val suffixIndex = effectiveId.indexOf(Constants.RepeatSeparator)

    if (suffixIndex != -1)
      effectiveId.substring(0, suffixIndex)
    else
      effectiveId
  }

  /**
    * Return an effective id without its prefix, e.g.:
    *
    * - `foo$bar$my-input` => `my-input`
    * - `foo$bar$my-input.1-2` => `my-input.1-2`
    *
    * @param effectiveId effective id to check
    * @return effective id without its prefix, null if effectiveId was null
    */
  def getEffectiveIdNoPrefix(effectiveId: String): String = {

    if (effectiveId eq null)
      return null

    val prefixIndex =
      effectiveId.lastIndexOf(Constants.ComponentSeparator)

    if (prefixIndex != -1)
      effectiveId.substring(prefixIndex + 1)
    else
      effectiveId
  }

  /**
    * Return the parts of an effective id prefix, e.g. for `foo$bar$my-input` return `Array("foo", "bar")`
    *
    * @param effectiveId effective id to check
    * @return array of parts, empty array if no parts, null if effectiveId was null
    */
  def getEffectiveIdPrefixParts(effectiveId: String): Array[String] = {

    if (effectiveId eq null)
      return null

    val prefixIndex = effectiveId.lastIndexOf(Constants.ComponentSeparator)

    if (prefixIndex != -1)
      effectiveId.substring(0, prefixIndex).splitTo[Array](Constants.ComponentSeparatorString)
    else
      EmptyStringArray
  }

  /**
    * Given a repeat control's effective id, compute the effective id of an iteration.
    *
    * @param repeatEffectiveId repeat control effective id
    * @param iterationIndex    repeat iteration
    * @return repeat iteration effective id
    */
  def getIterationEffectiveId(repeatEffectiveId: String, iterationIndex: Int): String = {

    val parentSuffix = getEffectiveIdSuffixWithSeparator(repeatEffectiveId)
    val iterationPrefixedId = getPrefixedId(repeatEffectiveId) + "~iteration"

    if (parentSuffix == "") {
      // E.g. foobar => foobar~iteration.3
      iterationPrefixedId + Constants.RepeatSeparator + iterationIndex
    } else {
      // E.g. foobar.3-7 => foobar~iteration.3-7-2
      iterationPrefixedId + parentSuffix + Constants.RepeatIndexSeparatorString + iterationIndex
    }
  }

  /**
    * Return the parts of an effective id suffix, e.g. for `$foo$bar.3-1-5` return `Array(3, 1, 5)`
    *
    * @param effectiveId effective id to check
    * @return array of parts, empty array if no parts, null if effectiveId was null
    */
  def getEffectiveIdSuffixParts(effectiveId: String): Array[Int] = {

    if (effectiveId eq null)
      return null

    val suffixIndex = effectiveId.indexOf(Constants.RepeatSeparator)
    if (suffixIndex != -1) {
      val stringResult = effectiveId.substring(suffixIndex + 1).splitTo[Array](Constants.RepeatIndexSeparatorString)
      val result = new Array[Int](stringResult.length)
      var i = 0
      while (i < stringResult.length) {
        val currentString = stringResult(i)
        result(i) = currentString.toInt
        i += 1
      }
      result
    }
    else
      EmptyIntArray
  }

  def buildEffectiveId(prefixedId: String, iterations: Iterable[Int]): String =
    if (iterations.isEmpty)
      prefixedId
    else
      prefixedId + Constants.RepeatSeparator + (iterations mkString Constants.RepeatIndexSeparatorString)

  /**
    * Compute an effective id based on an existing effective id and a static id. E.g.:
    *
    * `foo$bar.1-2` and `baz` => `foo$baz.1-2`
    *
    * @param baseEffectiveId base effective id
    * @param staticId        static id
    * @return effective id
    */
  def getRelatedEffectiveId(baseEffectiveId: String, staticId: String): String = {

    val prefix = getEffectiveIdPrefix(baseEffectiveId)
    val suffixIndex = baseEffectiveId.indexOf(Constants.RepeatSeparator)

    val suffix =
      if (suffixIndex == -1)
        ""
      else
          baseEffectiveId.substring(suffixIndex)

    prefix + staticId + suffix
  }

  /**
    * Return the static id associated with the given id, removing suffix and prefix if present.
    *
    * `foo$bar.1-2` => `bar`
    *
    * @param anyId id to check
    * @return static id, or null if anyId was null
    */
  def getStaticIdFromId(anyId: String): String =
    getPrefixedId(getEffectiveIdNoPrefix(anyId))

  /**
    * Append a new string to an effective id.
    *
    * `foo$bar.1-2` and `-my-ending` => `foo$bar-my-ending.1-2`
    *
    * @param effectiveId base effective id
    * @param ending      new ending
    * @return effective id
    */
  def appendToEffectiveId(effectiveId: String, ending: String): String =
    getPrefixedId(effectiveId) + ending + getEffectiveIdSuffixWithSeparator(effectiveId)

  /**
    * Check if an id is a static id, i.e. if it does not contain component/hierarchy separators.
    *
    * @param id static id to check
    * @return true if the id is a static id
    */
  def isStaticId(id: String): Boolean =
    (id ne null) && id.indexOf(Constants.ComponentSeparator) == -1 && ! hasEffectiveIdSuffix(id)

  def isEffectiveId(id: String): Boolean =
    (id ne null) && id.indexOf(Constants.ComponentSeparator) != -1 || hasEffectiveIdSuffix(id)

  /**
    * Whether the id is an absolute id.
    */
  def isAbsoluteId(id: String): Boolean = {
    val length = id.length
    length >= 3 && id.charAt(0) == Constants.AbsoluteIdSeparator && id.charAt(length - 1) == Constants.AbsoluteIdSeparator
  }

  /**
    * Convert an absolute id to an effective id.
    */
  def absoluteIdToEffectiveId(absoluteId: String): String = {
    assert(isAbsoluteId(absoluteId))
    absoluteId.substring(1, absoluteId.length - 1)
  }

  /**
    * Convert an effective id to an absolute id.
    */
  def effectiveIdToAbsoluteId(effectiveId: String): String =
    Constants.AbsoluteIdSeparator + effectiveId + Constants.AbsoluteIdSeparator

  // Means "not under an XBL component or nested part"
  def isTopLevelId(id: String): Boolean =
    id == getStaticIdFromId(id)

  private val EmptyStringArray = new Array[String](0)
  private val EmptyIntArray    = new Array[Int](0)
}
