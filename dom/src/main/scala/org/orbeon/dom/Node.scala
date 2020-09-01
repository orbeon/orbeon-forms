package org.orbeon.dom

import org.orbeon.dom.io.{OutputFormat, XMLWriter}
import org.orbeon.dom.tree.{AbstractNode, WithParent}
import org.orbeon.io.{IOUtils, StringBuilderWriter}

import scala.jdk.CollectionConverters._

import java.{util => ju, lang => jl}

object Node {

  implicit class NodeOps[N <: Node](private val n: N) extends AnyVal {

    def serializeToString(format: OutputFormat = XMLWriter.DefaultFormat): String =
      IOUtils.useAndClose(new StringBuilderWriter) { writer =>
        new XMLWriter(writer, format).write(n)
        writer.result
      }

    /**
      * Go over the `Node` and its children and make sure that there are no two contiguous text nodes so as to ensure that
      * XPath expressions run correctly. As per XPath 1.0 (http://www.w3.org/TR/xpath):
      *
      * "As much character data as possible is grouped into each text node: a text node never has an immediately
      * following or preceding sibling that is a text node."
      */
    def normalizeTextNodes: N = {

      val nodesToDetach = new ju.ArrayList[Node]

      n.accept(
        new VisitorSupport {
          override def visit(elem: Element): Unit = {
            var previousNode: Node = null
            var sb: jl.StringBuilder = null
            for (currentNode <- elem.nodeIterator) {
              if (previousNode ne null) {
                previousNode match {
                  case previousNodeText: Text if currentNode.isInstanceOf[Text] =>
                    if (sb eq null)
                      sb = new jl.StringBuilder(previousNodeText.getText)
                    sb.append(currentNode.getText)
                    nodesToDetach.add(currentNode)
                  case _: Text =>
                    // Update node if needed
                    if (sb ne null)
                      previousNode.setText(sb.toString)
                    previousNode = currentNode
                    sb = null
                  case _ =>
                    previousNode = currentNode
                    sb = null
                }
              } else {
                previousNode = currentNode
                sb = null
              }
            }
            if ((previousNode ne null) && (sb ne null))
              previousNode.setText(sb.toString)
          }
        }
      )

      // Detach nodes only in the end so as to not confuse the acceptor above
      for (currentNode <- nodesToDetach.asScala)
        currentNode.detach()

      n
    }
  }

  def nodeTypeName(node: Node): String = node match {
    case _: Element               => "Element"
    case _: Attribute             => "Attribute"
    case _: Text                  => "Text"
    case _: Document              => "Document"
    case _: Comment               => "Comment"
    case _: ProcessingInstruction => "ProcessingInstruction"
    case _: Namespace             => "Namespace"
    case _                        => throw new IllegalStateException
  }
}

trait Node extends Cloneable {

  def getParent: Element
  def parentElemOpt: Option[Element] = Option(getParent)
  def setParent(parent: Element): Unit

  def getDocument: Document
  def setDocument(document: Document): Unit

  def documentOpt: Option[Document] = Option(getDocument)

  def getName: String
  def getText: String
  def setText(text: String): Unit

  def getStringValue: String

  def detach(): Node

  def accept(visitor: Visitor): Unit

  // TODO: Move this to a separate object, like `Node.deepCopy()` and use pattern matching.
  // Maybe check this: https://tpolecat.github.io/2015/04/29/f-bounds.html
  def deepCopy: Node
  def createCopy: Node
}

object Text {
  def apply(text: String): Text = new Text(text ensuring (_ ne null))
}

class Text(var text: String) extends AbstractNode with WithParent {

  override def getText: String = text
  override def setText(text: String): Unit = this.text = text

  def accept(visitor: Visitor): Unit = visitor.visit(this)

  override def toString = s"""Text("$text")"""
}

object Comment {
  def apply(text: String): Comment = new Comment(text ensuring (_ ne null))
}

class Comment(var text: String) extends AbstractNode with WithParent {

  override def getText: String = text
  override def setText(text: String): Unit = this.text = text

  def accept(visitor: Visitor): Unit = visitor.visit(this)

  override def toString = s"""Comment("$text")"""
}

object ProcessingInstruction {
  def apply(target: String, data: String): ProcessingInstruction =
    new ProcessingInstruction(target, data)
}

class ProcessingInstruction(var target: String, var text: String)
  extends AbstractNode with WithParent {

  override def getName: String = getTarget

  def getTarget: String = target
  def setTarget(target: String): Unit = this.target = target

  override def getText: String = text
  override def setText(text: String): Unit = this.text = text

  def accept(visitor: Visitor): Unit = visitor.visit(this)

  override def toString = s"""ProcessingInstruction("$target", "$text")"""
}
