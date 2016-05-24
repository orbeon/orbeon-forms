package org.orbeon.saxon.dom4j;
import org.dom4j.*;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NamePool;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.type.Type;

import java.util.Iterator;
import java.util.Collections;

/**
  * The root node of an XPath tree. (Or equivalently, the tree itself).<P>
  * This class should have been named Root; it is used not only for the root of a document,
  * but also for the root of a result tree fragment, which is not constrained to contain a
  * single top-level element.
  * @author <A HREF="mailto:michael.h.kay@ntlworld.com>Michael H. Kay</A>
  */

public class DocumentWrapper extends NodeWrapper implements DocumentInfo {

    // An implementation of this interface can be set on DocumentWrapper to provide access to an index of elements by id.
    public interface IdGetter {
        Element apply(String id);
    }

    protected Configuration config;
    protected String baseURI;
    private int documentNumber;

    private IdGetter idGetter;

    /**
     * Create a Saxon wrapper for a dom4j document
     * @param doc     The dom4j document
     * @param baseURI The base URI for all the nodes in the document
     * @param config  The Saxon configuration
     */

    public DocumentWrapper(Document doc, String baseURI, Configuration config) {
        super(doc, null, 0);
        node = doc;
        nodeKind = Type.DOCUMENT;

        this.baseURI = baseURI;

        docWrapper = this;
        setConfiguration(config);
    }

    /**
     * Wrap a node in the dom4j document.
     * @param node The node to be wrapped. This must be a node in the same document
     * (the system does not check for this).
     * @return the wrapping NodeInfo object
     */

    public NodeInfo wrap(Object node) {
        if (node==this.node) {
            return this;
        }
        return makeWrapper(node, this);
    }

    /**
     *  Wrap a node without a document. The node must not have a document.
     */
    public static NodeInfo makeWrapper(Object node) {

        if (! (node instanceof Node))
            throw new IllegalArgumentException("Bad node type in dom4j: " + node.getClass() + " instance " + node.toString());

        assert(((Node) node).getDocument() == null);
        return makeWrapperImpl(node, null, null, -1);
    }

    /**
    * Get the unique document number
    */

    public int getDocumentNumber() {
        return documentNumber;
    }

    public void setIdGetter(IdGetter idGetter) {
        this.idGetter = idGetter;
    }

    /**
    * Get the element with a given ID, if any
    * @param id the required ID value
    * @return the element with the given ID, or null if there is no such ID
    */

    public NodeInfo selectID(String id) {
        if (idGetter == null) {
            return null;
        } else {
            final Element element = idGetter.apply(id);
            return element != null ? wrap(element) : null;
        }
    }

    /**
     * Get the list of unparsed entities defined in this document
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     */

    public Iterator getUnparsedEntityNames() {
        return Collections.EMPTY_LIST.iterator();
    }

    /**
    * Get the unparsed entity with a given name
    * @param name the name of the entity
    * @return null: dom4j does not provide access to unparsed entities
    */

    public String[] getUnparsedEntity(String name) {
        return null;
    }

    /**
     * Get the configuration previously set using setConfiguration
     * (or the default configuraton allocated automatically)
     */

    public Configuration getConfiguration() {
        return config;
    }


	/**
	* Get the name pool used for the names in this document
	*/

	public NamePool getNamePool() {
	    return config.getNamePool();
	}

	/**
	 * Set the configuration (containing the name pool used for all names in this document). Calling
     * this method allocates a unique number to the document (unique within the Configuration); this
     * will form the basis for testing node identity
     * @param config the configuration
	*/

	public void setConfiguration(Configuration config) {
        this.config = config;
		documentNumber = config.getDocumentNumberAllocator().allocateDocumentNumber();
	}

}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is
// Michael Kay (michael.h.kay@ntlworld.com).
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//
