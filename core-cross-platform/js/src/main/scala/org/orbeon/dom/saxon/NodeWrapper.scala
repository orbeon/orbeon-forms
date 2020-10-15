package org.orbeon.dom.saxon

import java.util.function.Predicate

import org.orbeon.saxon.model.{Type, UType}
import org.orbeon.saxon.om._
import org.orbeon.saxon.pattern.{AnyNodeTest, NodeTest}
import org.orbeon.saxon.tree.iter.{AxisIterator, LookaheadIterator}
import org.orbeon.saxon.tree.util.{FastStringBuffer, Navigator, SteppingNode}
import org.orbeon.saxon.tree.wrapper.{AbstractNodeWrapper, SiblingCountingNode}
import org.orbeon.dom._
import org.orbeon.oxf.util.CoreUtils._

/**
 * A node in the XML parse tree representing an XML element, character content, or attribute.
 * This is the implementation of the NodeInfo interface used as a wrapper for Orbeon DOM nodes.
 *
 * History: this started life as the NodeWrapper for JDOM nodes; it was then modified by the
 * Orbeon team to act as a wrapper for DOM4J nodes, and was shipped with the Orbeon product;
 * it has now been absorbed back into Saxon. And it is now back to its own life.
 *
 * And now it's again it's own thing for the Orbeon DOM. -Erik
 *
 *
 * @author Michael H. Kay
 * @author Orbeon
 */
private object NodeWrapper {

//  def makeWrapperImpl(node: Node, docWrapper: DocumentWrapper, parent: NodeWrapper): NodeWrapper =
//    node match {
//      case _: Document => docWrapper
//      case _           => new NodeWrapper(node, parent) |!> (_.treeInfo = docWrapper)
//    }
//
//  def getStringValue(node: Node): String =
//    node.getStringValue
}

/**
 * This constructor is protected: nodes should be created using the wrap
 * factory method on the DocumentWrapper class
 *
 * @param node   The node to be wrapped
 * @param parent The NodeWrapper that wraps the parent of this node
 */
