package org.orbeon.saxon.dom4j;

import org.dom4j.Document;
import org.orbeon.saxon.Configuration;

/**
 * This wrapper is an extension of the Saxon node wrapper which is aware of XForms type annotations.
 */
public class TypedDocumentWrapper extends DocumentWrapper {

    public TypedDocumentWrapper(Document document, String s, Configuration configuration) {
        super(document, s, configuration);
    }

    @Override
    protected NodeWrapper makeWrapper(Object node, DocumentWrapper docWrapper, NodeWrapper parent, int index) {
        return TypedNodeWrapper.makeTypedWrapper(node, docWrapper, parent, index);
    }
}
