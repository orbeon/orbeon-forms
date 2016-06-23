package org.orbeon.dom.saxon;

import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.dom.Node;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NamePool;
import org.orbeon.saxon.om.NodeInfo;

import java.util.Collections;
import java.util.Iterator;

/**
 * The root node of an XPath tree. (Or equivalently, the tree itself).
 * This class should have been named Root; it is used not only for the root of a document,
 * but also for the root of a result tree fragment, which is not constrained to contain a
 * single top-level element.
 *
 * @author Michael H. Kay
 */
public class DocumentWrapper extends org.orbeon.dom.saxon.NodeWrapper implements DocumentInfo {

    // An implementation of this interface can be set on DocumentWrapper to provide access to an index of elements by id.
    public interface IdGetter {
        Element apply(String id);
    }

    protected Configuration config;
    protected String baseURI;
    private int documentNumber;

    private IdGetter idGetter;

    public DocumentWrapper(Document doc, String baseURI, Configuration config) {
        super(doc, null, 0);

        this.baseURI = baseURI;

        this.docWrapper = this;
        this.config = config;
        this.documentNumber = config.getDocumentNumberAllocator().allocateDocumentNumber();
    }
    public NodeInfo wrap(Node node) {
        if (node == this.node) {
            return this;
        }
        return makeWrapper(node, this);
    }

    /**
     * Wrap a node without a document. The node must not have a document.
     */
    public static NodeInfo makeWrapper(Node node) {
        assert (node.getDocument() == null);
        return makeWrapperImpl(node, null, null, -1);
    }

    public int getDocumentNumber() {
        return documentNumber;
    }

    public void setIdGetter(IdGetter idGetter) {
        this.idGetter = idGetter;
    }

    public NodeInfo selectID(String id) {
        if (idGetter == null) {
            return null;
        } else {
            final Element element = idGetter.apply(id);
            return element != null ? wrap(element) : null;
        }
    }

    public Iterator getUnparsedEntityNames() {
        return Collections.EMPTY_LIST.iterator();
    }
    public String[] getUnparsedEntity(String name) {
        return null;
    }
    public Configuration getConfiguration() {
        return config;
    }
    public NamePool getNamePool() {
        return config.getNamePool();
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
