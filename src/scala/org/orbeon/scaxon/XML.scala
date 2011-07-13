/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.scaxon

import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr.ExpressionTool
import org.orbeon.saxon.value.StringValue
import xml.Elem
import org.orbeon.saxon.om._
import org.orbeon.oxf.util.XPathCache
import collection.JavaConverters._
import org.orbeon.oxf.xforms.{XFormsStaticStateImpl, XFormsInstance}
import java.util.Collections
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{TransformerUtils, NamespaceMapping}
import org.dom4j.{Attribute, QName, Element}
import org.orbeon.oxf.xforms.function.xxforms.XXFormsElement
import org.orbeon.saxon.pattern.{NameTest, NodeKindTest, LocalNameTest}
import org.orbeon.saxon.dom4j.{DocumentWrapper, NodeWrapper}

object XML {

    // TODO: Like for XFSS, this should not be global
    private val wrapper = new DocumentWrapper(Dom4jUtils.createDocument, null, XPathCache.getGlobalConfiguration)

    // Convenience methods for the XPath API
    def evalOne(item: Item, expr: String, namespaces: NamespaceMapping = XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING, variables: Map[String, ValueRepresentation] = null) =
        XPathCache.evaluaSingleteKeepItems(Collections.singletonList(item), 1, expr, namespaces, if (variables == null) null else variables.asJava, null, null, null, null)

    def eval(item: Item, expr: String, namespaces: NamespaceMapping = XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING, variables: Map[String, ValueRepresentation] = null) =
        XPathCache.evaluate(item, expr, namespaces, if (variables == null) null else variables.asJava, null, null, null, null)

    // Runtime conversion to NodeInfo (can fail!)
    def asNodeInfo(item: Item) = item.asInstanceOf[NodeInfo]
    def asNodeInfoSeq(item: Item) = item.asInstanceOf[NodeInfo]

    // Element and attribute creation
    def element(name: QName): Element = Dom4jUtils.createElement(name)
    def elementInfo(qName: QName, content: Seq[Item] = Seq()): NodeInfo = {
        val newElement = element(qName)
        content foreach (XXFormsElement.addItem(newElement, _))
        wrapper.wrap(newElement)
    }

    def attribute(name: QName, value: String = ""): Attribute = Dom4jUtils.createAttribute(name, value)
    def attributeInfo(name: QName, value: String = ""): NodeInfo = wrapper.wrap(attribute(name, value))

    // Like XPath resolve-QName()
    def resolveQName(elementInfo: NodeInfo, lexicalQName: String): QName = {

        val checker = Name10Checker.getInstance
        val resolver = new InscopeNamespaceResolver(elementInfo)

        val structuredQName = StructuredQName.fromLexicalQName(lexicalQName, true, checker, resolver)
        QName.get(lexicalQName, structuredQName.getNamespaceURI)
    }

    // Like XPath name()
    def name(nodeInfo: NodeInfo) = nodeInfo.getDisplayName

    // Like XPath local-name()
    def localname(nodeInfo: NodeInfo) = nodeInfo.getLocalPart

    // Like XPath namespace-uri()
    def namespaceURI(nodeInfo: NodeInfo) = {
        var uri = nodeInfo.getURI
        if (uri == null) "" else uri
    }

    private def parseQName(lexicalQName: String) = {
        val checker = Name10Checker.getInstance
        val parts = checker.getQNameParts(lexicalQName)

        (parts(0), parts(1))
    }

    // Selector for any node
    // TODO: Use class to represent selectors
    val * = "*"

    // Useful predicates
    val hasChildren: NodeInfo => Boolean = element => element \ * nonEmpty
    val hasId: (NodeInfo, String) => Boolean = (element, id) => element \@ "id" === id
    val exists: (Seq[Item]) => Boolean = (items) => items.nonEmpty

    // Get the value of the first attribute passed if any
    def attValueOption(atts: Seq[NodeInfo]) = atts.headOption map (_.getStringValue)

