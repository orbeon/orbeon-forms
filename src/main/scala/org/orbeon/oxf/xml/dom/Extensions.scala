/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.xml.dom

import org.orbeon.dom.{Element, Namespace, QName}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.StringUtils.StringOps


object Extensions {

  implicit class DomElemOps[E](private val e: Element) extends AnyVal {

    def resolveStringQName(qNameString: String, unprefixedIsNoNamespace: Boolean): QName =
      Extensions.resolveQName(e.allInScopeNamespacesAsStrings, qNameString, unprefixedIsNoNamespace)

    def resolveAttValueQName(attName: QName, unprefixedIsNoNamespace: Boolean): QName =
      resolveStringQName(e.attributeValue(attName), unprefixedIsNoNamespace)

    def resolveAttValueQName(attName: String, unprefixedIsNoNamespace: Boolean): QName =
      resolveStringQName(e.attributeValue(attName), unprefixedIsNoNamespace)
  }

  /**
    * Extract a QName from a string value, given namespace mappings. Return null if the text is empty.
    *
    * @param namespaces              prefix -> URI mappings
    * @param qNameStringOrig         QName to analyze
    * @param unprefixedIsNoNamespace if true, an unprefixed value is in no namespace; if false, it is in the default namespace
    * @return a QName object or null if not found
    */
  def resolveQName(
    namespaces              : Map[String, String],
    qNameStringOrig         : String,
    unprefixedIsNoNamespace : Boolean
  ): QName = {

    if (qNameStringOrig eq null)
      return null

    val qNameString = qNameStringOrig.trimAllToEmpty

    if (qNameString.isEmpty)
      return null

    val (localName, prefix, namespaceURI) =
      qNameString.indexOf(':') match {
        case 0 =>
          throw new IllegalArgumentException(s"Empty prefix for QName: `$qNameString`")
        case -1 =>
          val prefix = ""
          (
            qNameString,
            prefix,
            if (unprefixedIsNoNamespace) "" else namespaces.getOrElse(prefix, "")
          )
        case colonIndex =>
          val prefix = qNameString.substring(0, colonIndex)
          (
            qNameString.substring(colonIndex + 1),
            prefix,
            namespaces.getOrElse(prefix, throw new OXFException(s"No namespace declaration found for prefix: `$prefix`"))
          )
      }

    QName(localName, Namespace(prefix, namespaceURI))
  }

  def resolveAttValueQNameJava(elem: Element, attributeQName: QName, unprefixedIsNoNamespace: Boolean): QName =
    elem.resolveStringQName(elem.attributeValue(attributeQName), unprefixedIsNoNamespace)

  def resolveAttValueQNameJava(elem: Element, attributeName: String): QName =
    elem.resolveStringQName(elem.attributeValue(attributeName), unprefixedIsNoNamespace = true)

  def resolveTextValueQNameJava(elem: Element, unprefixedIsNoNamespace: Boolean): QName =
    resolveQName(elem.allInScopeNamespacesAsStrings, elem.getStringValue, unprefixedIsNoNamespace)
}
