package org.dom4j

/**
 * This interface is used by instances to retrieve
 * information about the current path hierarchy they are to process. It's
 * primary use is to retrieve the current being processed.
 */
trait ElementPath {

  // Number of elements in the path
  def size: Int

  // Current element
  def getCurrent: Element
}