    // Better operations on sequences of NodeInfo
    class RichNodeInfoSeq(seq: Seq[NodeInfo]) {
        // Semantic is the same as XPath: at least one value must match
        def ===(s: String) = seq exists (_ === s)

        def \@(attName: String): Seq[NodeInfo] = seq flatMap (_ \@ attName)
        def \(elementName: String): Seq[NodeInfo] = seq flatMap (_ \ elementName)
        def \\(elementName: String): Seq[NodeInfo] = seq flatMap (_ \\ elementName)

        def att(attName: String) = \@(attName)
        def child(elementName: String) = \(elementName)
        def descendant(elementName: String) = \\(elementName)

        def parent = seq map (_.getParent) filter (_ ne null)

        def getStringValue = seq match {
            case Seq() => ""
            case Seq(nodeInfo, _*) => nodeInfo.getStringValue
        }
    }

    implicit def nodeInfoSeqToRichNodeInfoSeq(seq: Seq[NodeInfo]): RichNodeInfoSeq = new RichNodeInfoSeq(seq)

    // Better operations on NodeInfo
    class RichNodeInfo(nodeInfo: NodeInfo) {

        def ===(s: String) = (s eq null) && (nodeInfo eq null) || (nodeInfo ne null) && nodeInfo.getStringValue == s

        // Return an element's attribute by name
        def \@(attName: String): Seq[NodeInfo] = find(Type.ATTRIBUTE, Axis.ATTRIBUTE, attName)
        def \@(attName: QName): Seq[NodeInfo] = find(Type.ATTRIBUTE, Axis.ATTRIBUTE, attName)
        def \(elementName: String): Seq[NodeInfo] = find(Type.ELEMENT, Axis.CHILD, elementName)
        def \\@(elementName: String): Seq[NodeInfo] = find(Type.ATTRIBUTE, Axis.DESCENDANT, elementName)
        def \\(elementName: String): Seq[NodeInfo] = find(Type.ELEMENT, Axis.DESCENDANT, elementName)

        def att(attName: String) = \@(attName)
        def child(elementName: String) = \(elementName)
        def descendant(elementName: String) = \\(elementName)

        def parent = Option(nodeInfo.getParent)

        def ancestor(elementName: String): Seq[NodeInfo] = find(Type.ELEMENT, Axis.ANCESTOR, elementName)
        def ancestorOrSelf (elementName: String): Seq[NodeInfo] = find(Type.ELEMENT, Axis.ANCESTOR_OR_SELF, elementName)
        def descendantOrSelf(elementName: String): Seq[NodeInfo] = find(Type.ELEMENT, Axis.DESCENDANT_OR_SELF, elementName)

        def preceding(elementName: String): Seq[NodeInfo] = find(Type.ELEMENT, Axis.PRECEDING, elementName) // TODO: use Type/NODE?
        def following(elementName: String): Seq[NodeInfo] = find(Type.ELEMENT, Axis.FOLLOWING, elementName) // TODO: use Type/NODE?

        def precedingSibling(elementName: String): Seq[NodeInfo] = find(Type.ELEMENT, Axis.PRECEDING_SIBLING, elementName) // TODO: use Type/NODE?
        def followingSibling(elementName: String): Seq[NodeInfo] = find(Type.ELEMENT, Axis.FOLLOWING_SIBLING, elementName) // TODO: use Type/NODE?

        def precedingElement = nodeInfo precedingSibling * headOption
        def followingElement = nodeInfo followingSibling * headOption

