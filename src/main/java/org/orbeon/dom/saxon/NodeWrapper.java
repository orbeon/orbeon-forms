package org.orbeon.dom.saxon;

import org.orbeon.dom.*;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.event.Receiver;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.pattern.AnyNodeTest;
import org.orbeon.saxon.pattern.NodeTest;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.value.UntypedAtomicValue;
import org.orbeon.saxon.value.Value;
import scala.Tuple2;
import scala.collection.Iterator;

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * A node in the XML parse tree representing an XML element, character content, or attribute.
 * This is the implementation of the NodeInfo interface used as a wrapper for Orbeon DOM nodes.
 *
 * History: this started life as the NodeWrapper for JDOM nodes; it was then modified by the
 * Orbeon team to act as a wrapper for DOM4J nodes, and was shipped with the Orbeon product;
 * it has now been absorbed back into Saxon. And it is now back to its own life
 *
 * @author Michael H. Kay
 * @author Orbeon
 */
public class NodeWrapper implements NodeInfo, VirtualNode, SiblingCountingNode {

    protected Node node;
    private NodeWrapper parent;     // null means unknown
    DocumentWrapper docWrapper;

    /**
     * This constructor is protected: nodes should be created using the wrap
     * factory method on the DocumentWrapper class
     *
     * @param node   The node to be wrapped
     * @param parent The NodeWrapper that wraps the parent of this node
     */
    protected NodeWrapper(Node node, NodeWrapper parent) {
        this.node = node;
        this.parent = parent;
    }

    /**
     * Factory method to wrap a node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The node
     * @param docWrapper The wrapper for the Document containing this node
     * @return The new wrapper for the supplied node
     */
    NodeWrapper makeWrapper(Node node, DocumentWrapper docWrapper) {
        return makeWrapper(node, docWrapper, null);
    }

    /**
     * Factory method to wrap a node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The node
     * @param docWrapper The wrapper for the Document containing this node
     * @param parent     The wrapper for the parent of the node
     * @return The new wrapper for the supplied node
     */
    protected NodeWrapper makeWrapper(Node node, DocumentWrapper docWrapper, NodeWrapper parent) {
        return makeWrapperImpl(node, docWrapper, parent);
    }

    static NodeWrapper makeWrapperImpl(Node node, DocumentWrapper docWrapper, NodeWrapper parent) {
        if (node instanceof Document) {
            return docWrapper;
        } else {
            final NodeWrapper wrapper = new NodeWrapper(node, parent);
            wrapper.docWrapper = docWrapper;
            return wrapper;
        }
    }

    public Object getUnderlyingNode() {
        return node;
    }

    public NamePool getNamePool() {
        return docWrapper.getNamePool();
    }

    public int getNodeKind() {
        // ORBEON: Should profile to see whether this is called often compared with the previous
        // implementation where the node type was directly stored into each underlying node.

        // Try to go from most frequently used to least
        if (node instanceof Element)
            return Type.ELEMENT;
        else if (node instanceof Attribute)
            return Type.ATTRIBUTE;
        else if (node instanceof Text)
            return Type.TEXT;
        else if (node instanceof Document)
            return Type.DOCUMENT;
        else if (node instanceof Comment)
            return Type.COMMENT;
        else if (node instanceof ProcessingInstruction)
            return Type.PROCESSING_INSTRUCTION;
        else if (node instanceof Namespace)
            return Type.NAMESPACE;
        else
            throw new IllegalStateException();
    }

    public SequenceIterator getTypedValue() throws XPathException {
        return SingletonIterator.makeIterator((AtomicValue) atomize());
    }

    public Value atomize() throws XPathException {
        if (node instanceof Comment || node instanceof ProcessingInstruction)
            return new StringValue(getStringValueCS());
        else
            return new UntypedAtomicValue(getStringValueCS());
    }

    // UNTYPED or UNTYPED_ATOMIC
    public int getTypeAnnotation() {
        if (node instanceof Attribute) {
            return StandardNames.XS_UNTYPED_ATOMIC;
        }
        return StandardNames.XS_UNTYPED;
    }

