/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.resources;

import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;

/**
 * This is the main interface a resource manager must implement.
 * A resource manager key is a path-like string, locating a resource.
 *
 * For example: /config/text.xml
 *              /pages/icon.gif
 *
 * Resources Manager could store resource in flat files (see FlatFileResourceManagerImpl), in
 * relational databases (see DBResourceManagerImpl) or in an application dependent way.
 *
 */
public interface ResourceManager {
    /**
     * Gets a W3C DOM node for the specified key. The key must point to an XML
     * document, or a OXFException is raised.
     *
     * @param key A Resource Manager key
     * @return a node element
     */
    public Node getContentAsDOM(String key);

    /**
     * Gets a DOM4J document for the specified key. The key must point to an XML
     * document, or a OXFException is raised.
     *
     * @param key A Resource Manager key
     * @return  a document element
     */
    public org.dom4j.Document getContentAsDOM4J(String key);

    /**
     * Gets a document form the resource manager and send SAX events to the
     * specified content handler. the key must point to an XML
     * document, or a OXFException is raised.
     *
     * @param key       A Resource Manager key
     * @param handler   The content handler where SAX events are sent
     */
    public void getContentAsSAX(String key, ContentHandler handler);

    /**
     * Gets a document form the resource manager and send SAX events to the
     * specified content handler. the key must point to an XML
     * document, or a OXFException is raised.
     * 
     * @param key               A Resource Manager key
     * @param handler           The content handler where SAX events are sent
     * @param validating        Whether the XML parser must attempt to validate the resource
     * @param handleXInclude    Whether the XML parser must process XInclude instructions
     */
    public void getContentAsSAX(String key, ContentHandler handler, boolean validating, boolean handleXInclude);

    /**
     * Returns a character reader from the resource manager for the specified
     * key. The key could point to any text document.
     * @param key A Resource Manager key
     * @return a character reader
     */
//    public Reader getContentAsReader(String key);

    /**
     * Returns a binary input stream for the specified key. The key could point
     * to any document type (text or binary).
     * @param key A Resource Manager key
     * @return a input stream
     */
    public InputStream getContentAsStream(String key);

    /**
     * Returns a XMLReader interface to the resource manager. One should then
     * use the <code>setContentHandler()<code> and <code>parse(String key)</code
     * method to get and parse an XML document into SAX events.
     *
     * @return An XML reader
     */
    public XMLReader getXMLReader();

    /**
     * Gets the last modified timestamp for the specified resource
     * @param key A Resource Manager key
     * @param doNotThrowResourceNotFound
     * @return a timestamp
     */
    public long lastModified(String key, boolean doNotThrowResourceNotFound);

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key);

    /**
     * Indicates if the resource manager implementation suports write operations
     * @param key A Resource Manager key
     * @return true if write operations are allowed
     */
    public boolean canWrite(String key);

    /**
     * Write the specified document in the Resource Manager
     * @param key A Resource Manager key
     * @param node The W3C DOM node to write
     */
//    public void writeDOM(String key, Node node);

    /**
     * Write the specified document in the Resource Manager
     * @param key A Resource Manager key
     * @param document A DOM4J document
     */
//    public void writeDOM4J(String key, org.dom4j.Document document);

    /**
     * Returns a ContentHandler that can write to the Resource Manager
     * @param key A Resource Manager key
     * @return A ContentHandler
     */
    public ContentHandler getWriteContentHandler(String key);

    /**
     * Allows writing to the resource
     * @param key A Resource Manager key
     * @return an output stream
     */
    public OutputStream getOutputStream(String key);

    /**
     * Allow writing to the resource
     * @param key A Resource Manager key
     * @return  a writer
     */
    public Writer getWriter(String key);

    /**
     * Returns the path to the given resource on the file system. If a path on
     * the local file system cannot be provided by the resource manager, null is
     * returned.
     */
    public String getRealPath(String key);

    /**
     * Check if a resource exists given its key.
     *
     * @param key   A Resource Manager key
     * @return      true iif the resource exists
     */
    public boolean exists(String key);
}
