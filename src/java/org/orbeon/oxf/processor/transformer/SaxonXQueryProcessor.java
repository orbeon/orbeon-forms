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
package org.orbeon.oxf.processor.transformer;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.query.DynamicQueryContext;
import org.orbeon.saxon.query.StaticQueryContext;
import org.orbeon.saxon.query.XQueryExpression;
import org.xml.sax.ContentHandler;

import javax.xml.transform.sax.SAXResult;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * XQuery processor based on the Saxon engine.
 *
 * TODO: should work like the XSLT processor, and handle:
 *
 *   o caching
 *   o errors
 *   o additional inputs
 *   o etc.
 */
public class SaxonXQueryProcessor extends ProcessorImpl {

    public SaxonXQueryProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    // This class just to make a method public. I believe it is already public in the newer version
    // of Saxon. When that is integrated, we can remove the class below.
    class LocalStaticQueryContext extends StaticQueryContext {

        public LocalStaticQueryContext(Configuration config) {
            super(config);
        }

        public void declareActiveNamespace(String prefix, String uri) {
            super.declareActiveNamespace(prefix, uri);
        }
    };

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    Document xqueryDocument = readCacheInputAsDOM4J(context, INPUT_CONFIG);
                    Document dataDocument = readInputAsDOM4J(context, INPUT_DATA);

                    // Read XQuery into String
                    String xqueryBody = Dom4jUtils.domToString(xqueryDocument);
                    xqueryBody = xqueryBody.substring(xqueryBody.indexOf(">") + 1);
                    xqueryBody = xqueryBody.substring(0, xqueryBody.lastIndexOf("<"));

                    // Create XQuery expression and run it
                    Configuration config = new Configuration();
                    LocalStaticQueryContext staticContext = new LocalStaticQueryContext(config);

                    // Add namespaces declarations
                    Map namespaces = new HashMap();
                    getDeclaredNamespaces(namespaces, xqueryDocument.getRootElement());
                    for (Iterator i = namespaces.keySet().iterator(); i.hasNext();) {
                        String prefix = (String) i.next();
                        String uri = (String) namespaces.get(prefix);
                        staticContext.declareActiveNamespace(prefix, uri);
                    }

                    // Create XQuery expression and run it
                    XQueryExpression exp = staticContext.compileQuery(xqueryBody);
                    DynamicQueryContext dynamicContext =  new DynamicQueryContext(config);
                    dynamicContext.setContextNode(staticContext.buildDocument(new DocumentSource(dataDocument)));
                    exp.run(dynamicContext, new SAXResult(contentHandler), new Properties());

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }

            public OutputCacheKey getKeyImpl(PipelineContext context) {
                return null;
            }

            public Object getValidityImpl(PipelineContext context) {
                return null;
            }
        };
        addOutput(name, output);
        return output;
    }

    /**
     * Returns all the namespaces declared in the given document. Throws an exception if a prefix
     * is mapped to two different URI in the document (we might want to support this in the future).
     */
    private void getDeclaredNamespaces(Map namespaces, Element element) {

        // Handle namespace in this element
        for (Iterator i = element.declaredNamespaces().iterator(); i.hasNext();) {
            Namespace namespace = (Namespace) i.next();
            String prefix = namespace.getPrefix();
            String uri = namespace.getURI();
            String existingMapping = (String) namespaces.get(prefix);
            if (existingMapping != null) {
                if (!uri.equals(existingMapping))
                    throw new OXFException("Namespace prefix '" + prefix + "' is mapped to more than one URI: '"
                            + existingMapping + "' and '" + uri + "'");
            } else {
                namespaces.put(prefix, uri);
            }
        }

        // Go through children elements
        for (Iterator i = element.elements().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            getDeclaredNamespaces(namespaces, child);
        }
    }
}
