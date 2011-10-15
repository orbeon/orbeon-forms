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
import org.orbeon.saxon.value.StringValue
import xml.Elem
import org.orbeon.saxon.om._
import org.orbeon.oxf.util.XPathCache
import collection.JavaConverters._
import org.orbeon.oxf.xforms.{XFormsStaticStateImpl, XFormsInstance}
import java.util.Collections
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{TransformerUtils, NamespaceMapping}
import org.orbeon.oxf.xforms.function.xxforms.XXFormsElement
import org.orbeon.saxon.dom4j.{DocumentWrapper, NodeWrapper}
import org.dom4j.{Document, Attribute, QName, Element}
import org.orbeon.saxon.pattern._
import org.orbeon.saxon.expr.{Token, ExpressionTool}

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
        val uri = nodeInfo.getURI
        if (uri == null) "" else uri
    }

    private def parseQName(lexicalQName: String) = {
        val checker = Name10Checker.getInstance
        val parts = checker.getQNameParts(lexicalQName)

        (parts(0), parts(1))
    }

    // Useful predicates
    val hasChildren: NodeInfo => Boolean = element => element \ * nonEmpty
    val hasId: (NodeInfo, String) => Boolean = (element, id) => element \@ "id" === id
    val exists: (Seq[Item]) => Boolean = (items) => items.nonEmpty

    // Get the value of the first attribute passed if any
    def attValueOption(atts: Seq[NodeInfo]) = atts.headOption map (_.getStringValue)

    // Node test
    abstract class Test {
        def test(nodeInfo: NodeInfo): NodeTest

        // Combinators
        def or(that: Test) = new OrTest(this, that)
        def and(that: Test) = new AndTest(this, that)
        def except(that: Test) = new ExceptTest(this, that)

        // Symbolic equivalents
        def ||(that: Test) = or(that)
        def &&(that: Test) = and(that)
        def -(that: Test) = except(that)
    }

    class NodeLocalNameTest(nodeKind: Int, name: String) extends Test {
        override def test(nodeInfo: NodeInfo) = {

            val pool = nodeInfo.getNamePool

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
    }

    class NodeQNameTest(nodeKind: Int, name: QName) extends Test {
        override def test(nodeInfo: NodeInfo) = {
            val pool = nodeInfo.getNamePool
            new NameTest(nodeKind, name.getNamespaceURI, name.getName, pool)
        }
    }

    class OrTest(s1: Test, s2: Test) extends Test {
        def test(nodeInfo: NodeInfo) = new CombinedNodeTest(s1.test(nodeInfo), Token.UNION, s2.test(nodeInfo))
    }

    class AndTest(s1: Test, s2: Test) extends Test {
        def test(nodeInfo: NodeInfo) = new CombinedNodeTest(s1.test(nodeInfo), Token.INTERSECT, s2.test(nodeInfo))
    }

    class ExceptTest(s1: Test, s2: Test) extends Test {
        def test(nodeInfo: NodeInfo) = new CombinedNodeTest(s1.test(nodeInfo), Token.EXCEPT, s2.test(nodeInfo))
    }

    // Match any element
    val * = new Test {
        def test(nodeInfo: NodeInfo) = NodeKindTest.makeNodeKindTest(Type.ELEMENT)
    }

    // Match any child node
    val node = new Test {
        def test(nodeInfo: NodeInfo) = AnyNodeTest.getInstance()
    }

    // Match any attribute
    val @* = new Test {
        def test(nodeInfo: NodeInfo) = NodeKindTest.makeNodeKindTest(Type.ATTRIBUTE)
    }

    // Passing a string as test means to test on the local name of an element
    implicit def stringToElementLocalNameTest(s: String) = new NodeLocalNameTest(Type.ELEMENT, s)

    // Passing a QName as test means to test on the qualified name of an element
    implicit def qNameToElementQNameTest(s: QName) = new NodeQNameTest(Type.ELEMENT, s)

    // Operations on NodeInfo
    class NodeInfoOps(nodeInfo: NodeInfo) {

        require(nodeInfo ne null)

        def ===(s: String) = (s eq null) && (nodeInfo eq null) || (nodeInfo ne null) && nodeInfo.getStringValue == s

        def \(test: Test) = find(Axis.CHILD, test)
        def \\(test: Test): Seq[NodeInfo] = find(Axis.DESCENDANT, test)

        // Return an element's attributes
        def \@(attName: String): Seq[NodeInfo] = \@(new NodeLocalNameTest(Type.ATTRIBUTE, attName))
        def \@(attName: QName): Seq[NodeInfo] = \@(new NodeQNameTest(Type.ATTRIBUTE, attName))
        def \@(test: Test): Seq[NodeInfo] = find(Axis.ATTRIBUTE, test)

        def \\@(attName: String): Seq[NodeInfo] = \\@(new NodeLocalNameTest(Axis.ATTRIBUTE, attName))
        def \\@(test: Test): Seq[NodeInfo] = find(Axis.DESCENDANT, test)

        def root = nodeInfo.getDocumentRoot

        def att(attName: String) = \@(attName)
        def att(test: Test) = \@(test)
        def child(test: Test) = \(test)
        def descendant(test: Test) = \\(test)

        def attValue(attName: String) = \@(attName).stringValue

        def self(test: Test) = find(Axis.SELF, test)
        def parent = Option(nodeInfo.getParent)

        def ancestor(test: Test): Seq[NodeInfo] = find(Axis.ANCESTOR, test)
        def ancestorOrSelf (test: Test): Seq[NodeInfo] = find(Axis.ANCESTOR_OR_SELF, test)
        def descendantOrSelf(test: Test): Seq[NodeInfo] = find(Axis.DESCENDANT_OR_SELF, test)

        def preceding(test: Test): Seq[NodeInfo] = find(Axis.PRECEDING, test) // TODO: use Type/NODE?
        def following(test: Test): Seq[NodeInfo] = find(Axis.FOLLOWING, test) // TODO: use Type/NODE?

        def precedingSibling(test: Test): Seq[NodeInfo] = find(Axis.PRECEDING_SIBLING, test) // TODO: use Type/NODE?
        def followingSibling(test: Test): Seq[NodeInfo] = find(Axis.FOLLOWING_SIBLING, test) // TODO: use Type/NODE?

        def precedingElement = nodeInfo precedingSibling * headOption
        def followingElement = nodeInfo followingSibling * headOption

        def stringValue = nodeInfo.getStringValue

        private def find(axisNumber: Byte, test: Test) = {
            // We know the result contains only NodeInfo, but ouch, this is a cast!
            val iterator = asScalaIterator(nodeInfo.iterateAxis(axisNumber, test.test(nodeInfo))).asInstanceOf[Iterator[NodeInfo]]
            // Be lazy: a good idea?
            iterator.toStream
        }
    }

    // Operations on sequences of NodeInfo
    class NodeInfoSeqOps(seq: Seq[NodeInfo]) {

        require(seq ne null)

        // Semantic is the same as XPath: at least one value must match
        def ===(s: String) = seq exists (_ === s)

        def \@(attName: String): Seq[NodeInfo] = seq flatMap (_ \@ attName)
        def \(test: Test): Seq[NodeInfo] = seq flatMap (_ \ test)
        def \\(test: Test): Seq[NodeInfo] = seq flatMap (_ \\ test)

        def att(attName: String) = \@(attName)
        def child(test: Test) = \(test)
        def descendant(test: Test) = \\(test)

        def parent = seq map (_.getParent) filter (_ ne null)

        // The string value is not defined on sequences. We take the first value, for convenience, like in XPath 2.0's
        // XPath 1.0 compatibility mode.
        def stringValue = seq match {
            case Seq() => ""
            case Seq(nodeInfo, _*) => nodeInfo.getStringValue
        }
    }

    // Scope ops on NodeInfo / Seq[NodeInfo]
    implicit def nodeInfoToRichNodeInfo(nodeInfo: NodeInfo): NodeInfoOps = new NodeInfoOps(nodeInfo)
    implicit def nodeInfoSeqToRichNodeInfoSeq(seq: Seq[NodeInfo]): NodeInfoSeqOps = new NodeInfoSeqOps(seq)

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

    def elemToDom4j(e: Elem): Document = Dom4jUtils.readDom4j(e.toString)
    def elemToDocumentInfo(e: Elem): DocumentInfo = TransformerUtils.stringToTinyTree(XPathCache.getGlobalConfiguration, e.toString, false, false)

    implicit def elemToItem(e: Elem): Item = elemToDocumentInfo(e) \ * head
    implicit def elemToItemSeq(e: Elem): Seq[Item] = elemToDocumentInfo(e) \ *
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

    implicit def asScalaIterator(i: SequenceIterator): Iterator[Item] = new Iterator[Item] {

        private var current = i.next()

        def next() = {
            val result = current
            current = i.next()
            result
        }

        def hasNext = current ne null
    }

    implicit def asScalaSeq(i: SequenceIterator): Seq[Item] = asScalaIterator(i).toSeq
}
