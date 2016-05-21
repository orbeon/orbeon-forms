/*
 * Copyright 2001-2005 (C) MetaStuff, Ltd. All Rights Reserved.
 *
 * This software is open source.
 * See the bottom of this file for the licence.
 */

package org.dom4j;

import java.io.IOException;
import java.io.Writer;

/**
 * <p>
 * <code>Node</code> defines the polymorphic behavior for all XML nodes in a
 * dom4j tree.
 * </p>
 *
 * <p>
 * A node can be output as its XML format, can be detached from its position in
 * a document and can have XPath expressions evaluated on itself.
 * </p>
 *
 * <p>
 * A node may optionally support the parent relationship and may be read only.
 * </p>
 *
 * @author James Strachan
 *
 * @see #supportsParent
 * @see #isReadOnly
 */
public interface Node extends Cloneable {
    // W3C DOM complient node type codes

    /** Matches Element nodes */
    short ELEMENT_NODE = 1;

    /** Matches elements nodes */
    short ATTRIBUTE_NODE = 2;

    /** Matches elements nodes */
    short TEXT_NODE = 3;

    /** Matches elements nodes */
    short CDATA_SECTION_NODE = 4;

    /** Matches elements nodes */
    short ENTITY_REFERENCE_NODE = 5;

    /** Matches elements nodes */

    // public static final short ENTITY_NODE = 6;
    /** Matches ProcessingInstruction */
    short PROCESSING_INSTRUCTION_NODE = 7;

    /** Matches Comments nodes */
    short COMMENT_NODE = 8;

    /** Matches Document nodes */
    short DOCUMENT_NODE = 9;

    /** Matches DocumentType nodes */
    short DOCUMENT_TYPE_NODE = 10;

    // public static final short DOCUMENT_FRAGMENT_NODE = 11;
    // public static final short NOTATION_NODE = 12;

    /** Matchs a Namespace Node - NOTE this differs from DOM */

    // XXXX: ????
    short NAMESPACE_NODE = 13;

    /** Does not match any valid node */
    short UNKNOWN_NODE = 14;

    /** The maximum number of node types for sizing purposes */
    short MAX_NODE_TYPE = 14;

    /**
     * <p>
     * <code>supportsParent</code> returns true if this node supports the
     * parent relationship.
     * </p>
     *
     * <p>
     * Some XML tree implementations are singly linked and only support downward
     * navigation through children relationships. The default case is that both
     * parent and children relationships are supported though for memory and
     * performance reasons the parent relationship may not be supported.
     * </p>
     *
     * @return true if this node supports the parent relationship or false it is
     *         not supported
     */
    boolean supportsParent();

    /**
     * <p>
     * <code>getParent</code> returns the parent <code>Element</code> if
     * this node supports the parent relationship or null if it is the root
     * element or does not support the parent relationship.
     * </p>
     *
     * <p>
     * This method is an optional feature and may not be supported for all
     * <code>Node</code> implementations.
     * </p>
     *
     * @return the parent of this node or null if it is the root of the tree or
     *         the parent relationship is not supported.
     */
    Element getParent();

    /**
     * <p>
     * <code>setParent</code> sets the parent relationship of this node if the
     * parent relationship is supported or does nothing if the parent
     * relationship is not supported.
     * </p>
     *
     * <p>
     * This method should only be called from inside an <code>Element</code>
     * implementation method and is not intended for general use.
     * </p>
     *
     * @param parent
     *            is the new parent of this node.
     */
    void setParent(Element parent);

    /**
     * <p>
     * <code>getDocument</code> returns the <code>Document</code> that this
     * <code>Node</code> is part of if this node supports the parent
     * relationship.
     * </p>
     *
     * <p>
     * This method is an optional feature and may not be supported for all
     * <code>Node</code> implementations.
     * </p>
     *
     * @return the document of this node or null if this feature is not
     *         supported or the node is not associated with a
     *         <code>Document</code>
     */
    Document getDocument();

    /**
     * <p>
     * <code>setDocument</code> sets the document of this node if the parent
     * relationship is supported or does nothing if the parent relationship is
     * not supported.
     * </p>
     *
     * <p>
     * This method should only be called from inside a <code>Document</code>
     * implementation method and is not intended for general use.
     * </p>
     *
     * @param document
     *            is the new document of this node.
     */
    void setDocument(Document document);

    /**
     * <p>
     * <code>isReadOnly</code> returns true if this node is read only and
     * cannot be modified. Any attempt to modify a read-only <code>Node</code>
     * will result in an <code>UnsupportedOperationException</code> being
     * thrown.
     * </p>
     *
     * @return true if this <code>Node</code> is read only and cannot be
     *         modified otherwise false.
     */
    boolean isReadOnly();

    /**
     * <p>
     * <code>hasContent</code> returns true if this node is a Branch (either
     * an Element or a Document) and it contains at least one content node such
     * as a child Element or Text node.
     * </p>
     *
     * @return true if this <code>Node</code> is a Branch with a nodeCount()
     *         of one or more.
     */
    boolean hasContent();

