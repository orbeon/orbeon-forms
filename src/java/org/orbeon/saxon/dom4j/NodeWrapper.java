package org.orbeon.saxon.dom4j;
import org.orbeon.saxon.event.Receiver;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.pattern.AnyNodeTest;
import org.orbeon.saxon.pattern.NodeTest;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.UntypedAtomicValue;
import org.orbeon.saxon.style.StandardNames;
import org.dom4j.*;

import javax.xml.transform.TransformerException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
  * A node in the XML parse tree representing an XML element, character content, or attribute.<P>
  * This is the implementation of the NodeInfo interface used as a wrapper for JDOM nodes.
  * @author <A HREF="mailto:michael.h.kay@ntlworld.com>Michael H. Kay</A>
  */

public class NodeWrapper implements NodeInfo, VirtualNode, SiblingCountingNode {

    protected Object node;
    protected short nodeKind;
    private NodeWrapper parent;     // null means unknown
    protected DocumentWrapper docWrapper;
    protected int index;            // -1 means unknown

    /**
     * This constructor is protected: nodes should be created using the wrap
     * factory method on the DocumentWrapper class
     * @param node    The JDOM node to be wrapped
     * @param parent  The NodeWrapper that wraps the parent of this node
     * @param index   Position of this node among its siblings
     */
    protected NodeWrapper(Object node, NodeWrapper parent, int index) {
        this.node = node;
        this.parent = parent;
        this.index = index;
    }

    /**
     * Factory method to wrap a JDOM node with a wrapper that implements the Saxon
     * NodeInfo interface.
     * @param node        The JDOM node
     * @param docWrapper  The wrapper for the Document containing this node
     * @return            The new wrapper for the supplied node
     */
    protected NodeWrapper makeWrapper(Object node, DocumentWrapper docWrapper) {
        return makeWrapper(node, docWrapper, null, -1);
    }

    /**
     * Factory method to wrap a JDOM node with a wrapper that implements the Saxon
     * NodeInfo interface.
     * @param node        The JDOM node
     * @param docWrapper  The wrapper for the Document containing this node
     * @param parent      The wrapper for the parent of the JDOM node
     * @param index       The position of this node relative to its siblings
     * @return            The new wrapper for the supplied node
     */

    protected NodeWrapper makeWrapper(Object node, DocumentWrapper docWrapper,
                                      NodeWrapper parent, int index) {
        NodeWrapper wrapper;
        if (node instanceof Document) {
            return docWrapper;
        } else if (node instanceof Element) {
            wrapper = new NodeWrapper(node, parent, index);
            wrapper.nodeKind = Type.ELEMENT;
        } else if (node instanceof Attribute) {
            wrapper = new NodeWrapper(node, parent, index);
            wrapper.nodeKind = Type.ATTRIBUTE;
        } else if (node instanceof String || node instanceof Text || node instanceof CDATA) {
            wrapper = new NodeWrapper(node, parent, index);
            wrapper.nodeKind = Type.TEXT;
        } else if (node instanceof Comment) {
            wrapper = new NodeWrapper(node, parent, index);
            wrapper.nodeKind = Type.COMMENT;
        } else if (node instanceof ProcessingInstruction) {
            wrapper = new NodeWrapper(node, parent, index);
            wrapper.nodeKind = Type.PROCESSING_INSTRUCTION;
        } else if (node instanceof Namespace) {
            wrapper = new NodeWrapper(node, parent, index);
            wrapper.nodeKind = Type.NAMESPACE;
        } else {
            throw new IllegalArgumentException("Bad node type in dom4j! " + node.getClass() + " instance " + node.toString());
        }
        wrapper.docWrapper = docWrapper;
        return wrapper;
    }

    /**
    * Get the underlying DOM node, to implement the VirtualNode interface
    */

    public Object getUnderlyingNode() {
        return node;
    }


    /**
     * Get the name pool for this node
     * @return the NamePool
     */

    public NamePool getNamePool() {
        return docWrapper.getNamePool();
    }

    /**
    * Return the type of node.
    * @return one of the values Node.ELEMENT, Node.TEXT, Node.ATTRIBUTE, etc.
    */

    public int getNodeKind() {
        return nodeKind;
    }

    /**
    * Get the typed value of the item
    */

    public SequenceIterator getTypedValue() {
        return SingletonIterator.makeIterator(new UntypedAtomicValue(getStringValue()));
    }

