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
package org.orbeon.oxf.processor.serializer;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.resources.handler.OXFHandler;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XPathUtils;

import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class URLSerializer extends ProcessorImpl {

    public URLSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, URLGenerator.URL_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    public void start(PipelineContext pipelineContext) {
        try {
            // Create the URL from the configuration
            final URL url = (URL) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    try {
                        final Document configDocument = readInputAsDOM4J(context, input);
                        final String urlString = XPathUtils.selectStringValueNormalize(configDocument, "/config/url");
                        return URLFactory.createURL(urlString.trim());
                    } catch (MalformedURLException e) {
                        throw new OXFException(e);
                    }
                }
            });

            if (OXFHandler.PROTOCOL.equals(url.getProtocol())) {
                // NOTE: This is probably done as an optimization. Is this really necessary?

                final OutputStream os = ResourceManagerWrapper.instance().getOutputStream(url.getFile());
                try {
                    final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
                    identity.setResult(new StreamResult(os));

                    readInputAsSAX(pipelineContext, INPUT_DATA, identity);
                } finally {
                    // Clean up
                    if (os != null)
                        os.close();
                }
            } else {
                // Open the URL
                final URLConnection conn = url.openConnection();
                try {
                    conn.setDoOutput(true);
                    conn.connect();
                    final OutputStream os = conn.getOutputStream();
                    try {
                        // Create an identity transformer and start the transformation
                        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
                        identity.setResult(new StreamResult(os));
                        readInputAsSAX(pipelineContext, INPUT_DATA, identity);
                    } finally {
                        // Clean up
                        if (os != null)
                            os.close();
                    }
                } finally {
                    // Clean up
                    if (conn instanceof HttpURLConnection)
                        ((HttpURLConnection) conn).disconnect();
                }
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
