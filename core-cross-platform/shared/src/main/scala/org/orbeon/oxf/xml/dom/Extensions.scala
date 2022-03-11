/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.xml.dom

import java.net.URI
import java.{util => ju}

import org.orbeon.dom._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiverHelper}
import org.xml.sax.helpers.AttributesImpl

import scala.jdk.CollectionConverters._


object Extensions {

  trait VisitorListener {
    def startElement(element: Element): Unit
    def endElement(element: Element): Unit
    def text(text: Text): Unit
  }

  private val XmlNamespaceMap = Map(XMLConstants.XML_PREFIX -> XMLConstants.XML_URI)
  private val IdQName = QName("id")

  implicit class DomElemOps(private val e: Element) extends AnyVal {

    def idOpt: Option[String] = e.attributeValueOpt(IdQName)
    def idOrNull: String      = e.attributeValue(IdQName)
    def idOrThrow: String     = idOpt getOrElse (throw new IllegalArgumentException)

    def resolveStringQName(qNameString: String, unprefixedIsNoNamespace: Boolean): Option[QName] =
      Extensions.resolveQName(e.namespaceForPrefix, qNameString, unprefixedIsNoNamespace)

    def resolveStringQNameOrThrow(qNameString: String, unprefixedIsNoNamespace: Boolean): QName =
      Extensions.resolveQName(e.namespaceForPrefix, qNameString, unprefixedIsNoNamespace) getOrElse
        (throw new IllegalArgumentException)

    def resolveAttValueQName(attName: QName, unprefixedIsNoNamespace: Boolean): Option[QName] =
      resolveStringQName(e.attributeValue(attName), unprefixedIsNoNamespace)

    def resolveAttValueQName(attName: String, unprefixedIsNoNamespace: Boolean): Option[QName] =
      resolveStringQName(e.attributeValue(attName), unprefixedIsNoNamespace)

    def copyMissingNamespacesByPrefix(sourceElem: Option[Element], prefixesToFilter: Set[String] = Set.empty): Element = {

      val srcElemNs  = sourceElem map (_.allInScopeNamespacesAsStrings) getOrElse XmlNamespaceMap
      val destElemNs = e.allInScopeNamespacesAsStrings

      for ((prefix, uri) <- srcElemNs)
        if (! destElemNs.contains(prefix) && ! prefixesToFilter.contains(prefix))
          e.addNamespace(prefix, uri)

      e
    }

    /**
      * Return a new document with all parent namespaces copied to the new root element, assuming they are not already
      * declared on the new root element.
      *
      * @param detach  if true the element is detached, otherwise it is deep copied
      * @return new document
      */
    def createDocumentCopyParentNamespaces(detach: Boolean, prefixesToFilter: Set[String] = Set.empty): Document = {
      val savedParentElemOpt = e.parentElemOpt
      val document =
        if (detach)
          Document(e.detach().asInstanceOf[Element])
        else
          Document(e.createCopy)
      document.getRootElement.copyMissingNamespacesByPrefix(savedParentElemOpt, prefixesToFilter)
      document
    }

    def copyAndCopyParentNamespaces: Element =
      e.createCopy.copyMissingNamespacesByPrefix(e.parentElemOpt)

    def getNamespaceContextNoDefault: Map[String, String] =
      e.allInScopeNamespacesAsStrings.filter(_._1.nonEmpty)

    /**
    * Visit the `Element`'s descendants.
    *
    * @param visitorListener listener to call back
    * @param mutable         whether the source tree can mutate while being visited
    */
    def visitDescendants(visitorListener: VisitorListener, mutable: Boolean, includeSelf: Boolean = false): Unit = {

      if (includeSelf)
        visitorListener.startElement(e)

      // If the source tree can mutate, copy the list first, otherwise the DOM might throw exceptions
      val immutableContent =
        if (mutable)
          List(e.content)
        else
          e.content

      // Iterate over the content
      for (childNode <- immutableContent)
        childNode match {
          case childElem: Element =>
            visitorListener.startElement(childElem)
            childElem.visitDescendants(visitorListener, mutable, includeSelf = false)
            visitorListener.endElement(childElem)
          case text: Text => visitorListener.text(text)
          case _ => // Ignore as we don't need other node types for now
        }

      if (includeSelf)
        visitorListener.endElement(e)
    }

    def ancestorIterator(includeSelf: Boolean): Iterator[Element] = new Iterator[Element] {

      private var current = if (includeSelf) e else e.getParent

      override def hasNext: Boolean = current ne null

      override def next(): Element = {
        val r = current
        current = r.getParent
        r
      }
    }

    def descendantElementIterator(includeSelf: Boolean): Iterator[Element] = {

      val childrenAndDescendants =
        e.content.iterator collect { case e: Element => e } flatMap (_.descendantElementIterator(includeSelf = true))

      if (includeSelf)
        Iterator(e) ++ childrenAndDescendants
      else
        childrenAndDescendants
    }

    /**
     * Resolve a URI string against an element, taking into account ancestor xml:base attributes for
     * the resolution.
     *
     * @param baseURI optional base URI
     * @param uri     URI to resolve
     * @return resolved URI
     */
    def resolveXMLBase(baseURI: Option[String], uri: String): URI = {

      // Allow for null `Element` (why? and what about `baseURI` in this case?)
      if (e == null)
        return URI.create(uri)

      // Collect `xml:base` attributes
      val xmlBaseValuesLeafToRootIt =
        ancestorIterator(includeSelf = true) flatMap
          (_.attributeValueOpt(XMLConstants.XML_BASE_QNAME))

      val urisRootToLeaf =
        baseURI.iterator ++ xmlBaseValuesLeafToRootIt.toArray.reverseIterator ++ Iterator(uri)

      urisRootToLeaf.foldLeft(null: URI) {
        case (r, s) =>
          val currentXMLBaseURI = URI.create(s)
          if (r eq null)
            currentXMLBaseURI
          else
            r.resolve(currentXMLBaseURI)
      }
    }

    def attributesAsSax: AttributesImpl = {
      val result = new AttributesImpl
      for (att <- e.attributeIterator)
        result.addAttribute(att.getNamespaceURI, att.getName, att.getQualifiedName, XMLReceiverHelper.CDATA, att.getValue)
      result
    }
  }

