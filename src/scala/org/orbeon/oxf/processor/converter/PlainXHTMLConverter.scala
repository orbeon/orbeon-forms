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

import org.orbeon.oxf.pipeline.api.{XMLReceiver, PipelineContext}
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.processor.{ProcessorInputOutputInfo, ProcessorImpl}
import org.orbeon.oxf.xml.ForwardingXMLReceiver
import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI ⇒ HtmlURI}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

// Perform the following transformation on the input document:
//
// - remove all elements not in the XHTML namespace
// - remove all attributes in a namespace
// - remove the prefix of all XHTML elements
// - remove all other namespace information on elements
// - if the root element of the document is "{http://www.w3.org/1999/xhtml}html",
//   - add the XHTML namespace as default namespace on the root element
//   - all elements in the document are in the XHTML namespace
// - otherwise
//   - don't output any namespace declaration
//   - all elements in the document are in no namespace
//
class PlainXHTMLConverter extends ProcessorImpl {

    addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
    addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

    override def createOutput(outputName: String) =
        addOutput(outputName, new CacheableTransformerOutputImpl(PlainXHTMLConverter.this, outputName) {
            def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

                readInputAsSAX(pipelineContext, ProcessorImpl.INPUT_DATA, new ForwardingXMLReceiver(xmlReceiver) {

                    var level = 0
                    var inXHTMLNamespace = false

                    override def startElement(uri: String, localname: String, qName: String, attributes: Attributes) = {

                        // Only output the top-level namespace if the element is "{http://www.w3.org/1999/xhtml}html",
                        // following http://www.w3.org/TR/xslt-xquery-serialization/#xhtml-output: "The serializer
                        // SHOULD output namespace declarations in a way that is consistent with the requirements of the
                        // XHTML DTD if this is possible".
                        if (level == 0 && uri == HtmlURI && localname == "html") {
                            inXHTMLNamespace = true
                            super.startPrefixMapping("", HtmlURI)
                        }

                        if (uri == HtmlURI)
                            super.startElement(if (inXHTMLNamespace) HtmlURI else "", localname, localname, filterAttributes(attributes))

                        level += 1
                    }

                    override def endElement(uri: String, localname: String, qName: String) = {

                        level -= 1

                        if (uri == HtmlURI)
                            super.endElement(if (inXHTMLNamespace) HtmlURI else "", localname, localname)

                        if (level == 0 && inXHTMLNamespace)
                            super.endPrefixMapping("")
                    }

                    // Swallow all namespace mappings
                    override def startPrefixMapping(prefix: String, uri: String) = ()
                    override def endPrefixMapping(prefix: String) = ()

                    // Only keep attributes in no namespace
                    def filterAttributes(attributes: Attributes) = {
                        val length = attributes.getLength

                        // Whether there is at least one attribute in a namespace
                        def hasNamespace: Boolean = {
                            var i = 0
                            while (i < length) {
                                if (attributes.getURI(i) != "")
                                    return true

                                i += 1
                            }
                            false
                        }

                        if (hasNamespace) {
                            val newAttributes = new AttributesImpl

                            var i = 0
                            while (i < length) {
                                if (attributes.getURI(i) == "")
                                    newAttributes.addAttribute(attributes.getURI(i), attributes.getLocalName(i),
                                        attributes.getQName(i), attributes.getType(i), attributes.getValue(i))

                                i += 1
                            }

                            newAttributes
                        } else
                            attributes
                    }
                })
            }
        })
}
