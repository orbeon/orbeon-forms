/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor.serializer;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.resources.oxf.Handler;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.ContentHandler;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class URLSerializer extends ProcessorImpl {

//    private static Logger logger = LoggerFactory.createLogger(URLSerializer.class);

    public URLSerializer() {
        addInputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(INPUT_CONFIG, URLGenerator.URL_NAMESPACE_URI));
        addInputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(INPUT_DATA));
    }

    public void start(PipelineContext context) {
        try {
            // first read the data input and cache the result
            ProcessorInput dataInput = getInputByName(INPUT_DATA);
            SAXStore store = new SAXStore();
            dataInput.getOutput().read(context, store);

            // then create the URL...
            URL url = (URL) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new org.orbeon.oxf.processor.CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    try {
                        Document doc = readInputAsDOM4J(context, input);
                        String url = XPathUtils.selectStringValueNormalize(doc, "/config/url");
                        return URLFactory.createURL(url.trim());
                    } catch (MalformedURLException e) {
                        throw new OXFException(e);
                    }
                }
            });

            if (Handler.PROTOCOL.equals(url.getProtocol())) {
                ContentHandler handler = ResourceManagerWrapper.instance().getWriteContentHandler(url.getFile());
                store.replay(handler);
            } else {
                // ...and open it
                URLConnection conn = url.openConnection();
                conn.setDoOutput(true);
                conn.connect();
                OutputStream os = conn.getOutputStream();

                // Create an identity transformer and start the transformation
                TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
                identity.setResult(new StreamResult(os));
                store.replay(identity);

                // clean up
                os.close();
                if (conn instanceof HttpURLConnection)
                    ((HttpURLConnection) conn).disconnect();
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }


}