    public String getSystemId() {
        return docWrapper.baseURI;
    }
    public void setSystemId(String uri) {
        docWrapper.baseURI = uri;
    }

    // In this model, base URIs are held only an the document level. We don't currently take any account of xml:base attributes.
    public String getBaseURI() {
        if (node instanceof Namespace) {
            return null;
        }
        NodeInfo n = this;
        if (!(node instanceof Element)) {
            n = n.getParent();
        }
        // Look for an xml:base attribute
        while (n != null) {
            String xmlbase = n.getAttributeValue(StandardNames.XML_BASE);
            if (xmlbase != null) {
                return xmlbase;
            }
            n = n.getParent();
        }
        // if not found, return the base URI of the document node
        return docWrapper.baseURI;
    }

    public int getLineNumber() {
        return -1;
    }
    public int getColumnNumber() {
        return -1;
    }

    public int compareOrder(NodeInfo other) {
        return Navigator.compareOrder(this, (SiblingCountingNode) other);
    }

    public String getStringValue() {
        return getStringValue(node);
    }

    public CharSequence getStringValueCS() {
        return getStringValue(node);
    }

    private static String getStringValue(Node node) {
        return node.getStringValue();
    }

    public int getNameCode() {
        switch (getNodeKind()) {
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                return docWrapper.getNamePool().allocate(getPrefix(), getURI(), getLocalPart());
            default:
                return -1;
        }
    }

    public int getFingerprint() {
        return getNameCode() & 0xfffff;
    }

    public String getLocalPart() {
        switch (getNodeKind()) {
            case Type.ELEMENT:
                return node.getName();
            case Type.ATTRIBUTE:
                return node.getName();
            case Type.TEXT:
            case Type.COMMENT:
            case Type.DOCUMENT:
                return "";
            case Type.PROCESSING_INSTRUCTION:
                return ((ProcessingInstruction) node).getTarget();
            case Type.NAMESPACE:
                return ((Namespace) node).prefix();
            default:
                return null;
        }
    }

    public String getPrefix() {
        if (node instanceof Element)
            return ((Element) node).getNamespacePrefix();
        else if (node instanceof Attribute)
            return ((Attribute) node).getNamespacePrefix();
        else
            return "";
    }

    public String getURI() {
        if (node instanceof Element)
            return ((Element) node).getNamespaceURI();
        else if (node instanceof Attribute)
            return ((Attribute) node).getNamespaceURI();
        else
            return "";
    }

    public String getDisplayName() {
        switch (getNodeKind()) {
            case Type.ELEMENT:
                return ((Element) node).getQualifiedName();
            case Type.ATTRIBUTE:
                return ((Attribute) node).getQualifiedName();
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                return getLocalPart();
            default:
                return "";
        }
    }

    public NodeInfo getParent() {
        if (parent == null) {
            if (node instanceof Element) {
                if (((Element) node).isRootElement()) {
                    parent = makeWrapper(node.getDocument(), docWrapper);
                } else {
                    final Node parentNode = node.getParent();
                    // This checks the case of an element detached from a Document
                    if (parentNode != null)
                        parent = makeWrapper(parentNode, docWrapper);
                }
            } else if (node instanceof Text) {
                parent = makeWrapper(node.getParent(), docWrapper);
            } else if (node instanceof Comment) {
                parent = makeWrapper(node.getParent(), docWrapper);
            } else if (node instanceof ProcessingInstruction) {
                parent = makeWrapper(node.getParent(), docWrapper);
            } else if (node instanceof Attribute) {
                parent = makeWrapper(node.getParent(), docWrapper);
            } else if (node instanceof Document) {
                parent = null;
            } else if (node instanceof Namespace) {
                throw new UnsupportedOperationException("Cannot find parent of a Namespace node");
            } else {
                throw new IllegalStateException();
            }
        }
        return parent;
    }

