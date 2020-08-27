/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.processor.serializer

import java.io.ByteArrayOutputStream

import org.junit.Test
import org.orbeon.oxf.externalcontext.ResponseAdapter
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom.Converter._
import org.scalatestplus.junit.AssertionsForJUnit

// As of 2012-07-05, this tests a minimal subset of BinaryTextXMLReceiver only. We should test more combinations of
// the input parameters.
class BinaryTextXMLReceiverTest extends ResourceManagerTestBase with AssertionsForJUnit {

  @Test def noContentType(): Unit = {

    val (response, receiver) = responseWithReceiver

    val document =
      <document
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xsi:type="xs:base64Binary"/>.toDocument

    TransformerUtils.writeDom4j(document, receiver)

    assert("application/octet-stream" === response.contentType)
  }

  @Test def forwardAsIs(): Unit =
    for (contentType <- Seq("text/html; charset=utf-16", "text/html", "image/png")) {
      val (response, receiver) = responseWithReceiver

      val document =
        <document
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xsi:type="xs:base64Binary"
          content-type={contentType}/>.toDocument

      TransformerUtils.writeDom4j(document, receiver)

      assert(contentType === response.contentType)
    }

  def responseWithReceiver = {
    val response = new TestResponse
    (response, new BinaryTextXMLReceiver(Left(response), true, false, None, false, false, None, false, Nil))
  }

  class TestResponse extends ResponseAdapter {

    var contentType: String = _
    var status: Int = -1

    override def setContentType(contentType: String): Unit = this.contentType = contentType
    override def setStatus(status: Int): Unit = this.status = status

    override def setPageCaching(lastModified: Long): Unit = super.setPageCaching(lastModified)
    override def setHeader(name: String, value: String): Unit = super.setHeader(name, value)

    override val getOutputStream = new ByteArrayOutputStream
  }
}
