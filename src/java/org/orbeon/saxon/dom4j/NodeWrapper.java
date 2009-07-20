package org.orbeon.saxon.dom4j;

import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.event.Receiver;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.pattern.AnyNodeTest;
import org.orbeon.saxon.pattern.NodeTest;
import org.orbeon.saxon.style.StandardNames;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.UntypedAtomicValue;
import org.orbeon.saxon.value.Value;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.value.AtomicValue;
import org.dom4j.*;

import java.util.*;
import java.util.ListIterator;

/**
  * A node in the XML parse tree representing an XML element, character content, or attribute.<P>
  * This is the implementation of the NodeInfo interface used as a wrapper for DOM4J nodes.
  * @author Michael H. Kay
  */

  // History: this started life as the NodeWrapper for JDOM nodes; it was then modified by the
  // Orbeon team to act as a wrapper for DOM4J nodes, and was shipped with the Orbeon product;
  // it has now been absorbed back into Saxon.

public class NodeWrapper implements NodeInfo, VirtualNode, SiblingCountingNode {

    protected Object node;
    protected short nodeKind;
    private NodeWrapper parent;     // null means unknown
    protected DocumentWrapper docWrapper;
    // Beware: with dom4j, this is an index over the result of content(), which may contain Namespace nodes
    protected int index;            // -1 means unknown

    /**
     * This constructor is protected: nodes should be created using the wrap
     * factory method on the DocumentWrapper class
     * @param node    The DOM4J node to be wrapped
     * @param parent  The NodeWrapper that wraps the parent of this node
     * @param index   Position of this node among its siblings
     */
    protected NodeWrapper(Object node, NodeWrapper parent, int index) {
        this.node = node;
        this.parent = parent;
        this.index = index;
    }

    /**
     * Factory method to wrap a DOM4J node with a wrapper that implements the Saxon
     * NodeInfo interface.
     * @param node        The DOM4J node
     * @param docWrapper  The wrapper for the Document containing this node
     * @return            The new wrapper for the supplied node
     */
    protected NodeWrapper makeWrapper(Object node, DocumentWrapper docWrapper) {
        return makeWrapper(node, docWrapper, null, -1);
    }

    /**
     * Factory method to wrap a DOM4J node with a wrapper that implements the Saxon
     * NodeInfo interface.
     * @param node        The DOM4J node
     * @param docWrapper  The wrapper for the Document containing this node
     * @param parent      The wrapper for the parent of the DOM4J node
     * @param index       The position of this node relative to its siblings
     * @return            The new wrapper for the supplied node
     */

