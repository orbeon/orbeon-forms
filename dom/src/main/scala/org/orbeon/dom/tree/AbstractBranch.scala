package org.orbeon.dom.tree

import java.{lang => jl, util => ju}

import org.orbeon.dom._

abstract class AbstractBranch extends AbstractNode with Branch {

  protected def internalContent: ju.List[Node]

  override def getText: String = {
    val list = internalContent
    if (list ne null) {
      val size = list.size
      if (size >= 1) {
        val first = list.get(0)
        val firstText = getContentAsText(first)
        if (size == 1) {
          return firstText
        } else {
          val buffer = new jl.StringBuilder(firstText)
          for (i <- 1 until size) {
            val node = list.get(i)
            buffer.append(getContentAsText(node))
          }
          return buffer.toString
        }
      }
    }
    ""
  }

  // Return the text value of the Text node.
  private def getContentAsText(content: Node): String =
    content match {
      case _: Text => content.getText
      case _       => ""
    }

  // TODO: review as trimming is ok, but normalization should follow standard semantic, and method renamed if kept
  def getTextTrim: String = {
    val text = getText
    val textContent = new jl.StringBuilder
    val tokenizer = new ju.StringTokenizer(text)
    while (tokenizer.hasMoreTokens) {
      val str = tokenizer.nextToken()
      textContent.append(str)
      if (tokenizer.hasMoreTokens) {
        textContent.append(" ")
      }
    }
    textContent.toString
  }

  def addElement(name: String): Element = {
    val node = Element(name)
    add(node)
    node
  }

  def addElement(qname: QName): Element = {
    val node = Element(qname)
    add(node)
    node
  }

  def addElement(name: String, prefix: String, uri: String): Element = {
    val namespace = Namespace(prefix, uri)
    val qName = QName(name, namespace)
    addElement(qName)
  }

  def add(node: Node) = node match {
    case n: Element               => add(n)
    case n: Comment               => add(n)
    case n: ProcessingInstruction => add(n)
    case n                        => invalidNodeTypeException(n)
  }

  def remove(node: Node): Boolean = node match {
    case n: Element               => remove(n)
    case n: Comment               => remove(n)
    case n: ProcessingInstruction => remove(n)
    case n                        => invalidNodeTypeException(n)
  }

  def add(comment: Comment)             : Unit    = addNode(comment)
  def add(element: Element)             : Unit
  def add(pi: ProcessingInstruction)    : Unit    = addNode(pi)

  def remove(comment: Comment)          : Boolean = removeNode(comment)
  def remove(element: Element)          : Boolean
  def remove(pi: ProcessingInstruction) : Boolean = removeNode(pi)

  def appendContent(branch: Branch): Unit = {
    for (i <- 0 until branch.nodeCount) {
      val node = branch.node(i)
      add(node.deepCopy)
    }
  }

  def node(index: Int): Node =
    internalContent.get(index) match {
      case node: Node => node
      case _          => null
    }

  def nodeCount: Int = internalContent.size
  def jNodeIterator: ju.Iterator[Node] = internalContent.iterator

  protected def addNode(node: Node): Unit
  protected def addNode(index: Int, node: Node): Unit
  protected def removeNode(node: Node): Boolean

  /**
   * Called when a new child node has been added to me to allow any parent
   * relationships to be created or  events to be fired.
   */
  protected[dom] def childAdded(node: Node): Unit

  /**
   * Called when a child node has been removed to allow any parent
   * relationships to be deleted or events to be fired.
   */
  protected[dom] def childRemoved(node: Node): Unit

  private def invalidNodeTypeException(node: Node) =
    throw new IllegalAddException("Invalid node type. Cannot add node: " + node + " to this branch: " + this)
}
