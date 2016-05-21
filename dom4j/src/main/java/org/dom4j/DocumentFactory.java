/*
 * Copyright 2001-2005 (C) MetaStuff, Ltd. All Rights Reserved.
 *
 * This software is open source.
 * See the bottom of this file for the licence.
 */

package org.dom4j;

import org.dom4j.tree.*;
import org.dom4j.util.NonLazyUserDataElement;
import org.dom4j.util.UserDataAttribute;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Map;

/**
 * <p>
 * <code>DocumentFactory</code> is a collection of factory methods to allow
 * easy custom building of DOM4J trees. The default tree that is built uses a
 * doubly linked tree.
 * </p>
 *
 * @author James Strachan
 */
public class DocumentFactory implements Serializable {

    private static final DocumentFactory SINGLETON = new DocumentFactory();

    public static DocumentFactory getInstance() {
        return SINGLETON;
    }

    protected transient QNameCache cache;

    public DocumentFactory() {
        init();
    }

    // Factory methods
    public Document createDocument() {
        DefaultDocument answer = new DefaultDocument();
        answer.setDocumentFactory(this);

        return answer;
    }

    /**
     * DOCUMENT ME!
     *
     * @param encoding
     *            DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     *
     * @since 1.5
     */
    public Document createDocument(String encoding) {
        // to keep the DocumentFactory backwards compatible, we have to do this
        // in this not so nice way, since subclasses only need to extend the
        // createDocument() method.
        Document answer = createDocument();

        if (answer instanceof AbstractDocument) {
            answer.setXMLEncoding(encoding);
        }

        return answer;
    }

    public Document createDocument(Element rootElement) {
        Document answer = createDocument();
        answer.setRootElement(rootElement);

        return answer;
    }

    public Element createElement(final QName qName) {
        return new NonLazyUserDataElement(qName);
    }

    public Element createElement(String name) {
        return createElement(createQName(name));
    }

    public Element createElement(String qualifiedName, String namespaceURI) {
        return createElement(createQName(qualifiedName, namespaceURI));
    }

    public Attribute createAttribute(Element owner, String name, String value) {
        return createAttribute(owner, createQName(name), value);
    }

    public Attribute createAttribute(final Element ownerElement, final QName qName, final String value) {
        return new UserDataAttribute(qName, value);
    }

    public CDATA createCDATA(String text) {
        return new DefaultCDATA(text);
    }

    public Comment createComment(String text) {
        return new DefaultComment(text);
    }

    public Text createText(String text) {
        if (text == null) {
            String msg = "Adding text to an XML document must not be null";
            throw new IllegalArgumentException(msg);
        }

        return new DefaultText(text);
    }

    public Entity createEntity(String name, String text) {
        return new DefaultEntity(name, text);
    }

    public Namespace createNamespace(String prefix, String uri) {
        return Namespace.get(prefix, uri);
    }

    public ProcessingInstruction createProcessingInstruction(String target,
            String data) {
        return new DefaultProcessingInstruction(target, data);
    }

    public ProcessingInstruction createProcessingInstruction(String target,
                                                             Map<String, String> data) {
        return new DefaultProcessingInstruction(target, data);
    }

    public QName createQName(String localName, Namespace namespace) {
        return cache.get(localName, namespace);
    }

    public QName createQName(String localName) {
        return cache.get(localName);
    }

    public QName createQName(String qualifiedName, String uri) {
        return cache.get(qualifiedName, uri);
    }

    // Properties
    // -------------------------------------------------------------------------

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param qname
     *            DOCUMENT ME!
     *
     * @return the cached QName instance if there is one or adds the given qname
     *         to the cache if not
     */
    protected QName intern(QName qname) {
        return cache.intern(qname);
    }

    /**
     * Factory method to create the QNameCache. This method should be overloaded
     * if you wish to use your own derivation of QName.
     *
     * @return DOCUMENT ME!
     */
    protected QNameCache createQNameCache() {
        return new QNameCache(this);
    }

    private void readObject(ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        in.defaultReadObject();
        init();
    }

    protected void init() {
        cache = createQNameCache();
    }
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