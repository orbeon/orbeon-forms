package org.orbeon.saxon.dom4j;

import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.event.PipelineConfiguration;
import org.orbeon.saxon.event.Receiver;
import org.orbeon.saxon.expr.JPConverter;
import org.orbeon.saxon.expr.PJConverter;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.pattern.AnyNodeTest;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.value.SingletonNode;
import org.orbeon.saxon.value.Value;
import org.dom4j.*;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import java.io.Serializable;


/**
 * This class is the DOM4J implementation of Saxon's ExternalObjectModel interface; it supports
 * the wrapping of DOM4J documents as instances of the Saxon NodeInfo interface.
 */

public class DOM4JObjectModel implements ExternalObjectModel, Serializable {

    public DOM4JObjectModel() {}

    /**
     * Get the URI of the external object model as used in the JAXP factory interfaces for obtaining
     * an XPath implementation
     */

    public String getIdentifyingURI() {
        return NamespaceConstant.OBJECT_MODEL_DOM4J;
    }

    public PJConverter getPJConverter(Class targetClass) {
        if (isRecognizedNodeClass(targetClass)) {
            return new PJConverter() {
                public Object convert(ValueRepresentation value, Class targetClass, XPathContext context) throws XPathException {
                    return convertXPathValueToObject(Value.asValue(value), targetClass);
                }
            };
        } else {
            return null;
        }
    }

    public JPConverter getJPConverter(Class targetClass) {
        if (isRecognizedNodeClass(targetClass)) {
            return new JPConverter() {
                public ValueRepresentation convert(Object object, XPathContext context) throws XPathException {
                    return convertObjectToXPathValue(object, context.getConfiguration());
                }
                public ItemType getItemType() {
                    return AnyNodeTest.getInstance();
                }
            };
        } else {
            return null;
        }
    }

    /**
     * Get a converter that converts a sequence of XPath nodes to this model's representation
     * of a node list.
     * @param node an example of the kind of node used in this model
     * @return if the model does not recognize this node as one of its own, return null. Otherwise
     *         return a PJConverter that takes a list of XPath nodes (represented as NodeInfo objects) and
     *         returns a collection of nodes in this object model
     */

    public PJConverter getNodeListCreator(Object node) {
        return null;
    }

    /**
     * Test whether this object model recognizes a given node as one of its own
     */

    private boolean isRecognizedNode(Object object) {
         return object instanceof Document ||
                 object instanceof Element ||
                 object instanceof Attribute ||
                 object instanceof Text ||
                 object instanceof CDATA ||
                 object instanceof Comment ||
                 object instanceof ProcessingInstruction ||
                 object instanceof Namespace;
    }

    /**
     * Test whether this object model recognizes a given class as representing a
     * node in that object model. This method will generally be called at compile time.
     *
     * @param nodeClass A class that possibly represents nodes
     * @return true if the class is used to represent nodes in this object model
     */

    private boolean isRecognizedNodeClass(Class nodeClass) {
        return Document.class.isAssignableFrom(nodeClass) ||
                Element.class.isAssignableFrom(nodeClass) ||
                Attribute.class.isAssignableFrom(nodeClass) ||
                Text.class.isAssignableFrom(nodeClass) ||
                CDATA.class.isAssignableFrom(nodeClass) ||
                Comment.class.isAssignableFrom(nodeClass) ||
                ProcessingInstruction.class.isAssignableFrom(nodeClass) ||
                Namespace.class.isAssignableFrom(nodeClass);
    }

    /**
     * Test whether this object model recognizes a particular kind of JAXP Result object,
     * and if it does, return a Receiver that builds an instance of this data model from
     * a sequence of events. If the Result is not recognised, return null.
     */

    public Receiver getDocumentBuilder(Result result) {
        return null;
    }

    /**
     * Test whether this object model recognizes a particular kind of JAXP Source object,
     * and if it does, send the contents of the document to a supplied Receiver, and return true.
     * Otherwise, return false.
     */

