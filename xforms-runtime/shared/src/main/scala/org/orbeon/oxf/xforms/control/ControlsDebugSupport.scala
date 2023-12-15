/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control

import org.orbeon.dom.io.XMLWriter
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.saxon.om
import org.orbeon.saxon.value.AtomicValue

import scala.jdk.CollectionConverters._


object ControlsDebugSupport {

  import Private._

  def controlTreeAsXmlString(control: XFormsControl): String = {

    val (receiver, result) = StaticXPath.newTinyTreeReceiver
    implicit val identity: XMLReceiver = receiver

    withDocument {
      recurse(control)
    }

    StaticXPath.tinyTreeToOrbeonDom(result()).serializeToString(XMLWriter.PrettyFormat)
  }

  def printControlTreeAsXml(control: XFormsControl): Unit =
    println(controlTreeAsXmlString(control))

  private object Private {

    def writeControl(control: XFormsControl)(content: => Unit = ())(implicit receiver: XMLReceiver): Unit = {

      def itemToString(i: om.Item) = i match {
        case atomic: AtomicValue => atomic.getStringValue
        case node: om.NodeInfo   => node.getDisplayName
        case _                   => throw new IllegalStateException
      }

      val atts =
        List(
          "id"               -> control.getId,
          "effectiveId"      -> control.effectiveId,
          "isRelevant"       -> control.isRelevant.toString,
          "wasRelevant"      -> control.wasRelevant.toString,
          "binding-names"    -> (control.bindingContext.nodeset.asScala map itemToString mkString ("(", ", ", ")")),
          "binding-position" -> control.bindingContext.position.toString,
          "scope"            -> control.scope.scopeId
        )

      withElement(localName = control.localName, atts = atts) {
        control.childrenActions foreach recurse
        content
      }
    }

    def writeValueControl(control: XFormsValueControl)(content: => Unit)(implicit receiver: XMLReceiver): Unit =
      writeControl(control) {
        control.externalValueOpt(EventCollector.Throw) foreach text
        content
      }

    def writeContainerControl(control: XFormsContainerControl)(content: => Unit)(implicit receiver: XMLReceiver): Unit =
      writeControl(control) {
        control.children foreach recurse
        content
      }

    def recurse(control: XFormsControl)(implicit receiver: XMLReceiver): Unit =
      control match {
        case c: XFormsValueControl     => writeValueControl(c)(())
        case c: XFormsContainerControl => writeContainerControl(c)(())
        case c                         => writeControl(c)(())
      }
  }
}