    /**
    * Get the type annotation
    * @return 0 (there is no type annotation)
    */

    public int getTypeAnnotation() {
        return 0;
    }

    /**
    * Determine whether this is the same node as another node. <br />
    * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
    * @return true if this Node object and the supplied Node object represent the
    * same node in the tree.
    */

    public boolean isSameNode(NodeInfo other) {
        if (!(other instanceof NodeWrapper)) {
            return false;
        }
        NodeWrapper ow = (NodeWrapper)other;
        return node.equals(ow.node);
    }

    /**
    * Get the System ID for the node.
    * @return the System Identifier of the entity in the source document containing the node,
    * or null if not known. Note this is not the same as the base URI: the base URI can be
    * modified by xml:base, but the system ID cannot.
    */

    public String getSystemId() {
        return docWrapper.baseURI;
    }

    public void setSystemId(String uri) {
        docWrapper.baseURI = uri;
    }

    /**
    * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node. In the JDOM model, base URIs are held only an the document level. We don't
     * currently take any account of xml:base attributes.
    */

    public String getBaseURI() {
        if (getNodeKind() == Type.NAMESPACE) {
            return null;
        }
        NodeInfo n = this;
        if (getNodeKind() != Type.ELEMENT) {
            n = getParent();
        }
        // Look for an xml:base attribute
        while (n != null) {
            String xmlbase = n.getAttributeValue(StandardNames.XML_BASE);
            if (xmlbase != null) {
                return xmlbase;
            }
        }
        // if not found, return the base URI of the document node
        return docWrapper.baseURI;
    }

    /**
    * Get line number
    * @return the line number of the node in its original source document; or -1 if not available
    */

    public int getLineNumber() {
        return -1;
    }

    /**
    * Determine the relative position of this node and another node, in document order.
    * The other node will always be in the same document.
    * @param other The other node, whose position is to be compared with this node
    * @return -1 if this node precedes the other node, +1 if it follows the other
    * node, or 0 if they are the same node. (In this case, isSameNode() will always
    * return true, and the two nodes will produce the same result for generateId())
    */

    public int compareOrder(NodeInfo other) {
        return Navigator.compareOrder(this, (SiblingCountingNode)other);
    }

    /**
    * Return the string value of the node. The interpretation of this depends on the type
    * of node. For an element it is the accumulated character content of the element,
    * including descendant elements.
    * @return the string value of the node
    */

    public String getStringValue() {
        return getStringValue(node);
    }

    private static String getStringValue(Object node) {
        // TODO: from JDOM beta 10, there is a method to provide the XPath string value: use it
        if (node instanceof Document) {
            return ((Document)node).getStringValue();
        } else if (node instanceof Element) {
            return ((Element)node).getStringValue();
        } else if (node instanceof Attribute) {
                return ((Attribute)node).getValue();
        } else if (node instanceof Text) {
            return ((Text)node).getText();
        } else if (node instanceof CDATA) {
            return ((CDATA)node).getText();
        } else if (node instanceof String) {
            return (String)node;
        } else if (node instanceof Comment) {
            return ((Comment)node).getText();
        } else if (node instanceof ProcessingInstruction) {
            return ((ProcessingInstruction)node).getStringValue();
        } else if (node instanceof Namespace) {
            return ((Namespace)node).getURI();
        } else {
            return "";
        }
    }

    /**
    * Get name code. The name code is a coded form of the node name: two nodes
    * with the same name code have the same namespace URI, the same local name,
    * and the same prefix. By masking the name code with &0xfffff, you get a
    * fingerprint: two nodes with the same fingerprint have the same local name
    * and namespace URI.
    * @see org.orbeon.saxon.om.NamePool#allocate allocate
    */

    public int getNameCode() {
        switch (nodeKind) {
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                return docWrapper.namePool.allocate(getPrefix(),
                                                    getURI(),
                                                    getLocalPart());
            default:
                return -1;
        }
    }

    /**
    * Get fingerprint. The fingerprint is a coded form of the expanded name
    * of the node: two nodes
    * with the same name code have the same namespace URI and the same local name.
    * A fingerprint of -1 should be returned for a node with no name.
    */

    public int getFingerprint() {
        return getNameCode()&0xfffff;
    }

    /**
    * Get the local part of the name of this node. This is the name after the ":" if any.
    * @return the local part of the name. For an unnamed node, returns "".
    */

