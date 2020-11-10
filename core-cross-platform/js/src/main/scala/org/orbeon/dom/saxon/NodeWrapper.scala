package org.orbeon.dom.saxon

import java.util.function.Predicate
import java.{util => ju}

import org.orbeon.dom
import org.orbeon.saxon.model.{Type, UType}
import org.orbeon.saxon.om
import org.orbeon.saxon.om.AxisInfo
import org.orbeon.saxon.pattern.{AnyNodeTest, NameTest, NodeTest}
import org.orbeon.saxon.tree.iter.{AxisIterator, LookaheadIterator}
import org.orbeon.saxon.tree.util.{FastStringBuffer, Navigator, SteppingNode}
import org.orbeon.saxon.tree.wrapper.{AbstractNodeWrapper, SiblingCountingNode}


trait NodeWrapper
  extends AbstractNodeWrapper
     with SiblingCountingNode
     with SteppingNode[NodeWrapper] {

  val node       : dom.Node
  val docWrapper : DocumentWrapper
  var parent     : NodeWrapper // null means unknown

  // From `AbstractNodeWrapper`
  def getNextSibling: NodeWrapper =
    iterateSiblings(AnyNodeTest.getInstance, forwards = true).next().asInstanceOf[NodeWrapper]

  def getPreviousSibling: NodeWrapper =
    iterateSiblings(AnyNodeTest.getInstance, forwards = false).next().asInstanceOf[NodeWrapper]

  def getFirstChild: NodeWrapper =
    iterateChildren(AnyNodeTest.getInstance).next().asInstanceOf[NodeWrapper]

//  def getSuccessorElement2(anchor: NodeWrapper, uri: String, local: String): NodeWrapper = {
//
//    val stop = if (anchor eq null) null else anchor.node
//
//    var next = node
//
//    node.
//
//  }

  def getSuccessorElement(anchor: NodeWrapper, uri: String, local: String): NodeWrapper = {

    val stop = if (anchor eq null) null else anchor.node
    var next = node

    do next = getSuccessorNode(next, stop)
    while (
      (next ne null) &&
      ! (
        next.isInstanceOf[dom.Element]           &&
        (local == null || local == next.getName) &&
        (uri   == null || uri   == next.asInstanceOf[dom.Element].getNamespaceURI)
      )
    )

    if (next eq null)
      null
    else
      ConcreteNodeWrapper.makeWrapper(next, docWrapper, null)
  }

  private def getSuccessorNode(start: dom.Node, anchor: dom.Node): dom.Node = {

//    val firstChild = getFirstChild
//    if (firstChild ne null)
//      return firstChild
//
//    // Don't go past of the root of the subtree specifed by `anchor`
//    if ((anchor ne null) && (start eq anchor))
//      return null
//
//    var p: dom.Node = start
//    while (true) {
//      val s = p.getNextSibling
//      if (s ne null)
//        return s
//      p = p.getParent
//      if ((p eq null) || ((anchor ne null) && (start eq anchor))) {
//        return null
//      }
//    }
//    null
    ???
  }

  // From `VirtualNode`
  def getUnderlyingNode: AnyRef = node

  // New implementation

  // ORBEON: Should profile to see whether this is called often compared with the previous
  // implementation where the node type was directly stored into each underlying node.
  // Try to go from most frequently used to least
  def getNodeKind: Int =
    node match {
      case _: dom.Element               => Type.ELEMENT
      case _: dom.Attribute             => Type.ATTRIBUTE
      case _: dom.Text                  => Type.TEXT
      case _: dom.Document              => Type.DOCUMENT
      case _: dom.Comment               => Type.COMMENT
      case _: dom.ProcessingInstruction => Type.PROCESSING_INSTRUCTION
      case _: dom.Namespace             => Type.NAMESPACE
      case _                            => throw new IllegalStateException
    }

  override def equals(other: Any): Boolean =
    other match {
      case node: NodeWrapper => isSameNodeInfo(node)
      case _ => false
    }

  override def hashCode: Int = node.hashCode

  override def isSameNodeInfo(other: om.NodeInfo): Boolean =
    other match {
      case otherWrapper: NodeWrapper =>
        node match {
          case ns: dom.Namespace =>
            otherWrapper.node match {
              case otherNamespace: dom.Namespace =>
                // `Namespace` doesn't have a parent, but when `Namespace` is wrapped within `NodeWrapper`
                // a parent is set on the wrapper, so we can compare the parents' identity.
                ns.prefix == otherNamespace.prefix && getParent.isSameNodeInfo(otherWrapper.getParent)
              case _ => false
            }
          case _ =>
            node eq otherWrapper.node
        }
      case _ => false
    }

  def getLocalPart: String = node match {
    case _: dom.Element | _: dom.Attribute              => node.getName
    case _: dom.Text | _: dom.Comment | _: dom.Document => ""
    case n: dom.ProcessingInstruction                   => n.getTarget
    case n: dom.Namespace                               => n.prefix
    case _                                              => null
  }

  def getURI: String = node match {
    case elem: dom.Element  => elem.getNamespaceURI
    case att: dom.Attribute => att.getNamespaceURI
    case _                  => ""
  }

  def getPrefix: String = node match {
    case elem: dom.Element  => elem.getNamespacePrefix
    case att: dom.Attribute => att.getNamespacePrefix
    case _                  => ""
  }

  def getSiblingPosition: Int =
    getNodeKind match {
      case Type.ELEMENT | Type.TEXT | Type.COMMENT | Type.PROCESSING_INSTRUCTION =>
        val childrenIt = getAdjustedChildrenIt(getParent)
        var ix = 0
        while (childrenIt.hasNext) {
          val n = childrenIt.next()
          if (n eq node)
            return ix
          ix += 1
        }
        throw new IllegalStateException("DOM node not linked to parent node")
      case Type.ATTRIBUTE =>
        getSiblingPositionForIterator(getParent.iterateAxis(om.AxisInfo.ATTRIBUTE))
      case Type.NAMESPACE =>
        getSiblingPositionForIterator(getParent.iterateAxis(om.AxisInfo.NAMESPACE))
      case Type.DOCUMENT =>
        0
      case _ =>
        // Should probably not happen, right?
        0
    }

  override def getDisplayName: String = node match {
    case n: dom.Element                                  => n.getQualifiedName
    case n: dom.Attribute                                => n.getQualifiedName
    case _: dom.ProcessingInstruction | _: dom.Namespace => getLocalPart
    case _                                               => ""
  }

  def iterateAttributes(nodeTest: Predicate[_ >: om.NodeInfo]): AxisIterator = {
    var iter: AxisIterator = new AttributeEnumeration(this)
    if (nodeTest != AnyNodeTest.getInstance)
      iter = new Navigator.AxisFilter(iter, nodeTest)
    iter
  }

  def iterateChildren(nodeTest: Predicate[_ >: om.NodeInfo]): AxisIterator = {
    val elementOnly = isElementOnly(nodeTest)
    var iter: AxisIterator =
      new Navigator.EmptyTextFilter(new ChildEnumeration(this, downwards = true, forwards = true, elementsOnly = elementOnly))
    if (nodeTest != AnyNodeTest.getInstance)
      iter = new Navigator.AxisFilter(iter, nodeTest)
    iter
  }

  def iterateSiblings(nodeTest: Predicate[_ >: om.NodeInfo], forwards: Boolean): AxisIterator = {
    val elementOnly = isElementOnly(nodeTest)
    var iter: AxisIterator =
      new Navigator.EmptyTextFilter(new ChildEnumeration(this, downwards = false, forwards = forwards, elementsOnly = elementOnly))
    if (nodeTest != AnyNodeTest.getInstance)
      iter = new Navigator.AxisFilter(iter, nodeTest)
    iter
  }

  override def hasChildNodes: Boolean =
    node match {
      case _: dom.Document   => true
      case elem: dom.Element => elem.nodeIterator.exists(! _.isInstanceOf[dom.Namespace])
      case _                => false
    }

  private def isElementOnly(nodeTest: Predicate[_ >: om.NodeInfo]): Boolean =
    nodeTest.isInstanceOf[NodeTest] && nodeTest.asInstanceOf[NodeTest].getUType == UType.ELEMENT

  // Use generic way
  def compareOrder(other: om.NodeInfo): Int =
    Navigator.compareOrder(this, other.asInstanceOf[SiblingCountingNode])

  // NOTE: Could directly call `attributeValue` on element, but we need to pass the URI/localname.
  // So instead we do like in `DOMNodeWrapper`.
  override def getAttributeValue(uri: String, local: String): String = {
    val test = new NameTest(Type.ATTRIBUTE, uri, local, getNamePool)
    val iterator = iterateAxis(AxisInfo.ATTRIBUTE, test)
    val attribute = iterator.next()
    if (attribute == null)
      null
    else
      attribute.getStringValue
  }

  def generateId(buffer: FastStringBuffer): Unit =
    Navigator.appendSequentialKey(this, buffer, addDocNr = true)

  // ORBEON: This doesn't appear to be used by Saxon XPath
  override def getDeclaredNamespaces(buffer: Array[om.NamespaceBinding]): Array[om.NamespaceBinding] = ???

  override def getAllNamespaces: om.NamespaceMap =
    node match {
      case elem: dom.Element =>
        val namespaces = elem.allInScopeNamespacesAsStrings
        if (namespaces.isEmpty) {
          om.NamespaceMap.emptyMap
        } else {
          val namespaceMap = new om.NamespaceMap
          namespaces foreach {
            case (prefix, uri) => namespaceMap.put(prefix, uri)
          }
          namespaceMap
        }
      case _ => null
    }

  def getStringValueCS: CharSequence = node.getStringValue

  override def getRoot: om.NodeInfo = docWrapper

  final private class AttributeEnumeration private[saxon](var start: NodeWrapper)
    extends AxisIterator with LookaheadIterator {

    private val attsIt = start.node.asInstanceOf[dom.Element].attributeIterator

    def next(): om.NodeInfo =
      if (attsIt.hasNext)
        ConcreteNodeWrapper.makeWrapper(attsIt.next(), docWrapper, start)
      else
        null

    def hasNext: Boolean =
      attsIt.hasNext
  }

  /**
   * The class ChildEnumeration handles not only the child axis, but also the
   * following-sibling and preceding-sibling axes. It can also iterate the children
   * of the start node in reverse order, something that is needed to support the
   * preceding and preceding-or-ancestor axes (the latter being used by xsl:number)
   */
  private class ChildEnumeration(
    val start        : NodeWrapper,
    val downwards    : Boolean, // iterate children of start node (not siblings)
    val forwards     : Boolean, // iterate in document order (not reverse order)
    val elementsOnly : Boolean // xxx TODO filter?
  ) extends AxisIterator
      with LookaheadIterator {

    private val commonParent =
      if (downwards)
        start
      else
        start.getParent

    private val childrenIt = getAdjustedChildrenIt(commonParent)

    if (downwards) {
      if (! forwards) { // backwards enumeration: go to the end
        while (childrenIt.hasNext)
          childrenIt.next()
      }
    } else {
      val ix = start.getSiblingPosition
      // find the start node among the list of siblings
      if (forwards) {
        for (_ <- 0 to ix)
          childrenIt.next()
      } else {
        for (_ <- 0 until ix)
          childrenIt.next()
      }
    }

    private var current: om.NodeInfo = null

    // Initial advance
    advance()

    def advance(): Unit =
      if (forwards) {
        if (childrenIt.hasNext) {
          val nextChild = childrenIt.next()
          nextChild match {
            case _: dom.Namespace =>
              advance()
              return
            case _ =>
          }
          current = ConcreteNodeWrapper.makeWrapper(nextChild, docWrapper, commonParent)
        } else
          current = null
      } else { // backwards
        if (childrenIt.hasPrevious) {
          val nextChild = childrenIt.previous
          nextChild match {
            case _: dom.Namespace =>
              advance()
              return
            case _ =>
          }
          current = ConcreteNodeWrapper.makeWrapper(nextChild, docWrapper, commonParent)
        } else
          current = null
      }

    def next(): om.NodeInfo = {
      val r = current
      advance()
      r
    }

    def hasNext: Boolean =
      current ne null
  }

  def getParent: NodeWrapper = {
    if (parent eq null) {
        node match {
          case elem: dom.Element =>
            if (elem.isRootElement)
              parent = ConcreteNodeWrapper.makeWrapper(node.getDocument, docWrapper, null)
            else {
              val parentNode = node.getParent
              // This checks the case of an element detached from a Document
              if (parentNode ne null)
                parent = ConcreteNodeWrapper.makeWrapper(parentNode, docWrapper, null)
            }
          case _: dom.Text                  => parent = ConcreteNodeWrapper.makeWrapper(node.getParent, docWrapper, null)
          case _: dom.Comment               => parent = ConcreteNodeWrapper.makeWrapper(node.getParent, docWrapper, null)
          case _: dom.ProcessingInstruction => parent = ConcreteNodeWrapper.makeWrapper(node.getParent, docWrapper, null)
          case _: dom.Attribute             => parent = ConcreteNodeWrapper.makeWrapper(node.getParent, docWrapper, null)
          case _: dom.Document              => parent = null
          case _: dom.Namespace             => throw new UnsupportedOperationException("Cannot find parent of a Namespace node")
          case _ => throw new IllegalStateException
        }
    }
    parent
  }

  private def getAdjustedChildrenIt(p: NodeWrapper): ju.ListIterator[dom.Node] =
    if (p.getNodeKind == Type.DOCUMENT) {
      // This is an attempt to work around a DOM4J bug
      // ORBEON: What bug was that? Can we remove this and fix the issue in org.orbeon.dom?
      // 2020-08-31: Confirming that just removing this code causes tests to fail.
      val document = p.node.asInstanceOf[dom.Document]
      val content = document.jContent
      if (content.isEmpty && (document.getRootElement ne null))
        ju.Collections.singletonList(document.getRootElement: dom.Node).listIterator
      else
        content.listIterator
    } else {
      p.node.asInstanceOf[dom.Element].jContent.listIterator // content contains Namespace nodes (which is broken)!
    }

  private def getSiblingPositionForIterator(iter: AxisIterator): Int = {
    var ix = 0
    while (true) {
      val n = iter.next()
      if (n eq null)
        throw new IllegalStateException("DOM node not linked to parent node")
      if (n.isSameNodeInfo(this))
        return ix
      ix += 1
    }
    throw new IllegalStateException("DOM node not linked to parent node")
  }

////
////  // UNTYPED or UNTYPED_ATOMIC
////  def getTypeAnnotation: Int =
////    node match {
////      case _: Attribute => StandardNames.XS_UNTYPED_ATOMIC
////      case _            => StandardNames.XS_UNTYPED
////    }
////
////
}