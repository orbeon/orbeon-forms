package org.orbeon.dom

import java.{util => ju}

import scala.jdk.CollectionConverters._

/**
 * `Branch` interface defines the common behaviour for Nodes which
 * can contain child nodes (content) such as XML elements and documents. This
 * interface allows both elements and documents to be treated in a polymorphic
 * manner when changing or navigating child nodes (content).
 */
trait Branch extends Node {

  /**
   * Returns the `Node` at the specified index position.
   */
  def node(index: Int): Node

  def nodeCount: Int

  /**
   * Returns the content nodes of this branch as a backed so that
   * the content of this branch may be modified directly using the
   * interface. The `List` is backed by the
   * `Branch` so that changes to the list are reflected in the
   * branch and vice versa.
   */
  def jContent: ju.List[Node]
  def content: Seq[Node] = jContent.asScala

  /**
   * Returns an iterator through the content nodes of this branch
   */
  def jNodeIterator: ju.Iterator[Node]
  def nodeIterator: Iterator[Node] = jNodeIterator.asScala

  /**
   * Appends the content of the given branch to this branch instance. This
   * method behaves like the  method.
   */
  def appendContent(branch: Branch): Unit

  /**
   * Clears the content for this branch, removing any `Node`
   * instances this branch may contain.
   */
  def clearContent(): Unit

  /**
   * Adds a new `Element` node with the given name to this branch
   * and returns a reference to the new element.
   */
  def addElement(name: String): Element

  /**
   * Adds a new `Element` node with the given to
   * this branch and returns a reference to the new element.
   */
  def addElement(qname: QName): Element

  /**
   * Adds the given `Node` or throws `IllegalAddException`
   * if the given node is not of a valid type. This is a polymorphic method
   * which will call the typesafe method for the node type such as
   * add(Element) or add(Comment).
   */
  def add(node: Node): Unit

  /**
   * Adds the given `Comment` to this branch. If the given node
   * already has a parent defined then an `IllegalAddException`
   * will be thrown.
   */
  def add(comment: Comment): Unit

  /**
   * Adds the given `Element` to this branch. If the given node
   * already has a parent defined then an `IllegalAddException`
   * will be thrown.
   */
  def add(element: Element): Unit

  /**
   * Adds the given `ProcessingInstruction` to this branch. If
   * the given node already has a parent defined then an
   * `IllegalAddException` will be thrown.
   */
  def add(pi: ProcessingInstruction): Unit

  /**
   * Removes the given `Node` if the node is an immediate child
   * of this branch. If the given node is not an immediate child of this
   * branch then the method should be used instead. This
   * is a polymorphic method which will call the typesafe method for the node
   * type such as remove(Element) or remove(Comment).
   *
   * @return true if the node was removed
   */
  def remove(node: Node): Boolean

  /**
   * Removes the given `Comment` if the node is an immediate
   * child of this branch. If the given node is not an immediate child of this
   * branch then the method should be used instead.
   *
   * @return true if the comment was removed
   */
  def remove(comment: Comment): Boolean

  /**
   * Removes the given `Element` if the node is an immediate
   * child of this branch. If the given node is not an immediate child of this
   * branch then the method should be used instead.
   *
   * @return true if the element was removed
   */
  def remove(element: Element): Boolean

  /**
   * Removes the given `ProcessingInstruction` if the node is an
   * immediate child of this branch. If the given node is not an immediate
   * child of this branch then the method should be used
   * instead.
   *
   * @return true if the processing instruction was removed
   */
  def remove(pi: ProcessingInstruction): Boolean

  /**
   * Puts all `Text` nodes in the full depth of the sub-tree
   * underneath this `Node`, including attribute nodes, into a
   * "normal" form where only structure (e.g., elements, comments, processing
   * instructions) separates
   * `Text` nodes, i.e., there are neither adjacent
   * `Text` nodes nor empty `Text` nodes. This can
   * be used to ensure that the DOM view of a document is the same as if it
   * were saved and re-loaded, and is useful when operations (such as XPointer
   * lookups) that depend on a particular document tree structure are to be
   * used.
   */
  def normalize(): Unit
}