    /**
     * <p>
     * <code>getName</code> returns the name of this node. This is the XML
     * local name of the element, attribute, entity or processing instruction.
     * For CDATA and Text nodes this method will return null.
     * </p>
     *
     * @return the XML name of this node
     */
    String getName();

    /**
     * <p>
     * Sets the text data of this node or this method will throw an
     * <code>UnsupportedOperationException</code> if it is read-only.
     * </p>
     *
     * @param name
     *            is the new name of this node
     */
    void setName(String name);

    /**
     * <p>
     * Returns the text of this node.
     * </p>
     *
     * @return the text for this node.
     */
    String getText();

    /**
     * <p>
     * Sets the text data of this node or this method will throw an
     * <code>UnsupportedOperationException</code> if it is read-only.
     * </p>
     *
     * @param text
     *            is the new textual value of this node
     */
    void setText(String text);

    /**
     * Returns the XPath string-value of this node. The behaviour of this method
     * is defined in the <a href="http://www.w3.org/TR/xpath">XPath
     * specification </a>.
     *
     * @return the text from all the child Text and Element nodes appended
     *         together.
     */
    String getStringValue();

    /**
     * <p>
     * Returns the XPath expression which will return a nodeset of one node
     * which is the current node. This method will use the XPath index operator
     * to restrict the path if multiple elements with the same name occur on the
     * path.
     * </p>
     *
     * @return the XPath expression which will return a nodeset containing just
     *         this node.
     */
    String getUniquePath();

    /**
     * <p>
     * Returns the relative unique XPath expression from the given context which
     * will return a nodeset of one node which is the current node. This method
     * will use the XPath index operator to restrict the path if multiple
     * elements with the same name occur on the path.
     * </p>
     *
     * @param context
     *            is the parent context from which the path should start. If the
     *            context is null or the context is not an ancestor of this node
     *            then the path will start from the document and so begin with
     *            the '/' character.
     *
     * @return the XPath expression relative to the given context which will
     *         return a nodeset containing just this node.
     */
    String getUniquePath(Element context);

    /**
     * <p>
     * <code>asXML</code> returns the textual XML representation of this node.
     * </p>
     *
     * @return the XML representation of this node
     */
    String asXML();

    /**
     * <p>
     * <code>write</code> writes this node as the default XML notation for
     * this node. If you wish to control the XML output (such as for pretty
     * printing, changing the indentation policy etc.) then please use {@link
     * org.dom4j.io.XMLWriter} or its derivations.
     * </p>
     *
     * @param writer
     *            is the <code>Writer</code> to output the XML to
     *
     * @throws IOException
     *             DOCUMENT ME!
     */
    void write(Writer writer) throws IOException;

    /**
     * Returns the code according to the type of node. This makes processing
     * nodes polymorphically much easier as the switch statement can be used
     * instead of multiple if (instanceof) statements.
     *
     * @return a W3C DOM complient code for the node type such as ELEMENT_NODE
     *         or ATTRIBUTE_NODE
     */
    short getNodeType();

    /**
     * DOCUMENT ME!
     *
     * @return the name of the type of node such as "Document", "Element",
     *         "Attribute" or "Text"
     */
    String getNodeTypeName();

    /**
     * <p>
     * Removes this node from its parent if there is one. If this node is the
     * root element of a document then it is removed from the document as well.
     * </p>
     *
     * <p>
     * This method is useful if you want to remove a node from its source
     * document and add it to another document. For example
     * </p>
     * <code> Node node = ...; Element someOtherElement = ...;
     * someOtherElement.add( node.detach() ); </code>
     *
     * @return the node that has been removed from its parent node if any and
     *         its document if any.
     */
    Node detach();

    /**
     * <p>
     * <code>accept</code> is the method used in the Visitor Pattern.
     * </p>
     *
     * @param visitor
     *            is the visitor in the Visitor Pattern
     */
    void accept(Visitor visitor);

    /**
     * <p>
     * <code>clone</code> will return a deep clone or if this node is
     * read-only then clone will return the same instance.
     * </p>
     *
     * @return a deep clone of myself or myself if I am read only.
     */
    Object clone();
}

/*
 * Redistribution and use of this software and associated documentation
 * ("Software"), with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain copyright statements and
 * notices. Redistributions must also contain a copy of this document.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The name "DOM4J" must not be used to endorse or promote products derived
 * from this Software without prior written permission of MetaStuff, Ltd. For
 * written permission, please contact dom4j-info@metastuff.com.
 *
 * 4. Products derived from this Software may not be called "DOM4J" nor may
 * "DOM4J" appear in their names without prior written permission of MetaStuff,
 * Ltd. DOM4J is a registered trademark of MetaStuff, Ltd.
 *
 * 5. Due credit should be given to the DOM4J Project - http://www.dom4j.org
 *
 * THIS SOFTWARE IS PROVIDED BY METASTUFF, LTD. AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL METASTUFF, LTD. OR ITS CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2001-2005 (C) MetaStuff, Ltd. All Rights Reserved.
 */
