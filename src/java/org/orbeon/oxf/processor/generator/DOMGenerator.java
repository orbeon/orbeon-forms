/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
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
package org.orbeon.oxf.processor.generator;

import org.dom4j.DocumentHelper;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.cache.SimpleOutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.util.NumberUtils;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.util.HashMap;
import java.util.Map;

/**
 * A DOMGenerator reads an input from a Node, and outputs SAX events.
 */
public class DOMGenerator extends ProcessorImpl {

    private Document document;

    private final SimpleOutputCacheKey outputKey;
    private final String key;
    private Object validity;
    private SAXStoreEntry saxStoreEntry;
    private static Map keyToSaxStore = new HashMap();
    
    {
        addOutputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public DOMGenerator( Node node, Object validity ) {
        this.validity = validity;
        if (node == null) {
            throw new OXFException("Null node passed to DOMGenerator");
        } else if (node instanceof Document) {
            this.document = (Document) node;
        } else {
            Document document = XMLUtils.createDocument();
            document.appendChild(document.importNode(node, true));
            this.document = document;
        }
        final byte[] dgst = XMLUtils.getDigest( document );
        key = NumberUtils.toHexString( dgst );
        outputKey = new SimpleOutputCacheKey( DOMGenerator.class, OUTPUT_DATA, key );
    }

    public DOMGenerator(org.dom4j.Node node) {
        this(node, new Long(0));
    }

    public DOMGenerator(org.dom4j.Node node, Object validity) {
        this.validity = validity;
        try {
            if (node == null)
                throw new OXFException("Null node passed to DOMGenerator");

            // Create document from node
            org.dom4j.Document document;
            if (node instanceof org.dom4j.Document) {
                document = (org.dom4j.Document) node;
            } else if (node instanceof org.dom4j.Element) {
                document = DocumentHelper.createDocument(((org.dom4j.Element) node).createCopy());
            } else {
                throw new OXFException("Unsupported DOM4J node type " + node.getClass().getName());
            }

            // Create key
            final byte[] dgst = XMLUtils.getDigest( document );
            key = NumberUtils.toHexString( dgst );
            outputKey = new SimpleOutputCacheKey( DOMGenerator.class, OUTPUT_DATA, key );

            // Get SAX Store Entry, if possible from map
            synchronized (keyToSaxStore) {
                saxStoreEntry = (SAXStoreEntry) keyToSaxStore.get(outputKey);
                if (saxStoreEntry == null) {
                    // Create SAX Store
                    saxStoreEntry = new SAXStoreEntry();
                    saxStoreEntry.saxStore = new SAXStore();
                    saxStoreEntry.referenceCount = 1;
                    LocationSAXWriter saxWriter = new LocationSAXWriter();
                    saxWriter.setContentHandler(saxStoreEntry.saxStore);
                    saxWriter.write(document);
                    keyToSaxStore.put(outputKey, saxStoreEntry);
                } else {
                    saxStoreEntry.referenceCount++;
                }
            }
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    protected void finalize() throws Throwable {
        if (saxStoreEntry != null) {
            synchronized (keyToSaxStore) {
                if (saxStoreEntry.referenceCount == 1) {
                    keyToSaxStore.remove(outputKey);
                } else {
                    saxStoreEntry.referenceCount--;
                }
            }
        }
        super.finalize();
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                try {
                    if (document != null) {
                        Transformer identity = TransformerUtils.getIdentityTransformer();
                        identity.transform(new DOMSource(document, key ), new SAXResult(contentHandler));
                    } else {
                        saxStoreEntry.saxStore.replay(contentHandler);
                    }
                } catch (TransformerException e) {
                    throw new OXFException(e);
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            public OutputCacheKey getKeyImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                return outputKey;
            }

            public Object getValidityImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                return validity;
            }
        };

        addOutput(name, output);
        return output;
    }

    private static class SAXStoreEntry {
        public SAXStore saxStore;
        public int referenceCount;
    }
}
