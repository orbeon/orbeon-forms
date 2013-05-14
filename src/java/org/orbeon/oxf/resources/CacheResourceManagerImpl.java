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
package org.orbeon.oxf.resources;

import com.intersys.objects.CacheDatabase;
import com.intersys.objects.CacheException;
import com.intersys.objects.Database;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.TransformerUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.Map;

public class CacheResourceManagerImpl extends ResourceManagerBase {

    private static Logger logger = LoggerFactory.createLogger(CacheResourceManagerImpl.class);

    private String url;
    private String username;
    private String password;
    private Database db;

    public CacheResourceManagerImpl(Map props) throws OXFException {
        super(props);
        url = (String) props.get(CacheResourceManagerFactory.CACHE_URL);
        if (url == null)
            throw new OXFException("Property " + CacheResourceManagerFactory.CACHE_URL + " is null");
        username = (String) props.get(CacheResourceManagerFactory.CACHE_USERNAME);
        if (username == null)
            throw new OXFException("Property " + CacheResourceManagerFactory.CACHE_USERNAME + " is null");
        password = (String) props.get(CacheResourceManagerFactory.CACHE_PASSWORD);
        if (password == null)
            throw new OXFException("Property " + CacheResourceManagerFactory.CACHE_PASSWORD + " is null");

        try {
            db = CacheDatabase.getDatabase(url, username, password);
        } catch (CacheException e) {
            throw new OXFException(e);
        }
    }


    /**
     * Returns a binary input stream for the specified key. The key could point
     * to any document type (text or binary).
     * @param key A Resource Manager key
     * @return a input stream
     */
    public InputStream getContentAsStream(String key) {
        if (logger.isDebugEnabled())
            logger.debug("getContentAsStream(" + key + ")");
        try {
            return new ByteArrayInputStream(getDocumentAsString(key).getBytes("utf-8"));
        } catch (ResourceNotFoundException rnfe) {
            throw rnfe;
        } catch (Exception e) {
            throw new OXFException("Cannot read from file " + key, e);
        }
    }

    /**
     * Returns a character reader from the resource manager for the specified
     * key. The key could point to any text document.
     * @param key A Resource Manager key
     * @return a character reader
     */
    public Reader getContentAsReader(String key) {
        if (logger.isDebugEnabled())
            logger.debug("getContentAsReader(" + key + ")");
        try {
            return new StringReader(getDocumentAsString(key));
        } catch (ResourceNotFoundException rnfe) {
            throw rnfe;
        } catch (Exception e) {
            throw new OXFException("Cannot read from file " + key, e);
        }
    }

    private String getDocumentAsString(String key) {
        eXtc.Document doc = null;
        try {
            doc = eXtc.DOMAPI.getDocumentNode(db, key);
            if (doc == null) {
                if (logger.isDebugEnabled())
                    logger.debug("DOM not found: " + key);
                throw new ResourceNotFoundException("Cannot read from file " + key);
            }
            DOMSource source = new DOMSource(doc);
            StringWriter buffer = new StringWriter();
            StreamResult result = new StreamResult(buffer);
            TransformerUtils.getIdentityTransformer().transform(source, result);
            return buffer.toString();
        } catch (ResourceNotFoundException rnfe) {
            throw rnfe;
        } catch (Exception e) {
            throw new OXFException(e);
        }

    }


    public Node getContentAsDOM(String key) {
        if (logger.isDebugEnabled())
            logger.debug("getContentAsDOM(" + key + ")");
        try {
            eXtc.Document doc = eXtc.DOMAPI.getDocumentNode(db, key);
            if (doc == null)
                throw new ResourceNotFoundException("Cannot read from file " + key);
            else
                return doc;
        } catch (CacheException e) {
            throw new OXFException(e);
        }
    }


    public void getContentAsSAX(final String key, ContentHandler handler, boolean validating, boolean handleXInclude) {
        if (logger.isDebugEnabled())
            logger.debug("getContentAsSAX(" + key + ")");
        try {
            eXtc.DOMAPI.openDOM(db);
            eXtc.Document doc = eXtc.DOMAPI.getDocumentNode(db, key);
            if (doc == null)
                throw new ResourceNotFoundException("Cannot read from file " + key);
            else {
                try {
                    TransformerUtils.getIdentityTransformer().transform(new DOMSource(doc), new SAXResult(new ForwardingContentHandler(handler) {

                        public void startDocument() throws SAXException {
                            super.startDocument();
                            super.setDocumentLocator(new Locator() {
                                public int getColumnNumber() {
                                    return 0;
                                }

                                public int getLineNumber() {
                                    return 0;
                                }

                                public String getPublicId() {
                                    return "";
                                }

                                public String getSystemId() {
                                    return "oxf:" + key;
                                }
                            });
                        }

                        public void setDocumentLocator(Locator locator) {
                        }

                    }));
                } catch (TransformerException e) {
                    throw new OXFException(e);
                }
            }
            eXtc.DOMAPI.closeDOM(db);
        } catch (CacheException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Gets the last modified timestamp for the specofoed resource
     * @param key A Resource Manager key
     * @param doNotThrowResourceNotFound
     * @return a timestamp
     */
    public long lastModifiedImpl(String key, boolean doNotThrowResourceNotFound) {
        try {
            eXtc.Document doc = eXtc.DOMAPI.getDocumentNode(db, key);
            if (doc == null)
                throw new ResourceNotFoundException("Cannot read from file " + key);
            return doc.getcreationDate().getTime();
        } catch (CacheException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key) {
        if (logger.isDebugEnabled())
            logger.debug("length(" + key + ")");
        try {
            return getDocumentAsString(key).length();
        } catch (Exception e) {
            throw new OXFException("Cannot read from file " + key, e);
        }
    }

    /**
     * Indicates if the resource manager implementation suports write operations
     * @return true if write operations are allowed
     */
    public boolean canWrite(String key) {
        return true;
    }

    /**
     * Allows writing to the resource
     * @param key A Resource Manager key
     * @return an output stream
     */
    public OutputStream getOutputStream(String key) {
        throw new OXFException("Not supported");
    }

    /**
     * Allow writing to the resource
     * @param key A Resource Manager key
     * @return  a writer
     */
    public Writer getWriter(String key) {
        throw new OXFException("Not supported");
    }

    public void writeDOM(String key, Node node) {
        if (node instanceof Element) {
            try {
                Element el = (Element) node;
                DOMResult result = new DOMResult(eXtc.DOMAPI.createDocument(db, el.getNamespaceURI(), el.getNodeName(), key));
                TransformerUtils.getIdentityTransformer().transform(new DOMSource(node), result);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        } else
            throw new OXFException("Node is not an instance of element");

    }

    public void writeDOM4J(String key, Document document) {
        try {
            org.dom4j.Element el = document.getRootElement();
            DOMResult result = new DOMResult(eXtc.DOMAPI.createDocument(db, el.getNamespaceURI(), el.getName(), key));
            TransformerUtils.getIdentityTransformer().transform(new DocumentSource(document), result);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public ContentHandler getWriteContentHandler(String key) {
        try {
            TransformerHandler handler = TransformerUtils.getIdentityTransformerHandler();
            DOMResult result = new DOMResult(eXtc.DOMAPI.createDocument(db, "", "", key));
            handler.setResult(result);
            return handler;
        } catch (Exception e) {
            throw new OXFException(e);
        }

    }

    public String getRealPath(String key) {
        return null;
    }


}