    private int getSiblingPositionForIterator(AxisIterator iter) {
        int ix = 0;
        while (true) {
            NodeInfo n = (NodeInfo) iter.next();
            if (n == null) {
                break;
            }
            if (n.isSameNodeInfo(this)) {
                return ix;
            }
            ix++;
        }
        throw new IllegalStateException("DOM node not linked to parent node");
    }

    // Get the index position of this node among its siblings (starting from 0)
    public int getSiblingPosition() {
        final NodeWrapper parent = (NodeWrapper) getParent();
        switch (getNodeKind()) {
            case Type.ELEMENT:
            case Type.TEXT:
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION: {
                final List<Node> children;
                if (parent.getNodeKind() == Type.DOCUMENT) {
                    // This is an attempt to work around a DOM4J bug
                    // ORBEON: What bug was that? Can we remove this and fix the issue in org.orbeon.dom?
                    // TODO: This logic is also duplicated in this file.
                    final Document document = (Document) parent.node;
                    final List<Node> content = document.jContent();
                    if (content.size() == 0 && document.getRootElement() != null)
                        children = Collections.<Node>singletonList(document.getRootElement());
                    else
                        children = content;
                } else {
                    // Beware: content() contains Namespace nodes (which is broken)!
                    children = ((Element) parent.node).jContent();
                }
                int ix = 0;
                for (ListIterator iterator = children.listIterator(); iterator.hasNext(); ) {
                    final Object n = iterator.next();
                    if (n == node) {
                        return ix;
                    }
                    ix++;
                }
                throw new IllegalStateException("DOM node not linked to parent node");
            }
            case Type.ATTRIBUTE:
                return getSiblingPositionForIterator(parent.iterateAxis(Axis.ATTRIBUTE));
            case Type.NAMESPACE:
                return getSiblingPositionForIterator(parent.iterateAxis(Axis.NAMESPACE));
            case Type.DOCUMENT:
                return 0;
            default:
                // Should probably not happen, right?
                return 0;
        }
    }

    public AxisIterator iterateAxis(byte axisNumber) {
        return iterateAxis(axisNumber, AnyNodeTest.getInstance());
    }

