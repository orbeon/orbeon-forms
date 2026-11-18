package org.orbeon.oxf.cache

import org.orbeon.dom
import java.util.concurrent.locks.Lock


/**
 * Interface that cacheable objects can optionally implement to support callback methods.
 */
trait Cacheable {
  /**
   * Called when the object is added to the cache.
   */
  def added(): Unit

  /**
   * Called when the object is explicitly removed from the cache.
   */
  def removed(): Unit

  /**
   * Optional lock the cache must obtain to evict the item from cache.
   *
   * @return lock or null
   */
  def getEvictionLock: Lock

  /**
   * Called when the object is being evicted from cache.
   */
  def evicted(): Unit
}

/**
 * Optional interface that cached objects can implement to report an estimated retained heap footprint in bytes
 * (excluding globally shared singletons or shared static state).
 */
trait SizedCacheable {
  def estimatedMemoryBytes: Long
}

object DomMemorySizeEstimator {

  def estimateStringBytes(s: String): Long =
    if (s eq null) 0L
    else 40L + ((s.length.toLong + 7L) & ~7L)

  def estimateOrbeonDomDocument(doc: dom.Document, maxElements: Int = 25000): Long =
    if (doc eq null) 0L
    else {
      val rootOpt = Option(doc.getRootElement)
      64L + rootOpt.map(estimateOrbeonDomElement(_, maxElements)).getOrElse(0L)
    }

  def estimateOrbeonDomElement(root: dom.Element, maxElements: Int = 25000): Long =
    if (root eq null) {
      0L
    } else {
      var totalBytes   = 0L
      var visitedElems = 0
      val stack        = new java.util.ArrayDeque[dom.Element](64)
      stack.push(root)

      while (! stack.isEmpty && visitedElems < maxElements) {
        val elem = stack.pop()
        visitedElems += 1

        totalBytes += (if (elem.getData ne null) 136L else 88L)

        val attIt = elem.attributeIterator
        while (attIt.hasNext) {
          val att = attIt.next()
          totalBytes += 48L + estimateStringBytes(att.getValue)
        }

        val contentIt = elem.content.iterator
        while (contentIt.hasNext) {
          contentIt.next() match {
            case childElem: dom.Element =>
              stack.push(childElem)
            case text: dom.Text =>
              totalBytes += 32L + estimateStringBytes(text.getText)
            case _: dom.Namespace =>
              totalBytes += 32L
            case other: dom.Node =>
              totalBytes += 40L + estimateStringBytes(other.getText)
          }
        }
      }

      if (! stack.isEmpty && visitedElems > 0) {
        val avgPerElem = totalBytes / visitedElems
        totalBytes + stack.size.toLong * avgPerElem * 2L
      } else {
        totalBytes
      }
    }
}