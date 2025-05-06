/**
  * Copyright (C) 2016 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.test.ProcessorTestBase
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xml.{EncodeDecode, ParserConfiguration, TransformerUtils, XMLParsing, XMLReaderProviderRegistry}

import javax.xml.transform.TransformerException


class XFormsProcessorTest
  extends ProcessorTestBase(
    "oxf:/org/orbeon/oxf/xforms/tests-xforms.xml",
    XFormsStateManager.sessionCreated,
    XFormsStateManager.sessionDestroyed
  ) {
  // Register function returning XMLReader, so it can be used from coreCrossPlatformJVM, if available
  XMLReaderProviderRegistry.register(() => XMLParsing.newSAXParser(ParserConfiguration.Plain).getXMLReader)
}

object XFormsProcessorTest {
  //@XPathFunction
  def encodeW3CDomToStringForTests(node: org.w3c.dom.Node): String =
    try
      EncodeDecode.encodeXML(
        TransformerUtils.domToOrbeonDomDocument(node),
        XFormsGlobalProperties.isGZIPState,
        encrypt  = true,
        location = false
      )
    catch {
      case e: TransformerException =>
        throw new OXFException(e)
    }
}