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
package org.orbeon.oxf.processor.converter;

import org.apache.commons.fileupload.FileItem;
import org.orbeon.dom.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.ParserConfiguration;
import org.orbeon.oxf.xml.XMLParsing;
import org.orbeon.oxf.xml.XMLReceiver;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * This converter converts from a binary document or a text document to a parsed XML document.
 */
public class ToXMLConverter extends ProcessorImpl {
    public ToXMLConverter() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new CacheableTransformerOutputImpl(ToXMLConverter.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {

                // Read config input
                final ParserConfiguration config = readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader<ParserConfiguration>() {
                    public ParserConfiguration read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {

                        final Element configElement = readInputAsOrbeonDom(context, input).getRootElement();

                        return ParserConfiguration.apply(
                            ProcessorUtils.selectBooleanValue(configElement, "/config/validating", URLGenerator.DEFAULT_VALIDATING),
                            ProcessorUtils.selectBooleanValue(configElement, "/config/handle-xinclude", URLGenerator.DEFAULT_HANDLE_XINCLUDE),
                            ProcessorUtils.selectBooleanValue(configElement, "/config/external-entities", URLGenerator.DEFAULT_EXTERNAL_ENTITIES),
                            null
                        );
                    }
                });

                try {
                    // Get FileItem
                    final FileItem fileItem = NetUtils.prepareFileItem(NetUtils.REQUEST_SCOPE, logger);

                    // TODO: Can we avoid writing to a FileItem?

                    // Read to OutputStream
                    readInputAsSAX(pipelineContext, INPUT_DATA, new BinaryTextXMLReceiver(fileItem.getOutputStream()));

                    // Create parser
                    final XMLReader reader = XMLParsing.newXMLReader(config);

                    // Run parser on InputStream
                    //inputSource.setSystemId();
                    reader.setContentHandler(xmlReceiver);
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
