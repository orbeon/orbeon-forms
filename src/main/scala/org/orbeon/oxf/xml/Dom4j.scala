/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xml

import java.util.{List => JList}
import java.{lang => jl, util => ju}

import org.orbeon.dom._
import org.orbeon.oxf.util.StringUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils._
import org.orbeon.saxon.value.Whitespace

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.xml.{Elem, XML}

object Dom4j {

  /**
   * Compare two dom4j documents.
   *
   * This comparison:
   *
   * - doesn't modify the input documents
   * - normalizes adjacent text/CDATA nodes into single text nodes
   * - ignores resulting blank text nodes
   * - trims non-blank text nodes before comparing them
   * - ignores namespace nodes (not because they don't matter, but they are hard to handle)
   * - ignores entity nodes (we shouldn't have any and we don't know how to compare them with dom4j anyway)
   * - compares qualified names by checking namespace URIs and local names, but ignores prefixes
   * - ignores the relative order of an element's attributes
   *
   * In-scope namespaces are hard to compare when dealing with documents that were embedded. The embedded document
   * might declare many namespaces on its root element because of embedding. However the other document might have a
   * slightly different namespace layout. It is not clear, for the purpose of unit tests, whether we can realistically
   * compare namespace nodes in a much better way, without some kind of schema information.
   */
  def compareDocumentsIgnoreNamespacesInScope(left: Document, right: Document): Boolean = {
    val normalizeText = StringUtils.trimAllToEmpty _
    compareTwoNodes(
      left          = normalizeTextNodes(createCopy(left.getRootElement)),
      right         = normalizeTextNodes(createCopy(right.getRootElement)))(
      normalizeText = normalizeText
    )
  }

  /**
   * Same as compareDocumentsIgnoreNamespacesInScope but collapse white space when comparing text.
   */
  def compareDocumentsIgnoreNamespacesInScopeCollapse(left: Document, right: Document): Boolean =
    compareElementsIgnoreNamespacesInScopeCollapse(left.getRootElement, right.getRootElement)

  def compareElementsIgnoreNamespacesInScopeCollapse(left: Element, right: Element): Boolean = {
    val normalizeText = (c: String) => Whitespace.collapseWhitespace(c).toString
    compareTwoNodes(normalizeTextNodes(createCopy(left)), normalizeTextNodes(createCopy(right)))(normalizeText)
  }

  // Only keep the nodes we care about
  private def filterOut(l: Seq[Node]) = l collect {
    case n @ (_: Document | _: Element | _: Attribute | _: Comment | _: ProcessingInstruction) => n
    case t: Text if t.getText.nonAllBlank => t
  }

  private def compareTwoNodeSeqs(left: Seq[Node], right: Seq[Node])(normalizeText: String => String) =
    left.lengthCompare(right.size) == 0 && (left.zip(right) forall
      { case (n1, n2) => compareTwoNodes(n1, n2)(normalizeText) })

  private implicit def dom4jListToNodeSeq(l: JList[_]): Seq[Node] = l.asInstanceOf[JList[Node]].asScala

  // An ordering for attributes, which takes into account the namespace URI and the local name
  private implicit object AttributeOrdering extends Ordering[Attribute] {
    def compare(x: Attribute, y: Attribute): Int =
      x.getQName.uriQualifiedName compare y.getQName.uriQualifiedName
  }

  private def compareTwoNodes(left: Node, right: Node)(normalizeText: String => String): Boolean =
    (left, right) match {
      case (d1: Document, d2: Document) =>
        compareTwoNodeSeqs(filterOut(d1.content), filterOut(d2.content))(normalizeText)
      case (e1: Element, e2: Element) =>
        e1.getQName == e2.getQName &&
          compareTwoNodeSeqs(e1.attributes.asScala.sorted, e2.attributes.asScala.sorted)(normalizeText) && // sort attributes
          compareTwoNodeSeqs(filterOut(e1.content), filterOut(e2.content))(normalizeText)
      case (a1: Attribute, a2: Attribute) =>
        a1.getQName == a2.getQName &&
          a1.getValue == a2.getValue
      case (c1: Comment, c2: Comment) =>
        c1.getText == c2.getText
      case (t1: Text, t2: Text) =>
        normalizeText(t1.getText) == normalizeText(t2.getText)
      case (p1: ProcessingInstruction, p2: ProcessingInstruction) =>
        p1.getTarget == p2.getTarget && compareProcessingInstruction(p1, p2)
      case _ =>
        false
    }

