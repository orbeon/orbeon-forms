package org.orbeon.dom

import java.{util => ju}

import org.orbeon.dom.tree.AbstractBranch

object Document {

  def apply(): Document =
    new Document

  def apply(rootElementName: String): Document =
    apply(Element(QName(rootElementName)))

  def apply(rootElement: Element): Document = {
    val newDoc = new Document
    newDoc.setRootElement(rootElement)
    newDoc
  }
}

class Document extends AbstractBranch {

  private var _systemId: Option[String] = None

  def getType: Int = 9
  def systemIdOpt: Option[String] = _systemId
  def systemId: String = _systemId.orNull
  def systemId_=(name: String): Unit = _systemId = Option(name)

  override def getDocument: Document = this

  private var _rootElement: Element = _
  def rootElementOpt: Option[Element] = Option(_rootElement)
  def getRootElement: Element = _rootElement

  private var _internalContent: ju.List[Node] = _
  protected def internalContent: ju.List[Node] = {
    if (_internalContent eq null) {
      _internalContent = new ju.ArrayList[Node](1)
      if (_rootElement ne null)
        _internalContent.add(_rootElement)
    }
    _internalContent
  }

  def jContent: ju.List[Node] = internalContent

  override def deepCopy: Node = {
    val document = super.deepCopy.asInstanceOf[Document]
    document._rootElement = null
    document._internalContent = null
    document.appendContent(this)
    document
  }

  def clearContent(): Unit = {
    contentRemoved()
    _internalContent = null
    _rootElement = null
  }

  private def contentRemoved(): Unit =
    for (i <- 0 until internalContent.size) {
      internalContent.get(i) match {
        case node: Node => childRemoved(node)
        case _ =>
      }
    }

  protected def addNode(node: Node): Unit = {
    if (node ne null) {
      val document = node.getDocument
      if ((document ne null) && (document ne this)) {
        throw new IllegalAddException(
          this,
          node,
          s"The Node already has an existing document: $document"
        )
      }
      jContent.add(node)
      childAdded(node)
    }
  }

  protected def addNode(index: Int, node: Node): Unit = {
    if (node ne null) {
      val document = node.getDocument
      if ((document ne null) && (document ne this)) {
        throw new IllegalAddException(
          this,
          node,
          s"The Node already has an existing document: $document"
        )
      }
      jContent.add(index, node)
      childAdded(node)
    }
  }

  protected def removeNode(node: Node): Boolean = {
    if (node == _rootElement) {
      _rootElement = null
    }
    if (jContent.remove(node)) {
      childRemoved(node)
      return true
    }
    false
  }

  override def getStringValue: String =
    rootElementOpt map (_.getStringValue) getOrElse ""

  def accept(visitor: Visitor): Unit = {
    visitor.visit(this)
    content.iterator foreach (_.accept(visitor))
  }

  def normalize(): Unit =
    rootElementOpt foreach
      (_.normalize())

  def setRootElement(rootElement: Element): Unit = {
    // TODO ORBEON review: what if we have text and comment nodes at the top?
    clearContent()
    if (rootElement ne null) {
      add(rootElement)
      rootElementAdded(rootElement)
    }
  }

  def add(element: Element): Unit = {
    checkAddElementAllowed(element)
    addNode(element)
    rootElementAdded(element)
  }

  private def rootElementAdded(element: Element): Unit = {
    _rootElement = element
    element.setDocument(this)
  }

  def remove(element: Element): Boolean = {
    val answer = removeNode(element)
    val root = getRootElement
    if ((root ne null) && answer) {
      setRootElement(null)
    }
    element.setDocument(null)
    answer
  }

  protected[dom] def childAdded(node: Node): Unit =
    if (node ne null)
      node.setDocument(this)

  protected[dom] def childRemoved(node: Node): Unit =
    if (node ne null)
      node.setDocument(null)

  protected def checkAddElementAllowed(element: Element): Unit = {
    val root = getRootElement
    if (root ne null) {
      throw new IllegalAddException(
        this,
        element,
         s"Cannot add another element to this Document as it already has a root element of: ${root.getQualifiedName}"
      )
    }
  }

  override def toString: String =
    """Document()"""
}
