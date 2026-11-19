/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.dom.tree

import org.orbeon.dom.*

import java.io.Serializable
import java.util as ju


object CompactElement {

  val EmptyRawAtts: Array[AnyRef] = new Array[AnyRef](0)

  private val XmlNamespace = Namespace("xml", "http://www.w3.org/XML/1998/namespace")
  private val XmlBaseQName = QName("base", XmlNamespace)

  def fromElement(
    src               : Element,
    overrideXmlBaseOpt: Option[String],
    overrideData      : AnyRef
  ): CompactElement = {
    val srcAttCount = src.attributeCount
    overrideXmlBaseOpt match {
      case None =>
        if (srcAttCount == 0) {
          new CompactElement(src.getQName, EmptyRawAtts, overrideData)
        } else {
          val arr = new Array[AnyRef](srcAttCount * 2)
          var i   = 0
          while (i < srcAttCount) {
            val att        = src.attribute(i)
            arr(i * 2)     = att.getQName
            arr(i * 2 + 1) = att.getValue
            i += 1
          }
          new CompactElement(src.getQName, arr, overrideData)
        }

      case Some(resolvedXmlBase) =>
        var existingXmlBaseIdx = -1
        var i                  = 0
        while (i < srcAttCount && existingXmlBaseIdx == -1) {
          if (src.attribute(i).getQName == XmlBaseQName)
            existingXmlBaseIdx = i
          i += 1
        }

        val totalAtts = if (existingXmlBaseIdx >= 0) srcAttCount else srcAttCount + 1
        val arr       = new Array[AnyRef](totalAtts * 2)
        var j         = 0
        while (j < srcAttCount) {
          val att    = src.attribute(j)
          arr(j * 2) = att.getQName
          arr(j * 2 + 1) =
            if (j == existingXmlBaseIdx) resolvedXmlBase
            else att.getValue
          j += 1
        }
        if (existingXmlBaseIdx < 0) {
          arr(srcAttCount * 2)     = XmlBaseQName
          arr(srcAttCount * 2 + 1) = resolvedXmlBase
        }
        new CompactElement(src.getQName, arr, overrideData)
    }
  }

  private final class CompactAttribute(
    owner    : CompactElement,
    val qName: QName,
    val value: String
  ) extends AbstractNode with Attribute {

    override def getParent: Element = owner

    def getQName: QName            = qName
    def getNamespace: Namespace    = qName.namespace
    def getNamespacePrefix: String = qName.namespace.prefix
    def getNamespaceURI: String    = qName.namespace.uri
    override def getName: String   = qName.localName
    def getQualifiedName: String   = qName.qualifiedName

    def getValue: String              = value
    override def getText: String      = value
    def setValue(value: String): Unit = throw new UnsupportedOperationException("CompactAttribute is immutable")

    def getData: AnyRef             = null
    def setData(data: AnyRef): Unit = throw new UnsupportedOperationException("CompactAttribute is immutable")

    def accept(visitor: Visitor): Unit = visitor.visit(this)
  }
}

/**
 * Immutable, detached, childless `Element` representation for compiled XForms `ElementAnalysis` nodes.
 *
 * Layout on 64-bit JVM with compressed OOPs:
 * - `CompactElement` instance: 12B header + 3 * 4B refs = 24B
 * - `rawAtts`: shared 0B `EmptyRawAtts` when attributeless, or `Array[AnyRef](2 * N)` (`[QName, String, ...]`)
 */