    public String getLocalPart() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element)node).getName();
            case Type.ATTRIBUTE:
                return ((Attribute)node).getName();
            case Type.TEXT:
            case Type.COMMENT:
            case Type.DOCUMENT:
                return "";
            case Type.PROCESSING_INSTRUCTION:
                return ((ProcessingInstruction)node).getTarget();
            case Type.NAMESPACE:
                return ((Namespace)node).getPrefix();
            default:
                return null;
        }
    }

    /**
    * Get the prefix part of the name of this node. This is the name before the ":" if any.
     * (Note, this method isn't required as part of the NodeInfo interface.)
    * @return the prefix part of the name. For an unnamed node, return an empty string.
    */

    public String getPrefix() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element)node).getNamespacePrefix();
            case Type.ATTRIBUTE:
                return ((Attribute)node).getNamespacePrefix();
            default:
                return "";
        }
    }

    /**
    * Get the URI part of the name of this node. This is the URI corresponding to the
    * prefix, or the URI of the default namespace if appropriate.
    * @return The URI of the namespace of this node. For an unnamed node, return null.
    * For a node with an empty prefix, return an empty string.
    */

    public String getURI() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element)node).getNamespaceURI();
            case Type.ATTRIBUTE:
                return ((Attribute)node).getNamespaceURI();
            default:
                return "";
        }
    }

    /**
    * Get the display name of this node. For elements and attributes this is [prefix:]localname.
    * For unnamed nodes, it is an empty string.
    * @return The display name of this node.
    * For a node with no name, return an empty string.
    */

    public String getDisplayName() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element)node).getQualifiedName();
            case Type.ATTRIBUTE:
                return ((Attribute)node).getQualifiedName();
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                return getLocalPart();
            default:
                return "";

        }
    }

    /**
    * Get the NodeInfo object representing the parent of this node
    */

    public NodeInfo getParent() {
        if (parent==null) {
            if (node instanceof Element) {
                if (((Element)node).isRootElement()) {
                    parent = makeWrapper(((Element)node).getDocument(), docWrapper);
                } else {
                    parent = makeWrapper(((Element)node).getParent(), docWrapper);
                }
            } else if (node instanceof Text) {
                parent = makeWrapper(((Text)node).getParent(), docWrapper);
            } else if (node instanceof CDATA) {
                parent = makeWrapper(((CDATA)node).getParent(), docWrapper);
            } else if (node instanceof Comment) {
                parent = makeWrapper(((Comment)node).getParent(), docWrapper);
            } else if (node instanceof ProcessingInstruction) {
                parent = makeWrapper(((ProcessingInstruction)node).getParent(), docWrapper);
            } else if (node instanceof Attribute) {
                parent = makeWrapper(((Attribute)node).getParent(), docWrapper);
            } else if (node instanceof Document) {
                parent = null;
            } else if (node instanceof Namespace) {
                throw new UnsupportedOperationException("Cannot find parent of JDOM namespace node");
            } else {
                throw new IllegalStateException("Unknown JDOM node type " + node.getClass());
            }
        }
        return parent;
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     */

    public int getSiblingPosition() {
        if (index == -1) {
            int ix = 0;
            getParent();
            AxisIterator iter;
            switch (nodeKind) {
                case Type.ELEMENT:
                case Type.TEXT:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                    iter = parent.iterateAxis(Axis.CHILD);
                    break;
                case Type.ATTRIBUTE:
                    iter = parent.iterateAxis(Axis.ATTRIBUTE);
                    break;
                case Type.NAMESPACE:
                    iter = parent.iterateAxis(Axis.NAMESPACE);
                    break;
                default:
                    index = 0;
                    return index;
            }
            while (true) {
                NodeInfo n = (NodeInfo)iter.next();
                if (n == null) {
                    break;
                }
                if (n.isSameNode(this)) {
                    index = ix;
                    return index;
                }
                ix++;
            }
            throw new IllegalStateException("JDOM node not linked to parent node");
        }
        return index;
    }

    /**
    * Return an iteration over the nodes reached by the given axis from this node
    * @param axisNumber the axis to be used
    * @return a SequenceIterator that scans the nodes reached by the axis in turn.
    */

    public AxisIterator iterateAxis(byte axisNumber) {
        return iterateAxis(axisNumber, AnyNodeTest.getInstance());
    }

    /**
    * Return an iteration over the nodes reached by the given axis from this node
    * @param axisNumber the axis to be used
    * @param nodeTest A pattern to be matched by the returned nodes
    * @return a SequenceIterator that scans the nodes reached by the axis in turn.
    */

    public AxisIterator iterateAxis(byte axisNumber, NodeTest nodeTest) {
        switch (axisNumber) {
            case Axis.ANCESTOR:
                if (nodeKind==Type.DOCUMENT) return EmptyIterator.getInstance();
                return new Navigator.AxisFilter(
                            new Navigator.AncestorEnumeration(this, false),
                            nodeTest);

            case Axis.ANCESTOR_OR_SELF:
                if (nodeKind==Type.DOCUMENT) return EmptyIterator.getInstance();
                return new Navigator.AxisFilter(
                            new Navigator.AncestorEnumeration(this, true),
                            nodeTest);

            case Axis.ATTRIBUTE:
                if (nodeKind!=Type.ELEMENT) return EmptyIterator.getInstance();
                return new Navigator.AxisFilter(
                            new AttributeEnumeration(this),
                            nodeTest);

            case Axis.CHILD:
                if (hasChildNodes()) {
                    return new Navigator.AxisFilter(
                            new ChildEnumeration(this, true, true),
                            nodeTest);
                } else {
                    return EmptyIterator.getInstance();
                }

            case Axis.DESCENDANT:
                if (hasChildNodes()) {
                    return new Navigator.AxisFilter(
                            new Navigator.DescendantEnumeration(this, false, true),
                            nodeTest);
                } else {
                    return EmptyIterator.getInstance();
                }

            case Axis.DESCENDANT_OR_SELF:
                 return new Navigator.AxisFilter(
                            new Navigator.DescendantEnumeration(this, true, true),
                            nodeTest);

            case Axis.FOLLOWING:
                 return new Navigator.AxisFilter(
                            new Navigator.FollowingEnumeration(this),
                            nodeTest);

            case Axis.FOLLOWING_SIBLING:
                 switch (nodeKind) {
                    case Type.DOCUMENT:
                    case Type.ATTRIBUTE:
                    case Type.NAMESPACE:
                        return EmptyIterator.getInstance();
                    default:
                        return new Navigator.AxisFilter(
                            new ChildEnumeration(this, false, true),
                            nodeTest);
                 }

            case Axis.NAMESPACE:
                 if (nodeKind!=Type.ELEMENT) {
                     return EmptyIterator.getInstance();
                 }
                 return new Navigator.AxisFilter(
                                new NamespaceEnumeration(this),
                                nodeTest);

            case Axis.PARENT:
                 getParent();
                 if (parent==null) return EmptyIterator.getInstance();
                 if (nodeTest.matches(parent.getNodeKind(), parent.getFingerprint(), -1)) {
                    return SingletonIterator.makeIterator(parent);
                 }
                 return EmptyIterator.getInstance();

            case Axis.PRECEDING:
                 return new Navigator.AxisFilter(
                            new Navigator.PrecedingEnumeration(this, false),
                            nodeTest);

            case Axis.PRECEDING_SIBLING:
                 switch (nodeKind) {
                    case Type.DOCUMENT:
                    case Type.ATTRIBUTE:
                    case Type.NAMESPACE:
                        return EmptyIterator.getInstance();
                    default:
                        return new Navigator.AxisFilter(
                            new ChildEnumeration(this, false, false),
                            nodeTest);
                 }

            case Axis.SELF:
                 if (nodeTest.matches(getNodeKind(), getFingerprint(), -1)) {
                     return SingletonIterator.makeIterator(this);
                 }
                 return EmptyIterator.getInstance();

            case Axis.PRECEDING_OR_ANCESTOR:
                 return new Navigator.AxisFilter(
                            new Navigator.PrecedingEnumeration(this, true),
                            nodeTest);

            default:
                 throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }

    /**
     * Find the value of a given attribute of this node. <BR>
     * This method is defined on all nodes to meet XSL requirements, but for nodes
     * other than elements it will always return null.
     * @param uri the namespace uri of an attribute ("" if no namespace)
     * @param localName the local name of the attribute
     * @return the value of the attribute, if it exists, otherwise null
     */

//    public String getAttributeValue(String uri, String localName) {
//        if (nodeKind==Type.ELEMENT) {
//            Namespace ns = Namespace.getNamespace(uri);
//            return ((Element)node).getAttributeValue(localName, ns);
//        } else {
//            return "";
//        }
//    }

    /**
    * Get the value of a given attribute of this node
    * @param fingerprint The fingerprint of the attribute name
    * @return the attribute value if it exists or null if not
    */

    public String getAttributeValue(int fingerprint) {
        if (nodeKind==Type.ELEMENT) {
            Iterator list = ((Element)node).attributes().iterator();
            NamePool pool = docWrapper.getNamePool();
            while (list.hasNext()) {
                Attribute att = (Attribute)list.next();
                int nameCode = pool.allocate(att.getNamespacePrefix(),
                                             att.getNamespaceURI(),
                                             att.getName());
                if (fingerprint == (nameCode & 0xfffff)) {
                    return att.getValue();
                }
            }
        }
        return null;
    }

    /**
    * Get the root node - always a document node with this tree implementation
    * @return the NodeInfo representing the containing document
    */

    public NodeInfo getRoot() {
        return docWrapper;
    }

    /**
    * Get the root (document) node
    * @return the DocumentInfo representing the containing document
    */

    public DocumentInfo getDocumentRoot() {
        return docWrapper;
    }

    /**
    * Determine whether the node has any children. <br />
    * Note: the result is equivalent to <br />
    * getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()
    */

    public boolean hasChildNodes() {
        switch (nodeKind) {
            case Type.DOCUMENT:
                return true;
            case Type.ELEMENT:
                return !((Element)node).content().isEmpty();
            default:
                return false;
        }
    }
    /**
    * Get a character string that uniquely identifies this node.
    * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
    * @return a string that uniquely identifies this node, across all
    * documents
    */

    public String generateId() {
        return Navigator.getSequentialKey(this);
    }

    /**
     * Get the document number of the document containing this node. For a free-standing
     * orphan node, just return the hashcode.
     */

    public int getDocumentNumber() {
        return parent.getDocumentNumber();
    }

    /**
    * Copy this node to a given outputter (deep copy)
    */

    public void copy(Receiver out, int whichNamespaces, boolean copyAnnotations) throws TransformerException {
        Navigator.copy(this, out, docWrapper.namePool, whichNamespaces, copyAnnotations);
    }

    /**
    * Output all namespace nodes associated with this element. Does nothing if
    * the node is not an element.
    * @param out The relevant outputter
    * @param includeAncestors True if namespaces declared on ancestor elements must
    * be output; false if it is known that these are already on the result tree
    */

    public void outputNamespaceNodes(Receiver out, boolean includeAncestors)
        throws TransformerException {
        if (nodeKind==Type.ELEMENT) {
            NamePool pool = docWrapper.getNamePool();
            AxisIterator enm = iterateAxis(Axis.NAMESPACE);
            while (true) {
                NodeWrapper wrapper = (NodeWrapper)enm.next();
                if (wrapper == null) {
                    break;
                }
                if (!includeAncestors && !wrapper.getParent().isSameNode(this)) {
                    break;
                }
                Namespace ns = (Namespace)wrapper.node;
                int nscode = pool.allocateNamespaceCode(
                                ns.getPrefix(),
                                ns.getURI());
                out.namespace(nscode, 0);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Axis enumeration classes
    ///////////////////////////////////////////////////////////////////////////////


    private final class AttributeEnumeration extends Navigator.BaseEnumeration {

        private Iterator atts;
        private int ix = 0;
        private NodeWrapper start;

        public AttributeEnumeration(NodeWrapper start) {
            this.start = start;
            atts = ((Element)start.node).attributes().iterator();
        }

        public void advance() {
            if (atts.hasNext()) {
                current = makeWrapper(atts.next(), docWrapper, start, ix++);
            } else {
                current = null;
            }
        }

        public SequenceIterator getAnother() {
            return new AttributeEnumeration(start);
        }

    }  // end of class AttributeEnumeration

    private final class NamespaceEnumeration extends Navigator.BaseEnumeration {

        private HashMap nslist = new HashMap();
        private Iterator prefixes;
        private int ix = 0;
        private NodeWrapper start;

        public NamespaceEnumeration(NodeWrapper start) {
            this.start = start;
            NodeWrapper curr = start;

            // build the complete list of namespaces

            do {
                Element elem = (Element)curr.node;
                Namespace ns = elem.getNamespace();
                String prefix = ns.getPrefix();
                String uri = ns.getURI();
                if (!(prefix.equals("") && uri.equals(""))) {
                    if (!nslist.containsKey(prefix)) {
                        nslist.put(ns.getPrefix(), ns);
                    }
                }
                List addl = elem.additionalNamespaces();
                if (addl.size() > 0) {
                    Iterator itr = addl.iterator();
                    while (itr.hasNext()) {
                        ns = (Namespace) itr.next();
                        if (!nslist.containsKey(ns.getPrefix())) {
                            nslist.put(ns.getPrefix(), ns);
                        }
                    }
                }
                curr = (NodeWrapper)curr.getParent();
            } while (curr.getNodeKind()==Type.ELEMENT);

            nslist.put("xml", Namespace.XML_NAMESPACE);
            prefixes = nslist.keySet().iterator();
            //advance();
        }

        public void advance() {
            if (prefixes.hasNext()) {
                String prefix = (String)prefixes.next();
                Namespace ns = (Namespace)nslist.get(prefix);
                current = makeWrapper(ns, docWrapper, start, ix++);
            } else {
                current = null;
            }
        }

        public SequenceIterator getAnother() {
            return new NamespaceEnumeration(start);
        }

        // NB: namespace nodes in the JDOM implementation do not support all
        // XPath functions, for example namespace nodes have no parent.


    }  // end of class NamespaceEnumeration


    /**
    * The class ChildEnumeration handles not only the child axis, but also the
    * following-sibling and preceding-sibling axes. It can also iterate the children
    * of the start node in reverse order, something that is needed to support the
    * preceding and preceding-or-ancestor axes (the latter being used by xsl:number)
    */

    private final class ChildEnumeration extends Navigator.BaseEnumeration {

        private NodeWrapper start;
        private NodeWrapper commonParent;
        private ListIterator children;
        private int ix = 0;
        private boolean downwards;  // iterate children of start node (not siblings)
        private boolean forwards;   // iterate in document order (not reverse order)

        public ChildEnumeration(NodeWrapper start,
                                boolean downwards, boolean forwards) {
            this.start = start;
            this.downwards = downwards;
            this.forwards = forwards;

            if (downwards) {
                commonParent = start;
            } else {
                commonParent = (NodeWrapper)start.getParent();
            }

            if (commonParent.getNodeKind()==Type.DOCUMENT) {
                children = ((Document)commonParent.node).content().listIterator();
            } else {
                children = ((Element)commonParent.node).content().listIterator();
            }

            if (downwards) {
                if (!forwards) {
                    // backwards enumeration: go to the end
                    while (children.hasNext()) {
                        children.next();
                        ix++;
                    }
                }
            } else {
                ix = start.getSiblingPosition();
                // find the start node among the list of siblings
                if (forwards) {
                    for (int i=0; i<=ix; i++) {
                        children.next();
                    }
                    ix++;
                } else {
                    for (int i=0; i<ix; i++) {
                        children.next();
                    }
                    ix--;
                }
            }
            //advance();
        }



        public void advance() {
            if (forwards) {
                if (children.hasNext()) {
                    Object nextChild = children.next();
                    if (nextChild instanceof DocumentType) {
                        advance();
                        return;
                    }
                    if (nextChild instanceof Entity) {
                        throw new IllegalStateException("Unexpanded entity in JDOM tree");
                    } else {
                        if (isAtomizing()) {
                            current = new UntypedAtomicValue(getStringValue(node));
                        } else {
                            current = makeWrapper(nextChild, docWrapper, commonParent, ix++);
                        }
                    }
                } else {
                    current = null;
                }
            } else {    // backwards
                if (children.hasPrevious()) {
                    Object nextChild = children.previous();
                    if (nextChild instanceof DocumentType) {
                        advance();
                        return;
                    }
                    if (nextChild instanceof Entity) {
                        throw new IllegalStateException("Unexpanded entity in JDOM tree");
                    } else {
                        if (isAtomizing()) {
                            current = new UntypedAtomicValue(getStringValue(node));
                        } else {
                            current = makeWrapper(nextChild, docWrapper, commonParent, ix--);
                        }
                    }
                } else {
                    current = null;
                }
            }
        }

        public SequenceIterator getAnother() {
            return new ChildEnumeration(start, downwards, forwards);
        }

    } // end of class ChildEnumeration


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