        private def find(nodeKind: Int, axisNumber: Byte, name: String) = {
            val pool = nodeInfo.getNamePool

            val test =
                if (name == "*") {
                    NodeKindTest.makeNodeKindTest(nodeKind)
                } else {
                    // For now just test on the local name
                    // TODO: support for testing on qualified name -> requires namespace context
//                    val fingerprint = pool.getFingerprint(uri, qName._2)
//                    val test = new NameTest(nodeKind, fingerprint, pool)

                    val qName = parseQName(name)

                    // Warn in case the caller used "foo:bar". We don't warn for plain "foo" as that's a common use case for
                    // attributes, even through it's actually wrong. However most attributes are unprefixed anyway so it will
                    // be rare, but we need to support qualified names quickly.
                    if (! Set("", "*")(qName._1))
                        throw new IllegalArgumentException("""Only local name tests of the form "*:foo" are supported.""")

                    new LocalNameTest(pool, nodeKind, qName._2)
                }

            // We know the result contains only NodeInfo
            val iterator: Iterator[NodeInfo] = nodeInfo.iterateAxis(axisNumber, test)

            iterator.toStream
        }

        private def find(nodeKind: Int, axisNumber: Byte, name: QName) = {
            val pool = nodeInfo.getNamePool
            val test = new NameTest(nodeKind, name.getNamespaceURI, name.getName, pool)

            // We know the result contains only NodeInfo
            val iterator: Iterator[NodeInfo] = nodeInfo.iterateAxis(axisNumber, test)

            iterator.toStream
        }
    }

    implicit def nodeInfoToRichNodeInfo(nodeInfo: NodeInfo): RichNodeInfo = new RichNodeInfo(nodeInfo)

    // Other implicits

    implicit def itemToItemSeq(item: Item) = Seq(item)
    implicit def nodeInfoToNodeInfoSeq(node: NodeInfo) = if (node ne null) Seq(node) else Seq()// TODO: don't take null

    implicit def instanceToNodeInfo(instance: XFormsInstance) = instance.getInstanceRootElementInfo

    implicit def itemSeqToString(items: Seq[Item]): String = itemSeqToStringOption(items).orNull // TODO: don't return null
    implicit def itemSeqToItemOption(items: Seq[Item]): Option[Item] = items.headOption

    implicit def itemSeqToStringOption(items: Seq[Item]): Option[String] =
        items.headOption map (_.getStringValue)

    implicit def itemSeqToBoolean(items: Seq[Item]): Boolean =
        ExpressionTool.effectiveBooleanValue(new ListIterator(items.asJava))

    implicit def itemSeqToFirstItem(items: Seq[Item]): Item = items.headOption.orNull // TODO: don't return null

    implicit def nodeInfoToDom4jElement(nodeInfo: NodeInfo): Element =
        nodeInfo.asInstanceOf[NodeWrapper].getUnderlyingNode.asInstanceOf[Element]

    def elemToDocumentInfo(e: Elem): DocumentInfo = TransformerUtils.stringToTinyTree(XPathCache.getGlobalConfiguration, e.toString, false, false)
    implicit def elemToNodeInfo(e: Elem): NodeInfo = elemToDocumentInfo(e) \ * head
    implicit def elemToNodeInfoSeq(e: Elem): Seq[NodeInfo] = elemToDocumentInfo(e) \ *

    implicit def stringSeqToSequenceIterator(seq: Seq[String]): SequenceIterator =
        new ListIterator(seq map (stringToStringValue(_)) asJava)

    implicit def itemSeqToSequenceIterator[T <: Item](seq: Seq[T]): SequenceIterator = new ListIterator(seq.asJava)

    implicit def stringToStringValue(s: String) = StringValue.makeStringValue(s)
    implicit def stringToQName(s: String) = QName.get(s)
    implicit def stringToItem(s: String) = StringValue.makeStringValue(s)
    implicit def stringToItems(s: String) = Seq(StringValue.makeStringValue(s))

    implicit def saxonIteratorToItem(i: SequenceIterator): Item = i.next()

    implicit def saxonIteratorToScalaIterator[T <: Item](i: SequenceIterator): Iterator[T] = new Iterator[T] {

        private var current = i.next()

        def next() = {
            val result = current
            current = i.next()
            result.asInstanceOf[T]
        }

        def hasNext = current ne null
    }

    implicit def saxonIteratorToScalaSeq[T <: Item](i: SequenceIterator): Seq[T] = saxonIteratorToScalaIterator[T](i).toSeq
}
