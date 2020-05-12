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
package org.orbeon.oxf.processor.converter

import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInputOutputInfo}
import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI => HtmlURI}
import org.orbeon.oxf.xml.{PlainHTMLOrXHTMLReceiver, XMLReceiver}

// Perform the following transformation on the input document:
//
// - remove all elements not in the XHTML namespace and not in the null namespace
// - remove all attributes in a namespace
// - remove the prefix of all XHTML elements
// - remove all other namespace information on elements
// - for XHTML
//   - add the XHTML namespace as default namespace on the root element
//   - all elements in the document are in the XHTML namespace
// - otherwise
//   - don't output any namespace declaration
//   - all elements in the document are in no namespace
//
class PlainHTMLConverter  extends Converter("")
class PlainXHTMLConverter extends Converter(HtmlURI)

abstract class Converter(targetURI: String) extends ProcessorImpl {

  self =>

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
  addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

  override def createOutput(outputName: String) =
    addOutput(outputName, new CacheableTransformerOutputImpl(self, outputName) {
      def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit =
        readInputAsSAX(
          pipelineContext,
          ProcessorImpl.INPUT_DATA,
          new PlainHTMLOrXHTMLReceiver(targetURI, xmlReceiver)
        )
    })
}
