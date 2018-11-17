package org.orbeon.dom.saxon;

import org.orbeon.dom.Document;
import org.orbeon.dom.Node;
import org.orbeon.saxon.Configuration;

/**
 * This wrapper is an extension of the Saxon node wrapper which is aware of XForms type annotations.
 */
public class TypedDocumentWrapper extends org.orbeon.dom.saxon.DocumentWrapper {

    public TypedDocumentWrapper(Document document, String s, Configuration configuration) {
        super(document, s, configuration);
    }

    @Override
    protected org.orbeon.dom.saxon.NodeWrapper makeWrapper(Node node, org.orbeon.dom.saxon.DocumentWrapper docWrapper, org.orbeon.dom.saxon.NodeWrapper parent) {
        return TypedNodeWrapper.makeTypedWrapper(node, docWrapper, parent);
    }
}
