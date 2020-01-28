package org.orbeon.dom.tree

import java.{util => ju}

import org.orbeon.dom.{IllegalAddException, Node}

/**
 * `ContentListFacade` represents a facade of the content of a
 * which is returned via calls to the   method to allow users to modify the content of a
 * directly using the  interface. This list
 * is backed by the branch such that changes to the list will be reflected in
 * the branch and changes to the branch will be reflected in this list.
 */
class ContentListFacade[T <: Node](val branch: AbstractBranch, val branchContent: ju.List[T]) extends ju.AbstractList[T] {

  override def add(node: T): Boolean = {
    branch.childAdded(node)
    branchContent.add(node)
  }

  override def add(index: Int, node: T): Unit = {
    branch.childAdded(node)
    branchContent.add(index, node)
  }

  override def set(index: Int, node: T): T = {
    branch.childAdded(node)
    branchContent.set(index, node)
  }

  def remove(node: T): Boolean = {
    branch.childRemoved(node)
    branchContent.remove(node)
  }

  override def remove(index: Int): T = {
    val node = branchContent.remove(index)
    if (node ne null) {
      branch.childRemoved(node)
    }
    node
  }

  override def addAll(collection: ju.Collection[_ <: T]): Boolean = {
    var count = branchContent.size
    val iter = collection.iterator()
    while (iter.hasNext) {
      add(iter.next())
      count += 1
    }
    count == branchContent.size
  }

  override def addAll(index: Int, collection: ju.Collection[_ <: T]): Boolean = {
    var _index = index

    var count = branchContent.size
    val iter = collection.iterator()
    while (iter.hasNext) {
      add(_index, iter.next())
      _index += 1
      count -= 1
    }
    count == branchContent.size
  }

  override def clear(): Unit = {
    val iter = iterator()
    while (iter.hasNext) {
      val node = iter.next()
      branch.childRemoved(node)
    }
    branchContent.clear()
  }

  override def removeAll(c: ju.Collection[_]): Boolean = {
    val iter = c.iterator()

    while (iter.hasNext)
      branch.childRemoved(asNode(iter.next()))

    branchContent.removeAll(c)
  }

  // Operations which don't mutate the content

  def size: Int = branchContent.size

  override def isEmpty: Boolean = branchContent.isEmpty
  override def contains(o: AnyRef): Boolean = branchContent.contains(o)

  override def toArray: Array[AnyRef] = branchContent.toArray()

  def toArray(a: Array[T]): Array[T] = branchContent.toArray[T](a)

  override def containsAll(c: ju.Collection[_]): Boolean = branchContent.containsAll(c)

  def get(index: Int): T = branchContent.get(index)

  override def indexOf(o: AnyRef): Int = branchContent.indexOf(o)

  override def lastIndexOf(o: AnyRef): Int = branchContent.lastIndexOf(o)

  private def asNode(node: Any): Node =
    node match {
      case node: Node => node
      case o          => throw new IllegalAddException(s"This list must contain instances of Node. Invalid type: $o")
    }
}
