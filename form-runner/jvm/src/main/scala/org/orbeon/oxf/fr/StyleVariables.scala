/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StringUtils.*


// `oxf.fr.style.variable.<name>.<app>.<form>` properties, `<name>` without its `--orbeon-` prefix
object StyleVariables {

  val PropertyPrefix = "oxf.fr.style.variable"

  private val PropertyPrefixTokens      = PropertyPrefix.splitTo[List](".")
  private val VariableNameRE            = "[a-z][a-z0-9]*(-[a-z0-9]+)*".r
  private val StringRE                  = """'[^']*'|"[^"]*"""".r
  private val ForbiddenAnywhereRE       = """[<\\\n\r]""".r
  private val ForbiddenOutsideStringsRE = """['";{}\[\]]|/\*""".r

  def isValidName(name: String): Boolean =
    VariableNameRE.matches(name)

  // Written as is to a style element, so it must not close that element or its own declaration
  def isValidValue(value: String): Boolean = {

    val withoutStrings = StringRE.replaceAllIn(value, " ") // A string can contain anything, including parentheses

    def hasBalancedParentheses: Boolean = {
      val depths =
        withoutStrings.scanLeft(0) {
          case (depth, '(') => depth + 1
          case (depth, ')') => depth - 1
          case (depth, _)   => depth
        }
      depths.forall(_ >= 0) && depths.last == 0
    }

    value.nonEmpty                                                &&
    ForbiddenAnywhereRE.findFirstIn(value).isEmpty                && // Could close the style element, or escape a quote
    ForbiddenOutsideStringsRE.findFirstIn(withoutStrings).isEmpty && // Unclosed string, end of the declaration, comment
    hasBalancedParentheses
  }

  def propertyName(name: String): String =
    s"$PropertyPrefix.$name"

  def declaredPropertyName(
    name       : String,
    app        : String,
    form       : String,
    profileOpt : Option[String]
  )(implicit
    propertySet: PropertySet
  ): String =
    // As written in the properties, with its wildcards, for a variable that `fromProperties` returned
    propertySet.getPropertyOrThrow(s"${propertyName(name)}.$app.$form", profileOpt).name

  def fromProperties(
    app        : String,
    form       : String,
    profileOpt : Option[String]
  )(implicit
    propertySet: PropertySet
  ): List[(String, String)] = {

    // A blank value means no override, so that a more specific property can cancel a less specific one:
    //
    //   oxf.fr.style.variable.accent-color.*.*        = "#222222"
    //   oxf.fr.style.variable.accent-color.acme.sales = ""
    //
    // All forms get #222222, except acme/sales, which keeps the accent color of the theme

    val names =
      propertySet.propertiesStartsWith(PropertyPrefix, matchWildcards = false)
        .map(_.splitTo[List](".").drop(PropertyPrefixTokens.size))
        .collect {
          case name :: _ :: _ :: Nil => name
        }
        .filter(isValidName)
        .distinct
        .sorted

    for {
      name  <- names
      value <- propertySet.getNonBlankString(s"${propertyName(name)}.$app.$form", profileOpt)
    } yield
      name -> value.replaceAll("""\s+""", " ") // Collapse whitespace, including line breaks
  }

  // Also the first statement of the built-in themes
  val LayerOrder = "@layer orbeon-theme, orbeon-properties, orbeon-theme-variants, orbeon-properties-variants;"

  def toCss(light: List[(String, String)]): Option[String] = {

    def declarations(variables: List[(String, String)]): String =
      variables.map { case (name, value) => s"--orbeon-$name: $value;" }.mkString("{ ", " ", " }")

    light.nonEmpty.option(s"$LayerOrder @layer orbeon-properties { .orbeon ${declarations(light)} }")
  }
}
