package org.orbeon.oxf.transformer.xupdate;

import org.dom4j.Document;

import java.util.Map;
import java.util.HashMap;

public class DocumentContext {

    private Map uriToDocument = new HashMap();

    public void addDocument(String uri, Document document) {
        uriToDocument.put(uri, document);
    }

    public Document getDocument(String uri) {
        return (Document) uriToDocument.get(uri);
    }
}
