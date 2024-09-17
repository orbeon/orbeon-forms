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
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.event.XFormsEvent.*
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.event.XFormsEvents.*
import org.orbeon.saxon.om

class XFormsSubmitSerializeEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsEvent(XFORMS_SUBMIT_SERIALIZE, target, properties, bubbles = true, cancelable = false) {

  def this(target: XFormsEventTarget, binding: om.NodeInfo, requestedSerialization: String) = {
    this(target, EmptyGetter)
    bindingOpt = Option(binding)
    requestedSerializationOpt = Option(requestedSerialization)
  }

  private var bindingOpt: Option[om.NodeInfo] = None
  private var requestedSerializationOpt: Option[String] = None

  def submissionBodyAsString = property[om.NodeInfo]("submission-body") map (_.getStringValue) get

  override def lazyProperties = getters(this, XFormsSubmitSerializeEvent.Getters)
}

private object XFormsSubmitSerializeEvent {

  import XFormsEvent._

  def createSubmissionBodyElement(containingDocument: XFormsContainingDocument) = {
    val document = dom.Document()
    val docWrapper = new DocumentWrapper(document, null, XPath.GlobalConfiguration)
    val submissionBodyElement = dom.Element("submission-body")
    document.setRootElement(submissionBodyElement)
    docWrapper.wrap(submissionBodyElement)
  }

  val Getters = Map[String, XFormsSubmitSerializeEvent => Option[Any]] (
    xxfName("binding")       -> (_.bindingOpt),
    xxfName("serialization") -> (_.requestedSerializationOpt),
    "submission-body"        -> (e => Option(createSubmissionBodyElement(e.containingDocument)))
  )
}