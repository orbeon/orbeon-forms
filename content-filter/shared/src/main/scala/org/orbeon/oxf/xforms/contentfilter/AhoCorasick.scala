package org.orbeon.oxf.xforms.contentfilter

import scala.collection.mutable


case class Emit(start: Int, end: Int, keyword: String)

class AhoCorasickTrie private (root: AhoCorasickTrie.Node) {

  def parseText(text: String): List[Emit] = {
    val results = mutable.ListBuffer.empty[Emit]
    var current = root
    for (i <- 0 until text.length) {
      val c = text.charAt(i)
      while (current != root && ! current.children.contains(c)) {
        current = current.fail
      }
      current = current.children.getOrElse(c, root)
      for (keyword <- current.outputs) {
        val start = i - keyword.length + 1
        val end   = i
        results += Emit(start, end, keyword)
      }
    }
    results.toList
  }
}

object AhoCorasickTrie {

  private class Node {
    val children: mutable.Map[Char, Node] = mutable.Map.empty
    var fail: Node = null
    var outputs: List[String] = List.empty
  }

  class Builder {
    private val root = new Node
    private val keywords = mutable.Set.empty[String]

    def addKeyword(keyword: String): Builder = {
      if (keyword.nonEmpty && ! keywords.contains(keyword)) {
        keywords += keyword
        var current = root
        for (c <- keyword)
          current = current.children.getOrElseUpdate(c, new Node)
        if (! current.outputs.contains(keyword))
          current.outputs = keyword :: current.outputs
      }
      this
    }

    def build(): AhoCorasickTrie = {
      val queue = mutable.Queue.empty[Node]
      root.fail = root

      for (child <- root.children.values) {
        child.fail = root
        queue.enqueue(child)
      }

      while (queue.nonEmpty) {
        val current = queue.dequeue()
        for ((c, child) <- current.children) {
          var fallback = current.fail
          while (fallback != root && ! fallback.children.contains(c))
            fallback = fallback.fail
          child.fail = fallback.children.getOrElse(c, root)
          if (child.fail != child && child.fail.outputs.nonEmpty)
            child.outputs = (child.outputs ++ child.fail.outputs).distinct
          queue.enqueue(child)
        }
      }

      new AhoCorasickTrie(root)
    }
  }

  def builder(): Builder = new Builder
}
