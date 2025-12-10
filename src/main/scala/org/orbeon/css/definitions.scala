/**
 * Copyright (C) 2025 Orbeon, Inc.
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
package org.orbeon.css

import com.helger.css.ECSSVersion
import com.helger.css.tools.MediaQueryTools

import java.net.URI
import scala.jdk.CollectionConverters.IterableHasAsScala


// A media query, from the most simple ones consisting of a media type only (e.g. "all", "screen", "print") to more
// complex queries (e.g. "screen and (min-width: 800px)", "(min-width: 768px) and (max-width: 1024px)", etc.)
case class MediaQuery(mediaQuery: String) {
  def and(that: MediaQuery): MediaQuery =
    MediaQuery(
      (this.mediaQuery.trim, that.mediaQuery.trim) match {
        case ("all", r)       => r
        case (l, "all")       => l
        case (l, r) if l == r => l
        case (l, r)           => s"$l and $r"
      }
    )

  def simpleMatch(that: MediaQuery): Boolean =
    (this.mediaQuery.trim, that.mediaQuery.trim) match {
      case ("all", _)       => true
      case (_, "all")       => true
      case (l, r) if l == r => true
      case (_, _)           => false
    }
}

object MediaQuery {
  val AllMediaQuery   : MediaQuery = MediaQuery(MediaType.All)
  val PrintMediaQuery : MediaQuery = MediaQuery(MediaType.Print)
  val ScreenMediaQuery: MediaQuery = MediaQuery(MediaType.Screen)

  // We can find media query lists in media rules in CSS (e.g. @media <media-query-list> { ... }) or in media
  // attributes in HTML documents (e.g. <style media="<media-query-list>"> or <link media="<media-query-list>">)
  def mediaQueries(s: String): List[MediaQuery] =
    MediaQueryTools.parseToMediaQuery(s, ECSSVersion.LATEST).asScala.toList.map(q => MediaQuery(q.getAsCSSString))

  def simplified(mediaQueries: List[MediaQuery]): List[MediaQuery] = {
    val trimmed = mediaQueries.map(_.mediaQuery.trim).filter(_.nonEmpty)

    // List of media query can semantically be understood as OR-connected media queries
    (if (trimmed.contains(MediaType.All)) List(MediaType.All) else trimmed.distinct).map(MediaQuery.apply)
  }

  def and(leftMediaQueries: List[MediaQuery], rightMediaQueries: List[MediaQuery]): List[MediaQuery] =
    simplified(
      for {
        left  <- simplified(leftMediaQueries)
        right <- simplified(rightMediaQueries)
      } yield left.and(right)
    )
}

object MediaType {
  val All    = "all"
  val Print  = "print"
  val Screen = "screen"
}

// A CSS selector, from the most simple ones (e.g. "*", ":root", "div", etc.) to more complex selectors (e.g.
// "input[required]:invalid:not(:focus)", etc.)
case class Selector(selector: String)

// <link> and <style> elements in an HTML document
sealed trait CSSResource { def mediaQueries: List[MediaQuery] }
case class Link (uri: URI   , mediaQueries: List[MediaQuery]) extends CSSResource
case class Style(css: String, mediaQueries: List[MediaQuery]) extends CSSResource

case class VariableDefinition(name: String, value: String, mediaQueries: List[MediaQuery], selectors: List[Selector])

case class VariableDefinitions(variableDefinitions: List[VariableDefinition]) {
  // Naive implementation of variable value lookup (ignore complex media queries and ignore all selectors)
  def variableValue(variableName: String, mediaQuery: MediaQuery, selectors: List[Selector]): Option[String] =
    variableDefinitions.findLast { variableDefinition =>
      // Match based on simple media queries ("print" matches "all" and "print", etc.) and variable name
      variableDefinition.mediaQueries.exists(_.simpleMatch(mediaQuery)) &&
      variableDefinition.name == variableName
    }.map {
      _.value
    }

  def merged(variableDefinitions: VariableDefinitions): VariableDefinitions =
    VariableDefinitions(this.variableDefinitions ++ variableDefinitions.variableDefinitions)
}
