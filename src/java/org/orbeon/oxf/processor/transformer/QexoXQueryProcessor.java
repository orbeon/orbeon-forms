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
package org.orbeon.oxf.processor.transformer;

import gnu.kawa.sax.ConsumeSAXHandler;
import gnu.kawa.sax.ContentConsumer;
import gnu.lists.Consumer;
import gnu.lists.TreeList;
import gnu.mapping.CallContext;
import gnu.mapping.Procedure1;
import gnu.xml.XMLPrinter;
import gnu.xquery.lang.XQuery;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.XMLWriter;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.ProcessorOutputXMLReader;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * XQuery processor based on the Qexo engine.
 */
public class QexoXQueryProcessor extends org.orbeon.oxf.processor.ProcessorImpl {

    public QexoXQueryProcessor() {
        addInputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public org.orbeon.oxf.processor.ProcessorOutput createOutput(String name) {
        org.orbeon.oxf.processor.ProcessorOutput output = new org.orbeon.oxf.processor.ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                try {
                    Document xqueryDocument = readCacheInputAsDOM4J(context, INPUT_CONFIG);

                    // Read XQuery into String
                    StringWriter writer = new StringWriter();
                    XMLWriter xmlWriter = new NoNamespaceXMLWriter(writer);
                    xmlWriter.write(xqueryDocument);
                    xmlWriter.close();
                    String xqueryBody = writer.toString();
                    xqueryBody = xqueryBody.substring(xqueryBody.indexOf(">") + 1);
                    xqueryBody = xqueryBody.substring(0, xqueryBody.lastIndexOf("<"));

                    // Add namespaces declarations
                    Map namespaces = new HashMap();
                    StringBuffer xqueryString = new StringBuffer();
                    getDeclaredNamespaces(namespaces, xqueryDocument.getRootElement());
                    for (Iterator i = namespaces.keySet().iterator(); i.hasNext();) {
                        String prefix = (String) i.next();
                        String uri = (String) namespaces.get(prefix);
                        xqueryString.append("declare namespace ");
                        xqueryString.append(prefix);
                        xqueryString.append(" = \"");
                        xqueryString.append(uri);
                        xqueryString.append("\"\n");
                    }
                    xqueryString.append(xqueryBody);

                    // Execute XQuery
                    XQuery xquery = new XQuery();
                    DocumentProcedure documentProcedure = new DocumentProcedure(context);
                    xquery.define("document", documentProcedure);
                    xquery.define("doc", documentProcedure);
                    if (true) {
                        // Using SAX
                        contentHandler.startDocument();
                        ContentConsumer contentConsumer = new ContentConsumer();
                        contentConsumer.setContentHandler(contentHandler);
                        xquery.eval(xqueryString.toString(), contentConsumer);
                        contentHandler.endDocument();
                    } else {
                        // Create string representation of result
                        Object result = xquery.eval(xqueryString.toString());
                        StringWriter stringWriter = new StringWriter();
                        new XMLPrinter(stringWriter).writeObject(result);
                        String stringResult = stringWriter.toString();

                        // Add namespace declarations
                        StringBuffer resultWithNamespaces = new StringBuffer();
                        int endTag = Math.min(stringResult.indexOf(">"), stringResult.indexOf("/"));
                        resultWithNamespaces.append(stringResult.substring(0, endTag));
                        for (Iterator i = namespaces.keySet().iterator(); i.hasNext();) {
                            String prefix = (String) i.next();
                            String uri = (String) namespaces.get(prefix);
                            resultWithNamespaces.append(" xmlns:");
                            resultWithNamespaces.append(prefix);
                            resultWithNamespaces.append("=\"");
                            resultWithNamespaces.append(uri);
                            resultWithNamespaces.append("\"");
                        }
                        resultWithNamespaces.append(stringResult.substring(endTag));

                        // Parse string representation and generate SAX events
                        XMLReader xmlReader = XMLUtils.newSAXParser().getXMLReader();
                        xmlReader.setContentHandler(contentHandler);
                        xmlReader.parse(new InputSource(new StringReader(resultWithNamespaces.toString())));
                    }
                } catch (Throwable throwable) {
                    if (throwable instanceof Exception) {
                        throw new OXFException((Exception) throwable);
                    } else {
                        throwable.printStackTrace();
                        throw new OXFException(throwable.getMessage());
                    }
                }
            }

            public OutputCacheKey getKeyImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                return null;
            }

            public Object getValidityImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                return null;
            }
        };
        addOutput(name, output);
        return output;
    }

    /**
     * Returns all the namespaces declared in the given document.
     * Throws an exception if a prefix is mapped to two different URI
     * in the document (we might want to support this in the future).
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

        // Go through child elements
        for (Iterator i = element.elements().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            getDeclaredNamespaces(namespaces, child);
        }
    }

    /**
     * Implement the document() Xquery function
     */
    private class DocumentProcedure extends Procedure1 {

        private org.orbeon.oxf.pipeline.api.PipelineContext context;

        public DocumentProcedure(org.orbeon.oxf.pipeline.api.PipelineContext context) {
            this.context = context;
        }

        public void document(String url, Consumer consumer) throws Throwable {
            ConsumeSAXHandler consumeSAXHandler = new ConsumeSAXHandler(consumer);
            if (url.startsWith("#")) {
                String inputName = url.substring(1);
                org.orbeon.oxf.processor.ProcessorInput input = getInputByName(inputName);
                if (input == null)
                    throw new OXFException("XQuery Processor cannot find input name '" + inputName + "'");
                readInputAsSAX(context, input, consumeSAXHandler);
            } else {
                Processor urlGenerator = new URLGenerator(URLFactory.createURL(url));
                XMLReader xmlReader = new ProcessorOutputXMLReader(context, urlGenerator.createOutput(OUTPUT_DATA));
                xmlReader.setContentHandler(consumeSAXHandler);
                xmlReader.parse(url);
            }
        }

        public Object apply1(Object arg1) throws Throwable {
            TreeList doc = new TreeList();
            document(arg1.toString(), doc);
            return doc;
        }

        public void apply(CallContext ctx) throws Throwable {
            String fileName = ctx.getNextArg().toString();
            document(fileName, ctx.consumer);
        }
    }

    private static class NoNamespaceXMLWriter extends XMLWriter {
        public NoNamespaceXMLWriter(Writer writer) {
            super(writer);
        }

        protected void writeNamespace(String prefix, String uri) throws IOException { /* do nothing */
        }

        protected void writeDeclaration() throws IOException { /* do nothing */
        }
    }
}
