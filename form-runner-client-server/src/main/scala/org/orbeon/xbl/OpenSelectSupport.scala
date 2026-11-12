/**
  * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.*

import scala.util.Try


object OpenSelectSupport {

  private val EmailRegex  = """^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""".r
  private val NumberRegex = """^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$""".r

  def encodeToken(raw: String): Option[String] =
    Option(raw).flatMap(_.trimAllToOpt).map { trimmed =>
      val collapsed = trimmed.splitTo[List]().mkString(" ")
      val sb        = new java.lang.StringBuilder(collapsed.length + 8)
      var i         = 0
      while (i < collapsed.length) {
        collapsed.charAt(i) match {
          case '%' => sb.append("%25")
          case '+' => sb.append("%2B")
          case ' ' => sb.append('+')
          case c   => sb.append(c)
        }
        i += 1
      }
      sb.toString
    }

  def decodeToken(token: String): String =
    Option(token).fold("") { t =>
      val sb  = new java.lang.StringBuilder(t.length)
      var i   = 0
      val len = t.length
      while (i < len) {
        t.charAt(i) match {
          case '+' =>
            sb.append(' ')
            i += 1
          case '%' if i + 2 < len =>
            val h1 = t.charAt(i + 1)
            val h2 = t.charAt(i + 2)
            if (h1 == '2' && (h2 == 'B' || h2 == 'b')) {
              sb.append('+')
              i += 3
            } else if (h1 == '2' && h2 == '5') {
              sb.append('%')
              i += 3
            } else {
              sb.append('%')
              i += 1
            }
          case c =>
            sb.append(c)
            i += 1
        }
      }
      sb.toString
    }

  def partitionTokens(
    boundValue: String,
    itemValues: Set[String]
  ): (List[String], List[String]) = {
    val tokens = Option(boundValue).getOrElse("").splitTo[List]().distinct
    tokens.partition(itemValues.contains)
  }

  def closedTokensJoined(boundValue: String, itemValuesJoined: String): String = {
    val itemValues    = Option(itemValuesJoined).getOrElse("").splitTo[Set]()
    val (closed, _)   = partitionTokens(boundValue, itemValues)
    closed.mkString(" ")
  }

  def openTokensJoined(boundValue: String, itemValuesJoined: String): String = {
    val itemValues  = Option(itemValuesJoined).getOrElse("").splitTo[Set]()
    val (_, open)   = partitionTokens(boundValue, itemValues)
    open.mkString(" ")
  }

  def combineTokens(closedTokensJoined: String, openTokensJoined: String): String = {
    val closed = Option(closedTokensJoined).getOrElse("").splitTo[List]()
    val open   = Option(openTokensJoined).getOrElse("").splitTo[List]()
    (closed ++ open).distinct.mkString(" ")
  }

  def isValidTag(decoded: String, validationType: String, pattern: String): Boolean = {
    val trimmed    = Option(decoded).flatMap(_.trimAllToOpt).getOrElse("")
    val normType   = Option(validationType).flatMap(_.trimAllToOpt).getOrElse("")
    val rawPattern = Option(pattern).flatMap(_.trimAllToOpt)

    if (trimmed.isEmpty)
      false
    else
      normType match {
        case "" | "text" => true
        case "email"     => EmailRegex.matches(trimmed)
        case "url"       => UrlSupport.isValidWebUrl(trimmed)
        case "number"    => NumberRegex.matches(trimmed)
        case "pattern"   => rawPattern.forall(p => Try(trimmed.matches(p)).getOrElse(false))
        case _           => true
      }
  }

  def parseMaxTags(maxTagsStr: String): Option[Int] =
    Option(maxTagsStr).flatMap(_.trimAllToOpt).flatMap(_.toIntOption).filter(_ > 0)

  def isValidTags(
    boundValue    : String,
    validationType: String,
    pattern       : String,
    maxTagsStr    : String
  ): Boolean = {
    val tokens    = Option(boundValue).getOrElse("").splitTo[List]().distinct
    val maxTagsOk = parseMaxTags(maxTagsStr).forall(tokens.size <= _)
    val allTagsOk = tokens.forall(t => isValidTag(decodeToken(t), validationType, pattern))
    maxTagsOk && allTagsOk
  }
}

object OpenSelectSupportJava {

  //@XPathFunction
  def decodeToken(token: String): String =
    OpenSelectSupport.decodeToken(token)

  //@XPathFunction
  def closedTokensJoined(boundValue: String, itemValuesJoined: String): String =
    OpenSelectSupport.closedTokensJoined(boundValue, itemValuesJoined)

  //@XPathFunction
  def openTokensJoined(boundValue: String, itemValuesJoined: String): String =
    OpenSelectSupport.openTokensJoined(boundValue, itemValuesJoined)

  //@XPathFunction
  def combineTokens(closedTokensJoined: String, openTokensJoined: String): String =
    OpenSelectSupport.combineTokens(closedTokensJoined, openTokensJoined)

  //@XPathFunction
  def isValidTags(
    boundValue    : String,
    validationType: String,
    pattern       : String,
    maxTagsStr    : String
  ): Boolean =
    OpenSelectSupport.isValidTags(boundValue, validationType, pattern, maxTagsStr)
}