  private def compareProcessingInstruction(p1: ProcessingInstruction, p2: ProcessingInstruction): Boolean = {

    def parseValues(text: String): ju.Map[String, String] = {
      val result = new ju.HashMap[String, String]()
      val st = new ju.StringTokenizer(text, " =\'\"", true)
      while (st.hasMoreTokens) {
        val name = getName(st)
        if (st.hasMoreTokens) {
          val value = getValue(st)
          result.put(name, value)
        }
      }
      result
    }

    def getName(st: ju.StringTokenizer): String = {
      var token = st.nextToken()
      val sb = new jl.StringBuilder(token)
      while (st.hasMoreTokens) {
        token = st.nextToken()
        if (token != "=") {
          sb.append(token)
        } else {
          return sb.toString.trim
        }
      }
      sb.toString.trim
    }

    def getValue(st: ju.StringTokenizer): String = {
      var token = st.nextToken()
      val sb = new jl.StringBuilder
      while (st.hasMoreTokens && token != "\'" && token != "\"") {
        token = st.nextToken()
      }
      val quote = token
      while (st.hasMoreTokens) {
        token = st.nextToken()
        if (quote != token) {
          sb.append(token)
        } else {
          return sb.toString
        }
      }
      sb.toString
    }

    parseValues(p1.getText) == parseValues(p2.getText)
  }

  // Return an element's directly nested elements
  def elements(e: Element): Seq[Element] = e.elements.asScala

  // Return an element's directly nested elements with the given name
  def elements(e: Element, qName: QName): Seq[Element] = e.elements(qName).asScala
  def elements(e: Element, name: String): Seq[Element] = e.elements(name).asScala

  // Return the element's content as a mutable buffer
  def content(e: Element): mutable.Buffer[Node] = e.content.asScala

  // Return an element's attributes
  def attributes(e: Element): Seq[Attribute] = e.attributes.asScala

  // Ordering on QName, comparing first by namespace URI then by local name
  implicit object QNameOrdering extends Ordering[QName] {
    def compare(x: QName, y: QName): Int = {
      val nsOrder = x.namespace.uri compareTo y.namespace.uri
      if (nsOrder != 0)
        nsOrder
      else
        x.localName compareTo y.localName
    }
  }

  // Ensure that a path to an element exists by creating missing elements if needed
  def ensurePath(root: Element, path: Seq[QName]): Element = {

    @tailrec def insertIfNeeded(parent: Element, qNames: Iterator[QName]): Element =
      if (qNames.hasNext) {
        val qName = qNames.next()
        val existing = Dom4j.elements(parent, qName)

        val existingOrNew =
          existing.headOption getOrElse {
            val newElement = DocumentFactory.createElement(qName)
            parent.add(newElement)
            newElement
          }

        insertIfNeeded(existingOrNew, qNames)
      } else
        parent

    insertIfNeeded(root, path.iterator)
  }

  def visitSubtree(container: Element, process: Element => Boolean): Unit = {
    for (childNode <- new java.util.ArrayList(container.content).asScala) {
      childNode match {
        case e: Element =>
          if (process(e))
            visitSubtree(e, process)
        case _ =>
      }
    }
  }


  // TODO: should ideally not got go through serialization/deserialization
  implicit def elemToDocument(e: Elem): Document = Dom4jUtils.readDom4j(e.toString)
  implicit def elemToElement(e: Elem): Element   = Dom4jUtils.readDom4j(e.toString).getRootElement
  implicit def elementToElem(e: Element): Elem   = XML.loadString(e.serializeToString())

  // TODO: There is probably a better way to write these conversions
  implicit def scalaElemSeqToDom4jElementSeq(seq: Iterable[Elem]): Seq[Element] = seq map elemToElement toList
  implicit def dom4jElementSeqToScalaElemSeq(seq: Iterable[Element]): Seq[Elem] = seq map elementToElem toList
}