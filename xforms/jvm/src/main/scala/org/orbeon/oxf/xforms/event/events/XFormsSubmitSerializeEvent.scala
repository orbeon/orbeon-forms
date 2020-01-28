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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.dom
import org.orbeon.dom.DocumentFactory
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om._
import org.orbeon.oxf.util.XPath

class XFormsSubmitSerializeEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsEvent(XFORMS_SUBMIT_SERIALIZE, target, properties, bubbles = true, cancelable = false) {

  def this(target: XFormsEventTarget, binding: NodeInfo, requestedSerialization: String) = {
    this(target, EmptyGetter)
    bindingOpt = Option(binding)
    requestedSerializationOpt = Option(requestedSerialization)
  }

  private var bindingOpt: Option[NodeInfo] = None
  private var requestedSerializationOpt: Option[String] = None

  def submissionBodyAsString = property[NodeInfo]("submission-body") map (_.getStringValue) get

  override def lazyProperties = getters(this, XFormsSubmitSerializeEvent.Getters)
}

private object XFormsSubmitSerializeEvent {

  import XFormsEvent._

  def createSubmissionBodyElement(containingDocument: XFormsContainingDocument) = {
    val document = dom.Document()
    val docWrapper = new DocumentWrapper(document, null, XPath.GlobalConfiguration)
    val submissionBodyElement = DocumentFactory.createElement("submission-body")
    document.setRootElement(submissionBodyElement)
    docWrapper.wrap(submissionBodyElement)
  }

  val Getters = Map[String, XFormsSubmitSerializeEvent => Option[Any]] (
    xxfName("binding")       -> (_.bindingOpt),
    xxfName("serialization") -> (_.requestedSerializationOpt),
    "submission-body"        -> (e => Option(createSubmissionBodyElement(e.containingDocument)))
  )
}