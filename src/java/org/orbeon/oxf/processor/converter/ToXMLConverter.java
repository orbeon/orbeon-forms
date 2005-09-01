/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.processor.converter;

import org.apache.commons.fileupload.FileItem;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.serializer.BinaryTextContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;

/**
 * This converter converts from a binary document or a text document to a parsed XML document.
 */
public class ToXMLConverter extends ProcessorImpl {
    public ToXMLConverter() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {

                // Read config input
//                final Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
//                    public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
//                        Config result = new Config();
//
//                        Element configElement = readInputAsDOM4J(context, input).getRootElement();
//
//                        {
//                            Element matchURIElement = configElement.element("match").element("uri");
//                            result.matchURI = (matchURIElement == null) ? null : matchURIElement.getStringValue();
//                            Element matchPrefixElement = configElement.element("match").element("prefix");
//                            result.matchPrefix = (matchPrefixElement == null) ? null : matchPrefixElement.getStringValue();
//                        }
//
//                        {
//                            Element replaceURIElement = configElement.element("replace").element("uri");
//                            result.replaceURI = (replaceURIElement == null) ? null : replaceURIElement.getStringValue();
//                            Element replacePrefixElement = configElement.element("replace").element("prefix");
//                            result.replacePrefix = (replacePrefixElement == null) ? null : replacePrefixElement.getStringValue();
//                        }
//
//                        return result;
//                    }
//                });

                // Get FileItem
                final FileItem fileItem = XMLUtils.prepareFileItem(pipelineContext);

                try {
                    // Read to OutputStream
                    readInputAsSAX(pipelineContext, INPUT_DATA, new BinaryTextContentHandler(null, fileItem.getOutputStream(), false, null, false, false, null, false));

                    // Create parser
                    final SAXParser parser = XMLUtils.newSAXParser();
                    final XMLReader reader = parser.getXMLReader();

                    // Run parser on InputStream
                    //inputSource.setSystemId();
                    reader.setContentHandler(contentHandler);
                    reader.parse(new InputSource(fileItem.getInputStream()));

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