final class CompactElement(
  val qName  : QName,
  val rawAtts: Array[AnyRef],
  val data   : AnyRef
) extends Element
     with Serializable {

  import CompactElement.*

  def getType: Int               = 1
  def getQName: QName            = qName
  override def getName: String   = qName.localName
  def getNamespace: Namespace    = qName.namespace
  def getNamespacePrefix: String = qName.namespace.prefix
  def getNamespaceURI: String    = qName.namespace.uri
  def getQualifiedName: String   = qName.qualifiedName

  def getParent: Element                  = null
  def setParent(parent: Element): Unit    = throw new UnsupportedOperationException("CompactElement is immutable")
  def getDocument: Document               = null
  def setDocument(document: Document): Unit = throw new UnsupportedOperationException("CompactElement is immutable")
  def isRootElement: Boolean              = false
  def detach(): Node                      = this

  def getData: AnyRef             = data
  def setData(data: AnyRef): Unit = throw new UnsupportedOperationException("CompactElement is immutable")

  def getText: String            = ""
  def getTextTrim: String        = ""
  def getStringValue: String     = ""
  def setText(text: String): Unit = throw new UnsupportedOperationException("CompactElement is immutable")

  // ---- Attributes ----

  def attributeCount: Int = rawAtts.length >>> 1

  def attributeValue(name: String): String = {
    val len = rawAtts.length
    var i   = 0
    while (i < len) {
      val qn = rawAtts(i).asInstanceOf[QName]
      if (qn.localName == name)
        return rawAtts(i + 1).asInstanceOf[String]
      i += 2
    }
    null
  }

  def attributeValue(qName: QName): String = {
    val len = rawAtts.length
    var i   = 0
    while (i < len) {
      val qn = rawAtts(i).asInstanceOf[QName]
      if (qn == qName)
        return rawAtts(i + 1).asInstanceOf[String]
      i += 2
    }
    null
  }

  override def hasAttribute(name: String): Boolean = attributeValue(name) ne null
  override def hasAttribute(qName: QName): Boolean = attributeValue(qName) ne null

  def attribute(index: Int): Attribute = {
    val count = attributeCount
    if (index < 0 || index >= count)
      throw new IndexOutOfBoundsException(s"Index: $index, Size: $count")
    new CompactAttribute(
      this,
      rawAtts(index * 2).asInstanceOf[QName],
      rawAtts(index * 2 + 1).asInstanceOf[String]
    )
  }

  def attribute(name: String): Attribute = {
    val len = rawAtts.length
    var i   = 0
    while (i < len) {
      val qn = rawAtts(i).asInstanceOf[QName]
      if (qn.localName == name)
        return new CompactAttribute(this, qn, rawAtts(i + 1).asInstanceOf[String])
      i += 2
    }
    null
  }

  def attribute(qName: QName): Attribute = {
    val len = rawAtts.length
    var i   = 0
    while (i < len) {
      val qn = rawAtts(i).asInstanceOf[QName]
      if (qn == qName)
        return new CompactAttribute(this, qn, rawAtts(i + 1).asInstanceOf[String])
      i += 2
    }
    null
  }

  def jAttributes: ju.List[Attribute] = {
    val count = attributeCount
    if (count == 0) {
      ju.Collections.emptyList()
    } else {
      val list = new ju.ArrayList[Attribute](count)
      var i    = 0
      while (i < count) {
        list.add(attribute(i))
        i += 1
      }
      ju.Collections.unmodifiableList(list)
    }
  }

  def jAttributeIterator: ju.Iterator[Attribute] =
    if (rawAtts.isEmpty)
      ju.Collections.emptyIterator()
    else
      new ju.Iterator[Attribute] {
        private var idx = 0
        def hasNext: Boolean = idx < attributeCount
        def next(): Attribute = {
          if (! hasNext)
            throw new NoSuchElementException
          val res = attribute(idx)
          idx += 1
          res
        }
      }

  // ---- Namespaces (synthesized from element + attribute QNames without extra fields) ----

  def declaredNamespacesIterator: Iterator[Namespace] = Iterator.empty

  def allInScopeNamespacesAsNodes: Map[String, Namespace] = {
    var m = Map[String, Namespace](XmlNamespace.prefix -> XmlNamespace)
    if (qName.namespace.uri.nonEmpty)
      m += (qName.namespace.prefix -> qName.namespace)
    val len = rawAtts.length
    var i   = 0
    while (i < len) {
      val ns = rawAtts(i).asInstanceOf[QName].namespace
      if (ns.uri.nonEmpty && ! m.contains(ns.prefix))
        m += (ns.prefix -> ns)
      i += 2
    }
    m
  }

  def allInScopeNamespacesAsStrings: Map[String, String] =
    allInScopeNamespacesAsNodes.map { case (k, v) => k -> v.uri }

  def getNamespaceForPrefix(prefix: String): Namespace = {
    val p = if (prefix eq null) "" else prefix
    if (p == "xml")
      XmlNamespace
    else if (qName.namespace.uri.nonEmpty && qName.namespace.prefix == p)
      qName.namespace
    else {
      val len = rawAtts.length
      var i   = 0
      while (i < len) {
        val ns = rawAtts(i).asInstanceOf[QName].namespace
        if (ns.uri.nonEmpty && ns.prefix == p)
          return ns
        i += 2
      }
      null
    }
  }

  def namespaceForPrefix(prefix: String): Option[String] =
    Option(getNamespaceForPrefix(prefix)).map(_.uri)

  def prefixesForNamespaceUri(uri: String): Iterator[String] =
    allInScopeNamespacesAsNodes.valuesIterator.collect {
      case ns if ns.uri == uri && ns.prefix.nonEmpty => ns.prefix
    }

  // ---- Empty content / child navigation ----

  def nodeCount: Int                                  = 0
  def node(index: Int): Node                          = null
  def jContent: ju.List[Node] & ju.RandomAccess       = ju.Collections.emptyList[Node]().asInstanceOf[ju.List[Node] & ju.RandomAccess]
  def jNodeIterator: ju.Iterator[Node]                = ju.Collections.emptyIterator()
  def regularNodeIterator: Iterator[Node]             = Iterator.empty
  def containsElement: Boolean                        = false
  def element(name: String): Element                  = null
  def element(qName: QName): Element                  = null
  def jElements: ju.List[Element]                     = ju.Collections.emptyList()
  def jElements(name: String): ju.List[Element]       = ju.Collections.emptyList()
  def jElements(qName: QName): ju.List[Element]       = ju.Collections.emptyList()
  def jElementIterator: ju.Iterator[Element]          = ju.Collections.emptyIterator()
  def jElementIterator(name: String): ju.Iterator[Element] = ju.Collections.emptyIterator()

  // Safe no-ops for idempotent cleanup callers
  def clearContent(): Unit = ()
  def normalize(): Unit    = ()

  // ---- Unsupported mutations ----

  private def immutableError(): Nothing =
    throw new UnsupportedOperationException("CompactElement is immutable")

  def addAttribute(name: String, value: String): Element  = immutableError()
  def addAttribute(qName: QName, value: String): Element  = immutableError()
  def removeAttribute(qName: QName): Element              = immutableError()
  def addNamespace(prefix: String, uri: String): Element  = immutableError()
  def addText(text: String): Element                      = immutableError()
  def add(att: Attribute): Unit                           = immutableError()
  def add(text: Text): Unit                               = immutableError()
  def add(namespace: Namespace): Unit                     = immutableError()
  def remove(att: Attribute): Boolean                     = immutableError()
  def remove(namespace: Namespace): Boolean               = immutableError()
  def remove(text: Text): Boolean                         = immutableError()
  def appendContent(branch: Branch): Unit                 = immutableError()
  def addElement(name: String): Element                   = immutableError()
  def addElement(qname: QName): Element                   = immutableError()
  def add(node: Node): Unit                               = immutableError()
  def add(comment: Comment): Unit                         = immutableError()
  def add(element: Element): Unit                         = immutableError()
  def add(pi: ProcessingInstruction): Unit                = immutableError()
  def remove(node: Node): Boolean                         = immutableError()
  def remove(comment: Comment): Boolean                   = immutableError()
  def remove(element: Element): Boolean                   = immutableError()
  def remove(pi: ProcessingInstruction): Boolean          = immutableError()

  // Materialize a mutable `ConcreteElement` copy if a caller clones this element to attach namespaces/children
  override def deepCopy: Element = {
    val copy = new ConcreteElement(qName)
    copy.setData(data)
    val len = rawAtts.length
    var i   = 0
    while (i < len) {
      copy.addAttribute(
        rawAtts(i).asInstanceOf[QName],
        rawAtts(i + 1).asInstanceOf[String]
      )
      i += 2
    }
    copy
  }

  override def createCopy: Element = deepCopy

  def accept(visitor: Visitor): Unit = {
    visitor.visit(this)
    var i     = 0
    val count = attributeCount
    while (i < count) {
      visitor.visit(attribute(i))
      i += 1
    }
  }

  override def toString: String =
    s"CompactElement(<$getQualifiedName/>, atts=$attributeCount)"
}
