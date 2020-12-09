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
package org.orbeon.oxf.xml

import org.orbeon.oxf.util.CharacterAccumulator
import org.orbeon.oxf.util.Whitespace.Policy
import org.orbeon.oxf.util.WhitespaceMatching.PolicyMatcher
import org.xml.sax.Attributes


// This receiver can perform transformations on incoming character data based on a few policies.
//
// Example of configuration for whitespace preservation in XForms, showing the 3 types of selectors supported:
//
// `xf|instance > *, xf|var, xf|setvalue, xf|value, xxf|sequence, xxf|script, xf|action:not([type=xpath]), xs|schema`
//
// RFE: Support xml:space
//
class WhitespaceXMLReceiver(xmlReceiver: XMLReceiver, startPolicy: Policy, policyMatcher: PolicyMatcher)
    extends ForwardingXMLReceiver(xmlReceiver) {

  private case class StackElement(policy: Policy, parent: Option[(String, String)])

  private var stack = List(StackElement(startPolicy, None))
  private val acc   = new CharacterAccumulator

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

    flushCharacters()

    val elementName = (uri, localname)
    val current     = stack.head
    val nextPolicy  = StackElement(policyMatcher(current.policy, elementName, attributes, current.parent), Some(elementName))

    stack ::= nextPolicy

    super.startElement(uri, localname, qName, attributes)
  }

  override def endElement(uri: String, localname: String, qName: String): Unit = {
    flushCharacters()
    stack = stack.tail

    super.endElement(uri, localname, qName)
  }

  override def characters(ch: Array[Char], start: Int, length: Int): Unit =
    acc.append(stack.head.policy, ch, start, length)

  override def processingInstruction(target: String, data: String): Unit = {
    flushCharacters()
    super.processingInstruction(target, data)
  }

  override def comment(ch: Array[Char], start: Int, length: Int): Unit = {
    flushCharacters()
    super.comment(ch, start, length)
  }

  override def endDocument(): Unit =
    super.endDocument() // could debug log characters saved here

  private def flushCharacters(): Unit = {

    val result = acc.collapseAndReset(stack.head.policy).toCharArray

    if (result.nonEmpty)
      super.characters(result, 0, result.length)
  }
}
