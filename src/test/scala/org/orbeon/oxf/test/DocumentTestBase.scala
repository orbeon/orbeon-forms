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
package org.orbeon.oxf.test

import org.dom4j
import org.junit.After
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsStaticStateImpl}

abstract class DocumentTestBase extends ResourceManagerTestBase with XFormsSupport with XMLSupport {

  private var _document: XFormsContainingDocument = _
  def document = _document

  def setupDocument(documentURL: String): XFormsContainingDocument =
    setupDocument(ProcessorUtils.createDocumentFromURL(documentURL, null))

  def setupDocument(xhtml: dom4j.Document): XFormsContainingDocument = {
    ResourceManagerTestBase.staticSetup()

    val (template, staticState) = XFormsStaticStateImpl.createFromDocument(xhtml)
    _document = new XFormsContainingDocument(staticState, null, null, true)

    _document.setTemplateIfNeeded(AnnotatedTemplate(template))
    _document.afterInitialResponse()
    _document.beforeExternalEvents(null)

    _document
  }

  def setupDocument(doc: XFormsContainingDocument): Unit = {
    _document = doc

    _document.afterInitialResponse()
    _document.beforeExternalEvents(null)
  }

  @After def disposeDocument(): Unit = {
    if (_document ne null) {
      _document.afterExternalEvents()
      _document.afterUpdateResponse()

      _document = null
    }
  }
}