  /**
    * Extract a QName from a string value, given namespace mappings. Return null if the text is empty.
    *
    * @param namespaces              prefix -> URI mappings
    * @param qNameStringOrig         QName to analyze
    * @param unprefixedIsNoNamespace if true, an unprefixed value is in no namespace; if false, it is in the default namespace
    */
  def resolveQName(
    namespaces              : String => Option[String],
    qNameStringOrig         : String,
    unprefixedIsNoNamespace : Boolean
  ): Option[QName] =
    qNameStringOrig.trimAllToOpt map { qNameString =>

      val (localName, prefix, namespaceURI) =
        qNameString.indexOf(':') match {
          case 0 =>
            throw new IllegalArgumentException(s"Empty prefix for QName: `$qNameString`")
          case -1 =>
            val prefix = ""
            (
              qNameString,
              prefix,
              if (unprefixedIsNoNamespace) "" else namespaces(prefix).getOrElse("")
            )
          case colonIndex =>
            val prefix = qNameString.substring(0, colonIndex)
            (
              qNameString.substring(colonIndex + 1),
              prefix,
              namespaces(prefix).getOrElse(throw new OXFException(s"No namespace declaration found for prefix: `$prefix`"))
            )
        }

      QName(localName, Namespace(prefix, namespaceURI))
    }

  def resolveAttValueQNameJava(elem: Element, attributeQName: QName, unprefixedIsNoNamespace: Boolean): QName =
    elem.resolveStringQName(elem.attributeValue(attributeQName), unprefixedIsNoNamespace).orNull

  def resolveAttValueQNameJava(elem: Element, attributeName: String): QName =
    elem.resolveStringQName(elem.attributeValue(attributeName), unprefixedIsNoNamespace = true).orNull

  def resolveTextValueQNameJava(elem: Element, unprefixedIsNoNamespace: Boolean): QName =
    resolveQName(elem.namespaceForPrefix, elem.getStringValue, unprefixedIsNoNamespace).orNull

  def createDocumentCopyParentNamespacesJava(elem: Element, detach: Boolean): Document =
    elem.createDocumentCopyParentNamespaces(detach)

  def copyAndCopyParentNamespacesJava(elem: Element): Element =
    elem.copyAndCopyParentNamespaces

  def getNamespaceContextNoDefaultJava(elem: Element): Map[String, String] =
    elem.getNamespaceContextNoDefault

  def getNamespaceContextNoDefaultAsJavaMap(elem: Element): ju.Map[String, String] =
    elem.getNamespaceContextNoDefault.asJava
}
