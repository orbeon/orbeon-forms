/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import org.orbeon.scaxon.SAXMachine
import org.orbeon.scaxon.SAXEvents._
import javax.xml.namespace.QName
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xforms.XFormsConstants._
import collection.JavaConverters._
import org.orbeon.oxf.xml.XMLReceiver

// All the states we know
sealed trait State
case object RootState extends State
case object LHHAState extends State
case object XFormsState extends State
case object PreserveState extends State
case object HeadState extends State
case object TitleState extends State

case class ElementDetails(start: StartElement, parent: Option[ElementDetails], level: Int, previousState: State)

// Attempt to rewrite XFormsAnnotator as an FSM
class NewXFormsAnnotator(out: XMLReceiver) extends SAXMachine[State, Option[ElementDetails]] with XMLReceiver {

  private val HtmlHead = new QName(XHTML_NAMESPACE_URI, "head")
  private val HtmlTitle = new QName(XHTML_NAMESPACE_URI, "title")
  private val XFormsOutput = new QName(XFORMS_NAMESPACE_URI, "output")

  private def newElementDetail(start: StartElement, parent: Option[ElementDetails]) =
    Some(ElementDetails(start, parent, depth, parent map (_.previousState) getOrElse RootState ))

  private val goBackHandler: StateFunction = {
    // EndElement taking us back to the previous state
    case Event(ev @ EndElement(_), parentOption @ Some(parent)) if parent.level == depth =>
      forward(out, ev)
      goto(parent.previousState) using parentOption
  }

  private val defaultHandler: StateFunction = goBackHandler orElse {
    // StartElement keeping us in the same state
    case Event(ev @ StartElement(_, _), parentOption) =>
      forward(out, ev)
      stay() using newElementDetail(ev, parentOption)
    // EndElement keeping us in the same state
    case Event(ev @ EndElement(_), parentOption) =>
      forward(out, ev)
      stay() using parentOption
    // Any other event
    case Event(ev, _) =>
      forward(out, ev)
      stay()
  }

  // Register the state machine
  startWith(RootState, None)

  when(RootState) {
    case Event(ev @ StartElement(HtmlHead, _), parentOption) =>
      forward(out, ev)
      goto(HeadState) using newElementDetail(ev, parentOption)
  }
  when(RootState)(defaultHandler)

  when(HeadState) {
    case Event(ev @ StartElement(HtmlTitle, _), parentOption) =>
      forward(out, ev)
//            // Make sure there will be an id on the title element (ideally, we would do this only if there is a nested xf:output)
//            val newAtts = getAttributesGatherNamespaces(qName, attributes, reusableStringArray, idIndex)
//            htmlElementId = reusableStringArray[0]
//            htmlTitleElementId = htmlElementId
      goto(TitleState) using newElementDetail(ev, parentOption)
  }
  when(HeadState)(defaultHandler)

  when(TitleState) {
    case Event(ev @ StartElement(XFormsOutput, atts), parentOption) =>
//            val newAtts = XMLUtils.addOrReplaceAttribute(atts, "", "", "for", htmlTitleElementId)
//            startPrefixMapping(true, "xxforms", XXFORMS_NAMESPACE_URI)
//            startElement(true, XXFORMS_NAMESPACE_URI, "text", "xxf:text", newAtts)
      stay() using newElementDetail(ev, parentOption)
  }
  when(TitleState)(defaultHandler)

  when(PreserveState)(defaultHandler)

  initialize()

  private val metadata: Metadata = null // TODO

  protected def addNamespaces(id: String): Unit =
    metadata.addNamespaceMapping(id, namespaceContext.currentMapping)
}