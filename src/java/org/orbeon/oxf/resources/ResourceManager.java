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

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xml.XMLUtils;
import org.w3c.dom.Node;
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
     * @param key a Resource Manager key
     * @return a node element
     */
    Node getContentAsDOM(String key);

    /**
     * Gets a DOM4J document for the specified key. The key must point to an XML
     * document, or a OXFException is raised.
     *
     * @param key a Resource Manager key
     * @return  a document element
     */
    org.dom4j.Document getContentAsDOM4J(String key);
    
    /**
     * Gets a DOM4J document for the specified key. The key must point to an XML
     * document, or a OXFException is raised.
     *
     *
     * @param key                   a Resource Manager key
     * @param parserConfiguration   parser configuration
     * @param handleLexical         whether the XML parser must output SAX LexicalHandler events, including comments  @return  a document element
     */
    org.dom4j.Document getContentAsDOM4J(String key, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical);

    /**
     * Gets a document form the resource manager and send SAX events to the specified receiver. the key must point to an
     * XML document, or a OXFException is raised.
     *
     * @param key           a Resource Manager key
     * @param xmlReceiver   receiver where SAX events are sent
     */
    void getContentAsSAX(String key, XMLReceiver xmlReceiver);

    /**
     * Gets a document form the resource manager and send SAX events to the specified receiver. the key must point to an
     * XML document, or a OXFException is raised.
     *
     * @param key                   a Resource Manager key
     * @param xmlReceiver           receiver where SAX events are sent
     * @param parserConfiguration   parser configuration
     * @param handleLexical         whether the XML parser must output SAX LexicalHandler events, including comments
     */
    void getContentAsSAX(String key, XMLReceiver xmlReceiver, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical);

    /**
     * Returns a binary input stream for the specified key. The key could point
     * to any document type (text or binary).
     * @param key a Resource Manager key
     * @return a input stream
     */
    InputStream getContentAsStream(String key);

    /**
     * Returns a XMLReader interface to the resource manager. One should then
     * use the <code>setContentHandler()<code> and <code>parse(String key)</code
     * method to get and parse an XML document into SAX events.
     *
     * @return An XML reader
     */
    XMLReader getXMLReader();

    /**
     * Gets the last modified timestamp for the specified resource
     * @param key a Resource Manager key
     * @param doNotThrowResourceNotFound
     * @return a timestamp
     */
    long lastModified(String key, boolean doNotThrowResourceNotFound);

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    int length(String key);

    /**
     * Indicates if the resource manager implementation suports write operations
     * @param key a Resource Manager key
     * @return true if write operations are allowed
     */
    boolean canWrite(String key);

    /**
     * Returns a receiver that can write to the Resource Manager.
     *
     * @param key   a Resource Manager key
     * @return      receiver
     */
    XMLReceiver getWriteContentHandler(String key);

    /**
     * Allows writing to the resource
     * @param key a Resource Manager key
     * @return an output stream
     */
    OutputStream getOutputStream(String key);

    /**
     * Allow writing to the resource
     * @param key a Resource Manager key
     * @return  a writer
     */
    Writer getWriter(String key);

    /**
     * Returns the path to the given resource on the file system. If a path on
     * the local file system cannot be provided by the resource manager, null is
     * returned.
     */
    String getRealPath(String key);

    /**
     * Check if a resource exists given its key.
     *
     * @param key   a Resource Manager key
     * @return      true iif the resource exists
     */
    boolean exists(String key);
}