class NodeWrapper protected (
  val node   : Node,
  var parent : NodeWrapper // null means unknown
) extends AbstractNodeWrapper
    with SiblingCountingNode
    with SteppingNode[NodeWrapper] {

//  import NodeWrapper._
//
////
////  protected var docWrapper: DocumentWrapper = null
////
////  /**
////   * Factory method to wrap a node with a wrapper that implements the Saxon
////   * NodeInfo interface.
////   *
////   * @param node       The node
////   * @param docWrapper The wrapper for the Document containing this node
////   * @return The new wrapper for the supplied node
////   */
////  protected def makeWrapper(node: Node, docWrapper: DocumentWrapper): NodeWrapper =
////    makeWrapper(node, docWrapper, null)
////
////  /**
////   * Factory method to wrap a node with a wrapper that implements the Saxon
////   * NodeInfo interface.
////   *
////   * @param node       The node
////   * @param docWrapper The wrapper for the Document containing this node
////   * @param parent     The wrapper for the parent of the node
////   * @return The new wrapper for the supplied node
////   */
////  protected def makeWrapper(
////    node       : Node,
////    docWrapper : DocumentWrapper,
////    parent     : NodeWrapper
////  ): NodeWrapper = NodeWrapper.makeWrapperImpl(node, docWrapper, parent)
////
////  def getNamePool: NamePool = docWrapper.getNamePool
////
////  def getTypedValue: SequenceIterator = SingletonIterator.makeIterator(atomize.asInstanceOf[AtomicValue])
////
////  def atomize: AtomicSequence =
////    node match {
////      case _: Comment | _: ProcessingInstruction => new StringValue(getStringValueCS)
////      case _                                     => new UntypedAtomicValue(getStringValueCS)
////    }
////
////  // UNTYPED or UNTYPED_ATOMIC
////  def getTypeAnnotation: Int =
////    node match {
////      case _: Attribute => StandardNames.XS_UNTYPED_ATOMIC
////      case _            => StandardNames.XS_UNTYPED
////    }
////
////  def getSystemId: String = docWrapper.baseURI
////
////  def setSystemId(uri: String): Unit =
////    docWrapper.baseURI = uri
////
////  // In this model, base URIs are held only an the document level. We don't currently take any account of xml:base attributes.
////  def getBaseURI: String = {
////
////    if (node.isInstanceOf[Namespace])
////      return null
////
////    var n = this
////    if (! node.isInstanceOf[Element])
////      n = n.getParent
////
////    // Look for an `xml:base` attribute
////    while (n ne null) {
////      val xmlbase = n.getAttributeValue(StandardNames.XML_BASE)
////      if (xmlbase ne null)
////        return xmlbase
////      n = n.getParent
////    }
////    // if not found, return the base URI of the document node
////    docWrapper.baseURI
////  }
////
////  def getLineNumber: Int = -1
////  def getColumnNumber: Int = -1
////  def getStringValue: String = NodeWrapper.getStringValue(node)
////
////  def getNameCode: Int = getNodeKind match {
////    case Type.ELEMENT | Type.ATTRIBUTE | Type.PROCESSING_INSTRUCTION | Type.NAMESPACE =>
////      docWrapper.getNamePool.allocate(getPrefix, getURI, getLocalPart)
////    case _ =>
////      -1
////  }
////
////  def getFingerprint: Int = getNameCode & 0xfffff
////
////
////
////  def iterateAxis(axisNumber: Byte): AxisIterator = iterateAxis(axisNumber, AnyNodeTest.getInstance)
////
////  def iterateAxis(axisNumber: Byte, nodeTest: NodeTest): AxisIterator = {
////    val nodeKind = getNodeKind
////    axisNumber match {
////      case AxisInfo.ANCESTOR =>
////        if (nodeKind == Type.DOCUMENT)
////          EmptyIterator.getInstance
////        else
////          new Navigator.AxisFilter(new Navigator.AncestorEnumeration(this, false), nodeTest)
////      case AxisInfo.ANCESTOR_OR_SELF =>
////        if (nodeKind == Type.DOCUMENT)
////          Navigator.filteredSingleton(this, nodeTest)
////        else
////          new Navigator.AxisFilter(new Navigator.AncestorEnumeration(this, true), nodeTest)
////      case AxisInfo.ATTRIBUTE =>
////        if (nodeKind != Type.ELEMENT)
////          EmptyIterator.getInstance
////        else
////          new Navigator.AxisFilter(new AttributeEnumeration(this), nodeTest)
////      case AxisInfo.CHILD =>
////        if (hasChildNodes)
////          new Navigator.AxisFilter(new ChildEnumeration(this, true, true), nodeTest)
////        else
////          EmptyIterator.getInstance
////      case AxisInfo.DESCENDANT =>
////        if (hasChildNodes)
////          new Navigator.AxisFilter(new Navigator.DescendantEnumeration(this, false, true), nodeTest)
////        else
////          EmptyIterator.getInstance
////      case AxisInfo.DESCENDANT_OR_SELF =>
////        new Navigator.AxisFilter(new Navigator.DescendantEnumeration(this, true, true), nodeTest)
////      case AxisInfo.FOLLOWING =>
////        new Navigator.AxisFilter(new Navigator.FollowingEnumeration(this), nodeTest)
////      case AxisInfo.FOLLOWING_SIBLING =>
////        nodeKind match {
////          case Type.DOCUMENT | Type.ATTRIBUTE | Type.NAMESPACE =>
////            EmptyIterator.getInstance
////          case _ =>
////            new Navigator.AxisFilter(new ChildEnumeration(this, false, true), nodeTest)
////        }
////      case AxisInfo.NAMESPACE =>
////        if (nodeKind != Type.ELEMENT)
////          EmptyIterator.getInstance
////        else
////          new Navigator.AxisFilter(new NamespaceEnumeration(this), nodeTest)
////      case AxisInfo.PARENT =>
////        Navigator.filteredSingleton(getParent, nodeTest)
////      case AxisInfo.PRECEDING =>
////        new Navigator.AxisFilter(new Navigator.PrecedingEnumeration(this, false), nodeTest)
////      case AxisInfo.PRECEDING_SIBLING =>
////        nodeKind match {
////          case Type.DOCUMENT | Type.ATTRIBUTE | Type.NAMESPACE =>
////            EmptyIterator.getInstance
////          case _ =>
////            new Navigator.AxisFilter(new ChildEnumeration(this, false, false), nodeTest)
////        }
////      case AxisInfo.SELF =>
////        Navigator.filteredSingleton(this, nodeTest)
////      case AxisInfo.PRECEDING_OR_ANCESTOR =>
////        new Navigator.AxisFilter(new Navigator.PrecedingEnumeration(this, true), nodeTest)
////      case _ =>
////        throw new IllegalArgumentException("Unknown axis number " + axisNumber)
////    }
////  }
////
////  def getAttributeValue(fingerprint: Int): String =
////    node match {
////      case elem: Element =>
////        val list = elem.jAttributes.iterator
////        val pool = docWrapper.getNamePool
////        while (list.hasNext) {
////          val att = list.next()
////          val nameCode = pool.allocate(att.getNamespacePrefix, att.getNamespaceURI, att.getName)
////          if (fingerprint == (nameCode & 0xfffff))
////            return att.getValue
////        }
////        null
////      case _ =>
////        null
////    }
////
////  def getRoot: NodeInfo = docWrapper
////  def getDocumentRoot: DocumentInfo = docWrapper
////
////  // NOTE: We used to call getParent.getDocumentNumber, but all other implementations use
////  // docWrapper.getDocumentNumber so we now harmonize with them.
////  // This also has another benefit: if a node gets detached from its parent, and getParent has not yet been
////  // cached, getParent can return null and getDocumentNumber fails. By using docWrapper.getDocumentNumber
////  // we avoid this issue, although arguably 1) a detached node should not point back to a DocumentWrapper and 2)
////  // one should not keep using a NodeInfo created to a node which is then detached.
////  def getDocumentNumber: Int =
////    docWrapper.getDocumentNumber
////
////  def copy(out: Receiver, whichNamespaces: Int, copyAnnotations: Boolean, locationId: Int): Unit =
////    Navigator.copy(this, out, docWrapper.getNamePool, whichNamespaces, copyAnnotations, locationId)
////
////  def isId = false
////  def isIdref = false
////  def isNilled = false
////
////  final private class AttributeEnumeration private[saxon](var start: NodeWrapper) extends Navigator.BaseEnumeration {
////
////    private val atts = start.node.asInstanceOf[Element].attributes.iterator
////
////    def advance(): Unit =
////      if (atts.hasNext)
////        current = makeWrapper(atts.next(), docWrapper, start)
////      else
////        current = null
////
////    def getAnother = new AttributeEnumeration(start)
////  }
////
////  final private class NamespaceEnumeration private[saxon](var start: NodeWrapper) extends Navigator.BaseEnumeration {
////
////    private val namespaceIterator = start.node.asInstanceOf[Element].allInScopeNamespacesAsNodes.valuesIterator
////
////    def advance(): Unit =
////      if (namespaceIterator.hasNext)
////        current = makeWrapper(namespaceIterator.next(), docWrapper, start)
////      else
////        current = null
////
////    def getAnother = new NamespaceEnumeration(start)
////
////    // NB: namespace nodes in the implementation do not support all
////    // XPath functions, for example namespace nodes have no parent.
////  }
////
//  /**
//   * The class ChildEnumeration handles not only the child axis, but also the
//   * following-sibling and preceding-sibling axes. It can also iterate the children
//   * of the start node in reverse order, something that is needed to support the
//   * preceding and preceding-or-ancestor axes (the latter being used by xsl:number)
//   */
//  private class ChildEnumeration(
//    val start     : NodeWrapper,
//    val downwards : Boolean, // iterate children of start node (not siblings)
//    val forwards  : Boolean  // iterate in document order (not reverse order)
//  ) extends AxisIterator
//      with LookaheadIterator {
//
//    private var ix = 0
//
//    private val commonParent =
//      if (downwards)
//        start
//      else
//        start.getParent
//
//    private val childrenIt = getAdjustedChildrenIt(commonParent)
//
//    if (downwards) {
//      if (! forwards) { // backwards enumeration: go to the end
//        while (childrenIt.hasNext) {
//          childrenIt.next()
//          ix += 1
//        }
//      }
//    } else {
//      ix = start.getSiblingPosition
//      // find the start node among the list of siblings
//      if (forwards) {
//        for (_ <- 0 to ix)
//          childrenIt.next()
//        ix += 1
//      } else {
//        for (_ <- 0 until ix)
//          childrenIt.next()
//        ix -= 1
//      }
//    }
//
//    def advance(): Unit =
//      if (forwards) {
//        if (childrenIt.hasNext) {
//          val nextChild = childrenIt.next()
//          nextChild match {
//            case _: Namespace =>
//              ix += 1 // increment anyway so that makeWrapper() passes the correct index)
//              advance()
//              return
//            case _ =>
//          }
//          current = makeWrapper(nextChild, docWrapper, commonParent)
//        } else
//          current = null
//      } else { // backwards
//        if (childrenIt.hasPrevious) {
//          val nextChild = childrenIt.previous
//          nextChild match {
//            case _: Namespace =>
//              ix -= 1 // decrement anyway so that makeWrapper() passes the correct index)
//              advance()
//              return
//            case _ =>
//          }
//          current = makeWrapper(nextChild, docWrapper, commonParent)
//        } else
//          current = null
//      }
//
//    def next(): NodeInfo = ???
//
//    def hasNext: Boolean = ???
//  }
////
////  def isSameNodeInfo(other: NodeInfo): Boolean =
////    other match {
////      case otherWrapper: NodeWrapper =>
////        node match {
////          case ns: Namespace =>
////            otherWrapper.node match {
////              case otherNamespace: Namespace =>
////                // `Namespace` doesn't have a parent, but when `Namespace` is wrapped within `NodeWrapper`
////                // a parent is set on the wrapper, so we can compare the parents' identity.
////                ns.prefix == otherNamespace.prefix && getParent.isSameNodeInfo(otherWrapper.getParent)
////              case _ => false
////            }
////          case _ =>
////            // This check that `this.node eq other.node`
////            node eq otherWrapper.node
////        }
////      case _ => false
////    }
////
////  def getConfiguration: Configuration = docWrapper.getConfiguration
//
//  override def getDeclaredNamespaces(buffer: Array[NamespaceBinding]): Array[NamespaceBinding] = node match {
//    case elem: Element =>
//      val namespaces = elem.allInScopeNamespacesAsNodes
//      if (namespaces.isEmpty) {
//        NamespaceBinding.EMPTY_ARRAY
//      } else {
//        val count = namespaces.size
//        val result =
//          if ((buffer eq null) || count > buffer.length)
//            new Array[NamespaceBinding](count)
//          else
//            buffer
//        var n = 0
//        val i = namespaces.iterator
//        while (i.hasNext) {
//          val namespace = i.next()
//          result(n) = new NamespaceBinding(namespace._1, namespace._2.uri)
//          n += 1
//        }
//        if (count < result.length)
//          result(count) = null
//        result
//      }
//    case _ => null
//  }
////
////  override def equals(other: Any): Boolean =
////    other match {
////      case node: NodeInfo => isSameNodeInfo(node)
////      case _ => false
////    }
////
////  override def hashCode: Int = node.hashCode
//
//
//
//  def iterateAttributes(nodeTest: Predicate[_ >: NodeInfo]): AxisIterator = ???
//
//  def iterateChildren(nodeTest: Predicate[_ >: NodeInfo]): AxisIterator = {
////    val elementOnly = isElementOnly(nodeTest)
//    var iter: AxisIterator = new Navigator.EmptyTextFilter(new ChildEnumeration(this, true, true))
//    if (nodeTest != AnyNodeTest.getInstance)
//      iter = new Navigator.AxisFilter(iter, nodeTest)
//    iter
//  }
//
//  def iterateSiblings(nodeTest: Predicate[_ >: NodeInfo], forwards: Boolean): AxisIterator = ???
//
//  /**
//   * Get the index position of this node among its siblings (starting from 0)
//   *
//   * @return 0 for the first child, 1 for the second child, etc.
//   */
//  def getSiblingPosition: Int =
//    getNodeKind match {
//      case Type.ELEMENT | Type.TEXT | Type.COMMENT | Type.PROCESSING_INSTRUCTION =>
//        val childrenIt = getAdjustedChildrenIt(getParent)
//        var ix = 0
//        while (childrenIt.hasNext) {
//          val n = childrenIt.next()
//          if (n eq node)
//            return ix
//          ix += 1
//        }
//        throw new IllegalStateException("DOM node not linked to parent node")
//      case Type.ATTRIBUTE =>
//        getSiblingPositionForIterator(getParent.iterateAxis(AxisInfo.ATTRIBUTE))
//      case Type.NAMESPACE =>
//        getSiblingPositionForIterator(getParent.iterateAxis(AxisInfo.NAMESPACE))
//      case Type.DOCUMENT =>
//        0
//      case _ =>
//        // Should probably not happen, right?
//        0
//    }
//
//  def getParent: NodeWrapper = {
//    if (parent eq null) {
//        node match {
//          case elem: Element =>
//            if (elem.isRootElement)
//              parent = makeWrapperImpl(node.getDocument, docWrapper)
//            else {
//              val parentNode = node.getParent
//              // This checks the case of an element detached from a Document
//              if (parentNode ne null)
//                parent = makeWrapperImpl(parentNode, docWrapper)
//            }
//          case _: Text => parent = makeWrapperImpl(node.getParent, docWrapper)
//          case _: Comment => parent = makeWrapperImpl(node.getParent, docWrapper)
//          case _: ProcessingInstruction => parent = makeWrapperImpl(node.getParent, docWrapper)
//          case _: Attribute => parent = makeWrapperImpl(node.getParent, docWrapper)
//          case _: Document => parent = null
//          case _: Namespace => throw new UnsupportedOperationException("Cannot find parent of a Namespace node")
//          case _ => throw new IllegalStateException
//        }
//    }
//    parent
//  }
//
//  def getNextSibling: NodeWrapper = ???
//
//  def getPreviousSibling: NodeWrapper = ???
//
//  def getFirstChild: NodeWrapper = ???
//
//  def getSuccessorElement(anchor: NodeWrapper, uri: String, local: String): NodeWrapper = ???
//
//  def getUnderlyingNode: AnyRef = node
//
//  // ORBEON: Should profile to see whether this is called often compared with the previous
//  // implementation where the node type was directly stored into each underlying node.
//  // Try to go from most frequently used to least
//  def getNodeKind: Int =
//    node match {
//      case _: Element               => Type.ELEMENT
//      case _: Attribute             => Type.ATTRIBUTE
//      case _: Text                  => Type.TEXT
//      case _: Document              => Type.DOCUMENT
//      case _: Comment               => Type.COMMENT
//      case _: ProcessingInstruction => Type.PROCESSING_INSTRUCTION
//      case _: Namespace             => Type.NAMESPACE
//      case _                        => throw new IllegalStateException
//    }
//
//  def compareOrder(other: NodeInfo): Int = Navigator.compareOrder(this, other.asInstanceOf[SiblingCountingNode])
//
//  def getLocalPart: String = node match {
//    case _: Element | _: Attribute          => node.getName
//    case _: Text | _: Comment | _: Document => ""
//    case n: ProcessingInstruction           => n.getTarget
//    case n: Namespace                       => n.prefix
//    case _                                  => null
//  }
//
//  def getURI: String = node match {
//    case elem: Element  => elem.getNamespaceURI
//    case att: Attribute => att.getNamespaceURI
//    case _              => ""
//  }
//
//  def getPrefix: String = node match {
//    case elem: Element  => elem.getNamespacePrefix
//    case att: Attribute => att.getNamespacePrefix
//    case _              => ""
//  }
//
//  def generateId(buffer: FastStringBuffer): Unit =
//    Navigator.appendSequentialKey(this, buffer, addDocNr = true)
//
//  def getStringValueCS: String = NodeWrapper.getStringValue(node)
//
//  override def hasChildNodes: Boolean =
//    node match {
//      case _: Document   => true
//      case elem: Element => elem.nodeIterator.exists(! _.isInstanceOf[Namespace])
//      case _             => false
//    }
//
//  override def getTreeInfo: DocumentWrapper = treeInfo.asInstanceOf[DocumentWrapper]
//
//  private def getAdjustedChildrenIt(p: NodeWrapper): java.util.ListIterator[Node] =
//    if (p.getNodeKind == Type.DOCUMENT) {
//      // This is an attempt to work around a DOM4J bug
//      // ORBEON: What bug was that? Can we remove this and fix the issue in org.orbeon.dom?
//      // 2020-08-31: Confirming that just removing this code causes tests to fail.
//      val document = p.node.asInstanceOf[Document]
//      val content = document.jContent
//      if (content.isEmpty && (document.getRootElement ne null))
//        java.util.Collections.singletonList(document.getRootElement: Node).listIterator
//      else
//        content.listIterator
//    } else {
//      p.node.asInstanceOf[Element].jContent.listIterator // content contains Namespace nodes (which is broken)!
//    }
//
//  private def getSiblingPositionForIterator(iter: AxisIterator): Int = {
//    var ix = 0
//    while (true) {
//      val n = iter.next()
//      if (n eq null)
//        throw new IllegalStateException("DOM node not linked to parent node")
//      if (n.isSameNodeInfo(this))
//        return ix
//      ix += 1
//    }
//    throw new IllegalStateException("DOM node not linked to parent node")
//  }
//
//  private def isElementOnly(nodeTest: Predicate[_ >: NodeInfo]): Boolean =
//    nodeTest.isInstanceOf[NodeTest] &&
//      nodeTest.asInstanceOf[NodeTest].getUType == UType.ELEMENT

  // TODO: Stubs
  def iterateAttributes(nodeTest: Predicate[_ >: NodeInfo]): AxisIterator = ???
  def iterateChildren(nodeTest: Predicate[_ >: NodeInfo]): AxisIterator = ???
  def iterateSiblings(nodeTest: Predicate[_ >: NodeInfo], forwards: Boolean): AxisIterator = ???
  def getSiblingPosition: Int = ???
  def getParent: NodeWrapper = ???
  def getNextSibling: NodeWrapper = ???
  def getPreviousSibling: NodeWrapper = ???
  def getFirstChild: NodeWrapper = ???
  def getSuccessorElement(anchor: NodeWrapper, uri: String, local: String): NodeWrapper = ???
  def getUnderlyingNode: AnyRef = ???
  def getNodeKind: Int = ???
  def compareOrder(other: NodeInfo): Int = ???
  def getLocalPart: String = ???
  def getURI: String = ???
  def getPrefix: String = ???
  def generateId(buffer: FastStringBuffer): Unit = ???
  def getStringValueCS: CharSequence = ???
}