    protected NodeWrapper makeWrapper(Object node, DocumentWrapper docWrapper,
                                      NodeWrapper parent, int index) {

        NodeWrapper wrapper;
        final Node dom4jNode = (Node) node;
        switch (dom4jNode.getNodeType()) {
            case Type.DOCUMENT:
                return docWrapper;
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
            case Type.TEXT:
                wrapper = new NodeWrapper(node, parent, index);
                wrapper.nodeKind = dom4jNode.getNodeType();
                break;
            case 4: // dom4j CDATA
                wrapper = new NodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.TEXT;
                break;
            default :
               throw new IllegalArgumentException("Bad node type in dom4j: " + node.getClass() + " instance " + node.toString());
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
    public SequenceIterator getTypedValue() throws XPathException {
        return SingletonIterator.makeIterator((AtomicValue)atomize());
    }

    public Value atomize() throws XPathException {
        switch (getNodeKind()) {
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                return new StringValue(getStringValueCS());
            default:
                return new UntypedAtomicValue(getStringValueCS());
        }
    }

    /**
    * Get the type annotation
    * @return UNTYPED or UNTYPED_ATOMIC
    */
    public int getTypeAnnotation() {
        if (getNodeKind() == Type.ATTRIBUTE) {
            return StandardNames.XDT_UNTYPED_ATOMIC;
        }
        return StandardNames.XDT_UNTYPED;
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
     * in the node. In the DOM4J model, base URIs are held only an the document level. We don't
     * currently take any account of xml:base attributes.
    */

    public String getBaseURI() {
        if (getNodeKind() == Type.NAMESPACE) {
            return null;
        }
        NodeInfo n = this;
        if (getNodeKind() != Type.ELEMENT) {
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

    public CharSequence getStringValueCS() {
        return getStringValue(node);
    }

    private static String getStringValue(Object node) {
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
                return docWrapper.getNamePool().allocate(getPrefix(),
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
                    final Node parentNode = ((Element)node).getParent();
                    // This checks the case of an element detached from a Document
                    if (parentNode != null)
                        parent = makeWrapper(parentNode, docWrapper);
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
                throw new UnsupportedOperationException("Cannot find parent of DOM4J namespace node");
            } else {
                throw new IllegalStateException("Unknown DOM4J node type " + node.getClass());
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
                    {
                        final NodeWrapper parent = (NodeWrapper) getParent();
                        final List children;
                        if (parent.getNodeKind()==Type.DOCUMENT) {
                            // This is an attempt to work around a dom4j bug
                            final Document document = (Document) parent.node;
                            final List content = document.content();
                            if (content.size() == 0 && document.getRootElement() != null)
                                children = Collections.singletonList(document.getRootElement());
                            else
                                children = content;
                        } else {
                            // Beware: dom4j content() contains Namespace nodes (which is broken)!
                            children = ((Element) parent.node).content();
                        }
                        for (ListIterator iterator = children.listIterator(); iterator.hasNext();) {
                            final Object n = iterator.next();
                            if (n == node) {
                                index = ix;
                                return index;
                            }
                            ix++;
                        }
                        throw new IllegalStateException("DOM4J node not linked to parent node");
                    }
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
                if (n.isSameNodeInfo(this)) {
                    index = ix;
                    return index;
                }
                ix++;
            }
            throw new IllegalStateException("DOM4J node not linked to parent node");
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
                if (nodeKind==Type.DOCUMENT) {
                    return EmptyIterator.getInstance();
                }
                return new Navigator.AxisFilter(
                            new Navigator.AncestorEnumeration(this, false),
                            nodeTest);

            case Axis.ANCESTOR_OR_SELF:
                if (nodeKind==Type.DOCUMENT) {
                    return Navigator.filteredSingleton(this, nodeTest);
                }
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
                // Beware: dom4j content() contains Namespace nodes (which is broken)!
                List content = ((Element)node).content();
                for (int i=0; i<content.size(); i++) {
                    if (!(content.get(i) instanceof Namespace)) {
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }
    /**
    * Get a character string that uniquely identifies this node.
    * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
    * @param buffer a Buffer to contain a string that uniquely identifies this node, across all
    * documents
     */

    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
        //buffer.append(Navigator.getSequentialKey(this));
    }

    /**
     * Get the document number of the document containing this node. For a free-standing
     * orphan node, just return the hashcode.
     */

    public int getDocumentNumber() {
        return getParent().getDocumentNumber();
    }

    /**
    * Copy this node to a given outputter (deep copy)
    */

    public void copy(Receiver out, int whichNamespaces, boolean copyAnnotations, int locationId) throws XPathException {
        Navigator.copy(this, out, docWrapper.getNamePool(), whichNamespaces, copyAnnotations, locationId);
    }

    /**
    * Output all namespace nodes associated with this element. Does nothing if
    * the node is not an element.
    * @param out The relevant outputter
    * @param includeAncestors True if namespaces declared on ancestor elements must
    * be output; false if it is known that these are already on the result tree
    */

    public void outputNamespaceNodes(Receiver out, boolean includeAncestors)
        throws XPathException {
        if (nodeKind==Type.ELEMENT) {
            NamePool pool = docWrapper.getNamePool();
            AxisIterator enm = iterateAxis(Axis.NAMESPACE);
            while (true) {
                NodeWrapper wrapper = (NodeWrapper)enm.next();
                if (wrapper == null) {
                    break;
                }
                if (!includeAncestors && !wrapper.getParent().isSameNodeInfo(this)) {
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
            } while (curr != null && curr.getNodeKind()==Type.ELEMENT);// NOTE: support elements detached from document

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

        // NB: namespace nodes in the DOM4J implementation do not support all
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
                // This is an attempt to work around a dom4j bug
                final Document document = (Document)commonParent.node;
                final List content = document.content();
                if (content.size() == 0 && document.getRootElement() != null)
                    children = Collections.singletonList(document.getRootElement()).listIterator();
                else
                    children = content.listIterator();
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
                    if (nextChild instanceof DocumentType || nextChild instanceof Namespace) {
                        ix++; // increment anyway so that makeWrapper() passes the correct index)
                        advance();
                        return;
                    }
                    if (nextChild instanceof Entity) {
                        throw new IllegalStateException("Unexpanded entity in DOM4J tree");
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
                    if (nextChild instanceof DocumentType || nextChild instanceof Namespace) {
                        ix--; // decrement anyway so that makeWrapper() passes the correct index)
                        advance();
                        return;
                    }
                    if (nextChild instanceof Entity) {
                        throw new IllegalStateException("Unexpanded entity in DOM4J tree");
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



    /**
     * Determine whether this is the same node as another node.
     * Note: a.isSameNodeInfo(b) if and only if generateId(a)==generateId(b).
     * This method has the same semantics as isSameNode() in DOM Level 3, but
     * works on Saxon NodeInfo objects rather than DOM Node objects.
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     *         the same node in the tree.
     */

    public boolean isSameNodeInfo(NodeInfo other) {
        if (!(other instanceof NodeWrapper)) {
            return false;
        }
        NodeWrapper ow = (NodeWrapper)other;
        if (node instanceof Namespace) {
            return this.getLocalPart().equals(ow.getLocalPart()) && this.getParent().isSameNodeInfo(ow.getParent());
        }
        return node.equals(ow.node);
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
     *         this element. For a node other than an element, return null. Otherwise, the returned array is a
     *         sequence of namespace codes, whose meaning may be interpreted by reference to the name pool. The
     *         top half word of each namespace code represents the prefix, the bottom half represents the URI.
     *         If the bottom half is zero, then this is a namespace undeclaration rather than a declaration.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to -1.
     *         <p/>
     *         <p>For a node other than an element, the method returns null.</p>
     */
    public int[] getDeclaredNamespaces(int[] buffer) {
        if (node instanceof Element) {
            final Element elem = (Element) node;
            final List namespaces = elem.declaredNamespaces();

            if (namespaces == null || namespaces.size() == 0) {
                return EMPTY_NAMESPACE_LIST;
            }
            final int count = namespaces.size();
            if (count == 0) {
                return EMPTY_NAMESPACE_LIST;
            } else {
                int[] result = (count > buffer.length ? new int[count] : buffer);
                NamePool pool = getNamePool();
                int n = 0;
                for (Iterator i = namespaces.iterator(); i.hasNext();) {
                    final Namespace namespace = (Namespace) i.next();
                    final String prefix = namespace.getPrefix();
                    final String uri = namespace.getURI();

                    result[n++] = pool.allocateNamespaceCode(prefix, uri);
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

    /**
    * Output all namespace nodes associated with this element. Does nothing if
    * the node is not an element.
    * @param out The relevant outputter
     * @param includeAncestors True if namespaces declared on ancestor elements must
     */
    public void sendNamespaceDeclarations(Receiver out, boolean includeAncestors) throws XPathException {
        Navigator.sendNamespaceDeclarations(this, out, includeAncestors);
    }

    /**
      * The equals() method compares nodes for identity. It is defined to give the same result
      * as isSameNodeInfo().
      * @param other the node to be compared with this node
      * @return true if this NodeInfo object and the supplied NodeInfo object represent
      *      the same node in the tree.
      * @since 8.7 Previously, the effect of the equals() method was not defined. Callers
      * should therefore be aware that third party implementations of the NodeInfo interface may
      * not implement the correct semantics. It is safer to use isSameNodeInfo() for this reason.
      * The equals() method has been defined because it is useful in contexts such as a Java Set or HashMap.
      */

     public boolean equals(Object other) {
        if (other instanceof NodeInfo) {
            return isSameNodeInfo((NodeInfo)other);
        } else {
            return false;
}
    }

    /**
      * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
      * (represent the same node) then they must have the same hashCode()
      * @since 8.7 Previously, the effect of the equals() and hashCode() methods was not defined. Callers
      * should therefore be aware that third party implementations of the NodeInfo interface may
      * not implement the correct semantics.
      */

     public int hashCode() {
         return node.hashCode();
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