    public AxisIterator iterateAxis(byte axisNumber, NodeTest nodeTest) {

        final int nodeKind = getNodeKind();

        switch (axisNumber) {
            case Axis.ANCESTOR:
                if (nodeKind == Type.DOCUMENT) {
                    return EmptyIterator.getInstance();
                }
                return new Navigator.AxisFilter(
                        new Navigator.AncestorEnumeration(this, false),
                        nodeTest);

            case Axis.ANCESTOR_OR_SELF:
                if (nodeKind == Type.DOCUMENT) {
                    return Navigator.filteredSingleton(this, nodeTest);
                }
                return new Navigator.AxisFilter(
                        new Navigator.AncestorEnumeration(this, true),
                        nodeTest);

            case Axis.ATTRIBUTE:
                if (nodeKind != Type.ELEMENT) return EmptyIterator.getInstance();
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
                if (nodeKind != Type.ELEMENT) {
                    return EmptyIterator.getInstance();
                }
                return new Navigator.AxisFilter(
                        new NamespaceEnumeration(this),
                        nodeTest);

            case Axis.PARENT:
                getParent();
                return Navigator.filteredSingleton(parent, nodeTest);

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
                return Navigator.filteredSingleton(this, nodeTest);

            case Axis.PRECEDING_OR_ANCESTOR:
                return new Navigator.AxisFilter(
                        new Navigator.PrecedingEnumeration(this, true),
                        nodeTest);

            default:
                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }

    public String getAttributeValue(int fingerprint) {
        if (node instanceof Element) {
            java.util.Iterator<Attribute> list = ((Element) node).jAttributes().iterator();
            NamePool pool = docWrapper.getNamePool();
            while (list.hasNext()) {
                Attribute att = (Attribute) list.next();
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

    public NodeInfo getRoot() {
        return docWrapper;
    }
    public DocumentInfo getDocumentRoot() {
        return docWrapper;
    }

    public boolean hasChildNodes() {
        if (node instanceof Document) {
            return true;
        } else if (node instanceof Element) {
            // Beware: content() contains Namespace nodes (which is broken)!
            List<Node> content = ((Element) node).jContent();
            for (int i = 0; i < content.size(); i++) {
                if (!(content.get(i) instanceof Namespace)) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
    }

    public int getDocumentNumber() {
        // NOTE: We used to call getParent().getDocumentNumber(), but all other implementations use
        // docWrapper.getDocumentNumber() so we now harmonize with them.

        // This also has another benefit: if a node gets detached from its parent, and getParent() has not yet been
        // cached, getParent() can return null and getDocumentNumber() fails. By using docWrapper.getDocumentNumber()
        // we avoid this issue, although arguably 1) a detached node should not point back to a DocumentWrapper and 2)
        // one should not keep using a NodeInfo created to a node which is then detached.
        return docWrapper.getDocumentNumber();
    }

    public void copy(Receiver out, int whichNamespaces, boolean copyAnnotations, int locationId) throws XPathException {
        Navigator.copy(this, out, docWrapper.getNamePool(), whichNamespaces, copyAnnotations, locationId);
    }

    public boolean isId() {
        return false;
    }
    public boolean isIdref() {
        return false;
    }
    public boolean isNilled() {
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Axis enumeration classes
    ///////////////////////////////////////////////////////////////////////////////

    private final class AttributeEnumeration extends Navigator.BaseEnumeration {

        private java.util.Iterator<Attribute> atts;
        private NodeWrapper start;

        AttributeEnumeration(NodeWrapper start) {
            this.start = start;
            atts = ((Element) start.node).jAttributes().iterator();
        }

        public void advance() {
            if (atts.hasNext()) {
                current = makeWrapper(atts.next(), docWrapper, start);
            } else {
                current = null;
            }
        }

        public SequenceIterator getAnother() {
            return new AttributeEnumeration(start);
        }

    }

    private final class NamespaceEnumeration extends Navigator.BaseEnumeration {

        private Iterator<Namespace> namespaceIterator;
        private NodeWrapper start;

        NamespaceEnumeration(NodeWrapper start) {
            this.start = start;
            namespaceIterator = ((Element) start.node).allInScopeNamespacesAsNodes().valuesIterator();
        }

        public void advance() {
            if (namespaceIterator.hasNext()) {
                current = makeWrapper(namespaceIterator.next(), docWrapper, start);
            } else {
                current = null;
            }
        }

        public SequenceIterator getAnother() {
            return new NamespaceEnumeration(start);
        }

        // NB: namespace nodes in the implementation do not support all
        // XPath functions, for example namespace nodes have no parent.
    }


    /**
     * The class ChildEnumeration handles not only the child axis, but also the
     * following-sibling and preceding-sibling axes. It can also iterate the children
     * of the start node in reverse order, something that is needed to support the
     * preceding and preceding-or-ancestor axes (the latter being used by xsl:number)
     */
    private final class ChildEnumeration extends Navigator.BaseEnumeration {

        private NodeWrapper start;
        private NodeWrapper commonParent;
        private ListIterator<Node> children;
        private int ix = 0;
        private boolean downwards;  // iterate children of start node (not siblings)
        private boolean forwards;   // iterate in document order (not reverse order)

        ChildEnumeration(NodeWrapper start, boolean downwards, boolean forwards) {
            this.start = start;
            this.downwards = downwards;
            this.forwards = forwards;

            if (downwards) {
                commonParent = start;
            } else {
                commonParent = (NodeWrapper) start.getParent();
            }

            if (commonParent.getNodeKind() == Type.DOCUMENT) {
                // This is an attempt to work around a DOM4J bug
                // ORBEON: What bug was that? Can we remove this and fix the issue in org.orbeon.dom?
                // TODO: This logic is also duplicated in this file.
                final Document document = (Document) commonParent.node;
                final List<Node> content = document.jContent();
                if (content.size() == 0 && document.getRootElement() != null)
                    children = Collections.<Node>singletonList(document.getRootElement()).listIterator();
                else
                    children = content.listIterator();
            } else {
                children = ((Element) commonParent.node).jContent().listIterator();
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
                    for (int i = 0; i <= ix; i++) {
                        children.next();
                    }
                    ix++;
                } else {
                    for (int i = 0; i < ix; i++) {
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
                    Node nextChild = children.next();
                    if (nextChild instanceof Namespace) {
                        ix++; // increment anyway so that makeWrapper() passes the correct index)
                        advance();
                        return;
                    }
//                        if (isAtomizing()) {
//                            current = new UntypedAtomicValue(getStringValue(node));
//                        } else {
                    current = makeWrapper(nextChild, docWrapper, commonParent);
//                        }
                } else {
                    current = null;
                }
            } else {    // backwards
                if (children.hasPrevious()) {
                    Node nextChild = children.previous();
                    if (nextChild instanceof Namespace) {
                        ix--; // decrement anyway so that makeWrapper() passes the correct index)
                        advance();
                        return;
                    }
//                        if (isAtomizing()) {
//                            current = new UntypedAtomicValue(getStringValue(node));
//                        } else {
                    current = makeWrapper(nextChild, docWrapper, commonParent);
//                        }
                } else {
                    current = null;
                }
            }
        }

        public SequenceIterator getAnother() {
            return new ChildEnumeration(start, downwards, forwards);
        }

    }

    public boolean isSameNodeInfo(NodeInfo other) {
        if (other instanceof NodeWrapper) {
            final NodeWrapper otherWrapper = (NodeWrapper) other;
            if (node instanceof Namespace) {
                final Namespace thisNamespace = (Namespace) node;
                if (otherWrapper.node instanceof Namespace) {
                    final Namespace otherNamespace = (Namespace) otherWrapper.node;
                    // `Namespace` doesn't have a parent, but when `Namespace` is wrapped within `NodeWrapper`
                    // a parent is set on the wrapper, so we can compare the parents' identity.
                    return thisNamespace.prefix().equals(otherNamespace.prefix()) && getParent().isSameNodeInfo(otherWrapper.getParent());
                } else {
                    return false;
                }
            } else {
                // This check that `this.node eq other.node`
                return node == ((NodeWrapper) other).node;
            }
        } else {
            return false;
        }
    }

    public Configuration getConfiguration() {
        return docWrapper.getConfiguration();
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of integers representing the namespace declarations and undeclarations present on
     * this element. For a node other than an element, return null. Otherwise, the returned array is a
     * sequence of namespace codes, whose meaning may be interpreted by reference to the name pool. The
     * top half word of each namespace code represents the prefix, the bottom half represents the URI.
     * If the bottom half is zero, then this is a namespace undeclaration rather than a declaration.
     * The XML namespace is never included in the list. If the supplied array is larger than required,
     * then the first unused entry will be set to -1.
     *
     * For a node other than an element, the method returns null.
     */
    public int[] getDeclaredNamespaces(int[] buffer) {
        if (node instanceof Element) {
            final Element elem = (Element) node;
            final scala.collection.immutable.Map<String, Namespace> namespaces = elem.allInScopeNamespacesAsNodes();
            if (namespaces.isEmpty()) {
                return EMPTY_NAMESPACE_LIST;
            } else {
                final int count = namespaces.size();

                int[] result = (buffer == null || count > buffer.length ? new int[count] : buffer);
                NamePool pool = getNamePool();
                int n = 0;
                for (Iterator<Tuple2<String, Namespace>> i = namespaces.iterator(); i.hasNext(); ) {
                    final Tuple2<String, Namespace> namespace = i.next();
                    result[n++] = pool.allocateNamespaceCode(namespace._1, namespace._2.uri());
                }
                if (count < result.length) {
                    result[count] = -1;
                }
                return result;

            }
        } else {
            return null;
        }
    }

    public boolean equals(Object other) {
        if (other instanceof NodeInfo) {
            return isSameNodeInfo((NodeInfo) other);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return node.hashCode();
    }
}