    public boolean sendSource(Source source, Receiver receiver, PipelineConfiguration pipe) throws XPathException {
        return false;
    }

    /**
     * Wrap or unwrap a node using this object model to return the corresponding Saxon node. If the supplied
     * source does not belong to this object model, return null
     */

    public NodeInfo unravel(Source source, Configuration config) {
        return null;
    }

    /**
     * Convert a Java object to an XPath value. If the supplied object is recognized as a representation
     * of a value using this object model, the object model should convert the value to an XPath value
     * and return this as the result. If not, it should return null. If the object is recognized but cannot
     * be converted, an exception should be thrown
     */

    private ValueRepresentation convertObjectToXPathValue(Object object, Configuration config) throws XPathException {
        if (isRecognizedNode(object)) {
            if (object instanceof Document) {
                return wrapDocument(object, null, config);
            } else {
                Document root = getDocumentRoot(object);
                DocumentInfo docInfo = wrapDocument(root, null, config);
                return wrapNode(docInfo, object);
            }
        } else {
            return null;
        }
    }

    /**
     * Convert an XPath value to an object in this object model. If the supplied value can be converted
     * to an object in this model, of the specified class, then the conversion should be done and the
     * resulting object returned. If the value cannot be converted, the method should return null. Note
     * that the supplied class might be a List, in which case the method should inspect the contents of the
     * Value to see whether they belong to this object model.
     */

    private Object convertXPathValueToObject(Value value, Class targetClass) {
        if (value instanceof SingletonNode) {
            NodeInfo node = ((SingletonNode)value).getNode();
            if (node instanceof VirtualNode) {
                Object u = ((VirtualNode)node).getUnderlyingNode();
                if (targetClass.isAssignableFrom(u.getClass())) {
                    return u;
                }
            }
        }
        return null;
    }

    /**
     * Wrap a document node in the external object model in a document wrapper that implements
     * the Saxon DocumentInfo interface
     * @param node    a node (any node) in the third party document
     * @param baseURI the base URI of the node (supply "" if unknown)
     * @param config the Saxon configuration (which among other things provides access to the NamePool)
     * @return the wrapper, which must implement DocumentInfo
     */

    private DocumentInfo wrapDocument(Object node, String baseURI, Configuration config) {
        Document documentNode = getDocumentRoot(node);
        return new org.orbeon.saxon.dom4j.DocumentWrapper(documentNode, baseURI, config);
    }

    /**
     * Wrap a node within the external object model in a node wrapper that implements the Saxon
     * VirtualNode interface (which is an extension of NodeInfo)
     * @param document the document wrapper, as a DocumentInfo object
     * @param node the node to be wrapped. This must be a node within the document wrapped by the
     * DocumentInfo provided in the first argument
     * @return the wrapper for the node, as an instance of VirtualNode
     */

    private NodeInfo wrapNode(DocumentInfo document, Object node) {
        return ((DocumentWrapper)document).wrap(node);
    }

    /**
     * Get the document root
     */

    private Document getDocumentRoot(Object node) {
        while (!(node instanceof Document)) {
            if (node instanceof Element) {
                if (((Element)node).isRootElement()) {
                    return ((Element)node).getDocument();
                } else {
                    node = ((Element)node).getParent();
                }
            } else if (node instanceof Text) {
                node = ((Text)node).getParent();
            } else if (node instanceof Comment) {
                node = ((Comment)node).getParent();
            } else if (node instanceof ProcessingInstruction) {
                node = ((ProcessingInstruction)node).getParent();
            } else if (node instanceof Attribute) {
                node = ((Attribute)node).getParent();
            } else if (node instanceof Document) {
                return (Document)node;
            } else if (node instanceof Namespace) {
                throw new UnsupportedOperationException("Cannot find parent of DOM4J namespace node");
            } else {
                throw new IllegalStateException("Unknown DOM4J node type " + node.getClass());
            }
        }
        return (Document)node;
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
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): Gunther Schadow (changes to allow access to public fields; also wrapping
// of extensions and mapping of null to empty sequence).
//
