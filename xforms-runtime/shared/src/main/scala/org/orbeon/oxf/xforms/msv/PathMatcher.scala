/**
 * Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.msv

import org.orbeon.dom
import org.orbeon.msv.grammar.xmlschema.XPath
import org.orbeon.msv.relaxng.datatype.Datatype

/**
 * Base implementation of XPath matching engine.
 *
 * It only supports the subset defined in XML Schema Part 1. Extra care
 * must be taken to call the testInitialMatch method after the creation of an object.
 *
 * Match to an attribute is not supported. It is implemented in FieldPathMatcher class.
 *
 * The onMatched method is called when the specified XPath matches the current element.
 * Derived classes should implement this method to do something useful.
 */
abstract class PathMatcher(owner: IDConstraintChecker, paths: Array[XPath]) extends MatcherBundle(owner) {

  private var matchFound: Boolean = false

  children = paths.map(new SinglePathMatcher(_))

  def start(elt: dom.Element): Unit = {
    if (matchFound)
      onElementMatched(elt)
    matchFound = false
  }

  def onElementMatched(elt: dom.Element): Unit
  def onAttributeMatched(att: dom.Attribute, `type`: Datatype): Unit

  override def startElement(elt: dom.Element): Unit = {
    super.startElement(elt)
    if (matchFound)
      onElementMatched(elt)
    matchFound = false
  }

  override def onAttribute(att: dom.Attribute, `type`: Datatype): Unit = {
    super.onAttribute(att, `type`)
    if (matchFound)
      onAttributeMatched(att, `type`)
    matchFound = false
  }

  private class SinglePathMatcher(protected val path: XPath) extends Matcher(PathMatcher.this.owner) {
    private var activeSteps: Array[Array[Boolean]] = new Array[Array[Boolean]](4)
    private var elementMatched: Boolean = false

    activeSteps(0) = new Array[Boolean](path.steps.length + 1)
    activeSteps(0)(0) = true

    if (path.steps.length == 0) {
      if (path.attributeStep == null)
        matchFound = true
      else
        elementMatched = true
    }

    override def startElement(elt: dom.Element): Unit = {
      elementMatched = false

      val depth = getDepth

      if (depth == activeSteps.length - 1) {
        val newBuf = new Array[Array[Boolean]](depth * 2)
        System.arraycopy(activeSteps, 0, newBuf, 0, activeSteps.length)
        activeSteps = newBuf
      }

      val len = path.steps.length
      val prvBuf = activeSteps(depth - 1)
      var curBuf = activeSteps(depth)
      if (curBuf == null) {
        curBuf = new Array[Boolean](len + 1)
        activeSteps(depth) = curBuf
      }

      if (len != 0) {
        System.arraycopy(prvBuf, 0, curBuf, 1, len)
        curBuf(0) = path.isAnyDescendant
      }

      val nsURI = elt.getNamespaceURI
      val lnam = elt.getName
      var i = 1
      while (i <= len) {
        if (curBuf(i) && !path.steps(i - 1).accepts(nsURI, lnam))
          curBuf(i) = false
        i += 1
      }

      if (curBuf(len)) {
        if (path.attributeStep == null)
          matchFound = true
        else
          elementMatched = true
      }
    }

    override def onAttribute(att: dom.Attribute, `type`: Datatype): Unit = {
      if (!elementMatched) return

      val nsURI = att.getNamespaceURI
      val lnam = att.getName
      if (path.attributeStep.accepts(nsURI, lnam))
        matchFound = true
    }

    override def endElement(`type`: Datatype): Unit = {
      elementMatched = false
    }
  }
}
