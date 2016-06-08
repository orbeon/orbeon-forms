package org.orbeon.saxon.dom;

import org.orbeon.dom.Document;
import org.orbeon.dom.Node;
import org.orbeon.saxon.Configuration;

/**
 * This wrapper is an extension of the Saxon node wrapper which is aware of XForms type annotations.
 */
public class TypedDocumentWrapper extends DocumentWrapper {

    public TypedDocumentWrapper(Document document, String s, Configuration configuration) {
        super(document, s, configuration);
    }

    @Override
    protected NodeWrapper makeWrapper(Node node, DocumentWrapper docWrapper, NodeWrapper parent, int index) {
        return TypedNodeWrapper.makeTypedWrapper(node, docWrapper, parent, index);
    }
}
