/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._

import scala.annotation.tailrec


object XXFormsResourceSupport {

  private val IndexRegex = """(\d+)""".r

  def splitResourceName(s: String): List[String] =
    s.splitTo[List](".")

  def flattenResourceName(s: String): List[String] =
    splitResourceName(s) filter SaxonUtils.isValidNCName

  // Hand-made simple path search
  //
  // - path *must* have the form `foo.bar.2.baz` (names with optional index parts)
  // - each path element must be a NCName (non-qualified) except for indexes
  // - as in XPath, non-qualified names mean "in no namespace"
  //
  // NOTE: Ideally should check if this is faster than using a pre-compiled Saxon XPath expression!
  def pathFromTokens(context: om.NodeInfo, tokens: List[String]): List[om.NodeInfo] = {

      @tailrec
      def findChild(parents: List[om.NodeInfo], tokens: List[String]): List[om.NodeInfo] =
        tokens match {
          case Nil => parents
          case token :: restTokens =>
            parents match {
              case Nil => Nil
              case parents =>
                token match {
                  case IndexRegex(index) =>
                    findChild(List(parents(index.toInt)), restTokens)
                  case path if SaxonUtils.isValidNCName(path) =>
                    findChild(parents / token toList, restTokens)
                  case _ =>
                    throw new IllegalArgumentException(s"invalid resource path `${tokens mkString "."}`")
                }
            }
        }

      findChild(List(context), tokens)
  }

  def findResourceElementForLang(resourcesElement: om.NodeInfo, requestedLang: String): Option[om.NodeInfo] = {
    val availableLangs = resourcesElement / "resource" /@ "lang"
    availableLangs find (_ === requestedLang) orElse availableLangs.headOption flatMap (_.parentOption)
  }
}