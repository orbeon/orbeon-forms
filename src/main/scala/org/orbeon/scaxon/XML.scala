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
import org.orbeon.oxf.util.XPathCache._
import org.orbeon.oxf.util.XPath._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import collection.JavaConverters._
import org.orbeon.oxf.xforms.{XFormsUtils, XFormsInstance}
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{XMLReceiver, XMLParsing, TransformerUtils, NamespaceMapping}
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.dom4j.{XPath ⇒ _, _}
import org.orbeon.saxon.pattern._
import org.orbeon.saxon.expr.{Token, ExpressionTool}
import org.orbeon.saxon.om._
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.saxon.tinytree.TinyTree
import annotation.tailrec
import collection._
import org.orbeon.saxon.xqj.{SaxonXQDataFactory, StandardObjectConverter}
import org.orbeon.oxf.util.XPath

object XML {

    // This should ideally not be global. Tried 2013-11-14 to use DocumentWrapper.makeWrapper instead, see
    // 2263a3f7b9565fa2102a7cc56ecb007a5c881312 and 0d7bc1fda0a121b2c107adc15a92cba67a09984f, but this is not good
    // enough as NodeWrapper does need a Configuration to operate properly. So for now we keep this wrapper.
    private val Wrapper = new DocumentWrapper(Dom4jUtils.createDocument, null, XPath.GlobalConfiguration)

    // Convenience methods for the XPath API
    def evalOne(
            item            : Item,
            expr            : String,
            namespaces      : NamespaceMapping                 = BASIC_NAMESPACE_MAPPING,
            variables       : Map[String, ValueRepresentation] = null,
            reporter        : Reporter                         = null,
            functionContext : FunctionContext                  = null)(
            implicit library: FunctionLibrary                  = null
    ): Item =
        evaluateSingleKeepItems(
            Seq(item).asJava,
            1,
            expr,
            namespaces,
            if (variables eq null) null else variables.asJava,
            library,
            functionContext,
            null,
            null,
            reporter
        )

    // Evaluate an XPath expression and return a Seq of native Java objects (String, Boolean, etc.), but NodeInfo
    // wrappers are preserved.
    def eval(
            item            : Item,
            expr            : String,
            namespaces      : NamespaceMapping                 = BASIC_NAMESPACE_MAPPING,
            variables       : Map[String, ValueRepresentation] = null,
            reporter        : Reporter                         = null,
            functionContext : FunctionContext                  = null)(
            implicit library: FunctionLibrary                  = null
    ): Seq[AnyRef] =
        evaluate(item,
            expr,
            namespaces,
            if (variables eq null) null else variables.asJava,
            library, functionContext,
            null,
            null,
            reporter
        ).asScala

    // Evaluate an XPath expression as a value template
    def evalValueTemplate(
            item            : Item,
            expr            : String,
            namespaces      : NamespaceMapping                 = BASIC_NAMESPACE_MAPPING,
            variables       : Map[String, ValueRepresentation] = null,
            reporter        : Reporter                         = null,
            functionContext : FunctionContext                  = null)(
            implicit library: FunctionLibrary                  = null
    ): String =
        evaluateAsAvt(
            item,
            expr,
            namespaces,
            if (variables eq null) null else variables.asJava,
            library,
            functionContext,
            null,
            null,
            reporter
        )

    // Runtime conversion to NodeInfo (can fail!)
    def asNodeInfo(item: Item) = item.asInstanceOf[NodeInfo]
    def asNodeInfoSeq(item: Item) = item.asInstanceOf[NodeInfo]

    // Convert a ns → name tuple to a QName, including separating prefix/local if needed
    // TODO: Confusing that the name can be a local name?
    def toQName(qName: (String, String)) = {
        val prefixLocal = parseQName(qName._2)
        QName.get(prefixLocal._2, prefixLocal._1, qName._1)
    }

    // Effective boolean value of the iterator
    def effectiveBooleanValue(iterator: SequenceIterator) =
        ExpressionTool.effectiveBooleanValue(iterator)

    // Element and attribute creation
    def element(name: QName): Element = Dom4jUtils.createElement(name)
    def elementInfo(qName: QName, content: Seq[Item] = Seq()): NodeInfo = {
        val newElement = Wrapper.wrap(element(qName))
        insert(into = Seq(newElement), origin = content)
        newElement
    }

    def attribute(name: QName, value: String = ""): Attribute = Dom4jUtils.createAttribute(name, value)
    def attributeInfo(name: QName, value: String = ""): NodeInfo = Wrapper.wrap(attribute(name, value))

    def namespace(prefix: String, uri: String): Namespace = Dom4jUtils.createNamespace(prefix, uri)
    def namespaceInfo(prefix: String, uri: String): NodeInfo = Wrapper.wrap(namespace(prefix, uri))

    // Parse the given qualified name and return the separated prefix and local name
    def parseQName(lexicalQName: String) = {
        val checker = Name10Checker.getInstance
        val parts = checker.getQNameParts(lexicalQName)

        (parts(0), parts(1))
    }

    // Useful predicates
    val hasChildren: NodeInfo ⇒ Boolean = element ⇒ element / * nonEmpty
    val hasId: (NodeInfo) ⇒ Boolean = (element) ⇒ element /@ "id" nonEmpty
    val hasIdValue: (NodeInfo, String) ⇒ Boolean = (element, id) ⇒ element /@ "id" === id
    val exists: (Seq[Item]) ⇒ Boolean = (items) ⇒ items.nonEmpty

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

    private val ElementOrAttribute = Set(Type.ELEMENT, Type.ATTRIBUTE)

    private class LocalNameOnlyTest(pool: NamePool, localName: String) extends NodeTest {

        // Matches the name only, but not the node kind
        def matches(nodeKind: Int, fingerprint: Int, annotation: Int) =
            fingerprint != -1 && ElementOrAttribute(nodeKind.toShort) && localName == pool.getLocalName(fingerprint)

        override def matches(tree: TinyTree, nodeNr: Int) = {
            val fingerprint = tree.getNameCode(nodeNr) & NamePool.FP_MASK
            fingerprint != -1 && ElementOrAttribute(tree.getNodeKind(nodeNr).toShort) && localName == pool.getLocalName(fingerprint)
        }

        override def matches(node: NodeInfo) =
            ElementOrAttribute(node.getNodeKind.toShort) && localName == node.getLocalPart

        override def getNodeKindMask = 1 << Type.ELEMENT | 1 << Type.ATTRIBUTE
        override def toString = "*:" + localName

        def getDefaultPriority = -0.25 // probably not used right now
    }

    private class NameOnlyTest(pool: NamePool, uri: String, localName: String) extends NodeTest {

        private val fingerprint = pool.allocate("", uri, localName) & NamePool.FP_MASK

        // Matches the name only, but not the node kind
        def matches(nodeKind: Int, fingerprint: Int, annotation: Int) =
            ElementOrAttribute(nodeKind.toShort) && (fingerprint & NamePool.FP_MASK) == this.fingerprint

        override def matches(tree: TinyTree, nodeNr: Int) =
            ElementOrAttribute(tree.getNodeKind(nodeNr).toShort) && (tree.getNameCode(nodeNr) & NamePool.FP_MASK) == this.fingerprint

        override def matches(node: NodeInfo) =
            ElementOrAttribute(node.getNodeKind.toShort) && (
                node match {
                    case _: FingerprintedNode ⇒ node.getFingerprint == fingerprint
                    case _                    ⇒ localName == node.getLocalPart && uri == node.getURI
                }
            )

        override def getNodeKindMask = 1 << Type.ELEMENT | 1 << Type.ATTRIBUTE
        override def toString = pool.getClarkName(fingerprint)

        def getDefaultPriority = -0.25 // probably not used right now
    }

    object AnyTest extends Test {
        def test(nodeInfo: NodeInfo) = AnyNodeTest.getInstance
    }

    class NodeLocalNameTest(name: String, nodeKind: Option[Int] = None) extends Test {
        override def test(nodeInfo: NodeInfo) = {

            val pool = nodeInfo.getNamePool

            // For now just test on the local name
            // TODO: support for testing on qualified name → requires namespace context
//                    val fingerprint = pool.getFingerprint(uri, qName._2)
//                    val test = new NameTest(nodeKind, fingerprint, pool)

            val qName = parseQName(name)

            // Warn in case the caller used "foo:bar". We don't warn for plain "foo" as that's a common use case for
            // attributes, even through it's actually wrong. However most attributes are unprefixed anyway so it will
            // be rare, but we need to support qualified names quickly.
            if (! Set("", "*")(qName._1))
                throw new IllegalArgumentException("""Only local name tests of the form "*:foo" are supported.""")

            nodeKind map (new LocalNameTest(pool, _, qName._2)) getOrElse new LocalNameOnlyTest(pool, qName._2)
        }
    }

    class NodeQNameTest(name: (String, String), nodeKind: Option[Int] = None) extends Test {
        override def test(nodeInfo: NodeInfo) = {
            val pool = nodeInfo.getNamePool
            nodeKind map (new NameTest(_, name._1, name._2, pool)) getOrElse new NameOnlyTest(pool, name._1, name._2)
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

    private class NodeKindTestBase(nodeKind: Short) extends Test {
        private val test = NodeKindTest.makeNodeKindTest(nodeKind)
        def test(nodeInfo: NodeInfo) = test
    }

    // Match node types, like the XPath * or element(), @* or attribute(), document-node(), processing-instruction(),
    // comment(), text() or node() tests.
    val Element   : Test = new NodeKindTestBase(Type.ELEMENT)
    val *         : Test = Element
    val Attribute : Test = new NodeKindTestBase(Type.ATTRIBUTE)
    val @*        : Test = Attribute
    val Document  : Test = new NodeKindTestBase(Type.DOCUMENT)
    val PI        : Test = new NodeKindTestBase(Type.PROCESSING_INSTRUCTION)
    val Comment   : Test = new NodeKindTestBase(Type.COMMENT)
    val Text      : Test = new NodeKindTestBase(Type.TEXT)
    val Node      : Test = new NodeKindTestBase(Type.NODE)

    // Whether the node is an element and "supports" simple content, that is doesn't have child elements
    // NOTE: This ignores PI and comment nodes
    def supportsSimpleContent(nodeInfo: NodeInfo): Boolean =
        isElement(nodeInfo) && ! hasChildElement(nodeInfo)

    private def hasNonEmptyChildNode(nodeInfo: NodeInfo) =
        nodeInfo child Text filter (_.stringValue.nonEmpty) nonEmpty

    // TODO: Should be on NodeInfoOps!
    def isAttribute(nodeInfo: NodeInfo)          = nodeInfo.getNodeKind.toShort == Type.ATTRIBUTE
    def isElement(nodeInfo: NodeInfo)            = nodeInfo.getNodeKind.toShort == Type.ELEMENT
    def isElementOrAttribute(nodeInfo: NodeInfo) = ElementOrAttribute(nodeInfo.getNodeKind.toShort)

    // Whether the given node has at least one child element
    // TODO: Should be on NodeInfoOps!
    def hasChildElement(nodeInfo: NodeInfo) = nodeInfo child * nonEmpty

    // True if the node is an element, doesn't have child elements, and has at least one non-empty child text node,
    // NOTE: This ignores PI and comment nodes
    // TODO: Should be on NodeInfoOps!
    def hasSimpleContent(nodeInfo: NodeInfo): Boolean =
        isElement(nodeInfo) && ! hasChildElement(nodeInfo) && hasNonEmptyChildNode(nodeInfo)

    // True if the node is an element, has at least one child element, and has at least one non-empty child text node
    // NOTE: This ignores PI and comment nodes
    // TODO: Should be on NodeInfoOps!
    def hasMixedContent(nodeInfo: NodeInfo): Boolean =
        isElement(nodeInfo) && hasChildElement(nodeInfo) && hasNonEmptyChildNode(nodeInfo)

    // True if the node is an element and has no children nodes
    // NOTE: This ignores PI and comment nodes
    // TODO: Should be on NodeInfoOps!
    def hasEmptyContent(nodeInfo: NodeInfo): Boolean =
        isElement(nodeInfo) && (nodeInfo child Node isEmpty)

    // True if the node is an element, has at least one child element, and doesn't have non-empty child text nodes
    // NOTE: This ignores PI and comment nodes
    // TODO: Should be on NodeInfoOps!
    def hasElementOnlyContent(nodeInfo: NodeInfo): Boolean =
        isElement(nodeInfo) && hasChildElement(nodeInfo) && ! hasNonEmptyChildNode(nodeInfo)

    // Passing a string as test means to test on the local name of an element or attribute
    implicit def stringToTest(s: String): Test = new NodeLocalNameTest(s)

    // Passing a QName as test means to test on the qualified name of an element or attribute
    implicit def qNameToTest(attName: QName): Test = new NodeQNameTest((attName.getNamespaceURI, attName.getName))

    // Qualified name can also be passed as a pair of strings
    implicit def pairToTest(s: (String, String)): Test = new NodeQNameTest(s)

    // Operations on NodeInfo
    implicit class NodeInfoOps(val nodeInfo: NodeInfo) extends AnyVal {

        def ===(s: String) = (s eq null) && (nodeInfo eq null) || (nodeInfo ne null) && nodeInfo.getStringValue == s
        def !==(s: String) = ! ===(s)

        def /(test: Test) = find(Axis.CHILD, test)
        def \(test: Test) = /(test)
        def \\(test: Test): Seq[NodeInfo] = find(Axis.DESCENDANT, test)

        def firstChild(test: Test) = /(test).headOption

        // Return an element's attributes
        // Q: Should functions taking a String match on no namespace only?
        def /@(attName: String): Seq[NodeInfo] = /@(new NodeLocalNameTest(attName, Some(Type.ATTRIBUTE)))
        def \@(attName: String): Seq[NodeInfo] = /@(attName)
        def /@(attName: QName): Seq[NodeInfo] = /@(new NodeQNameTest((attName.getNamespaceURI, attName.getName), Some(Type.ATTRIBUTE)))
        def \@(attName: QName): Seq[NodeInfo] = /@(attName)
        def /@(attName: (String, String)): Seq[NodeInfo] = \@(new NodeQNameTest(attName, Some(Type.ATTRIBUTE)))
        def \@(attName: (String, String)): Seq[NodeInfo] = /@(attName)
        def /@(test: Test): Seq[NodeInfo] = find(Axis.ATTRIBUTE, test)
        def \@(test: Test): Seq[NodeInfo] = /@(test)

        // The following doesn't work right now because the DESCENDANT axis doesn't include attributes
//        def \\@(attName: String): Seq[NodeInfo] = \\@(new NodeLocalNameTest(attName, Some(Type.ATTRIBUTE)))
//        def \\@(attName: QName): Seq[NodeInfo] = \\@(new NodeQNameTest((attName.getNamespaceURI, attName.getName), Some(Type.ATTRIBUTE)))
//        def \\@(attName: (String, String)): Seq[NodeInfo] = \\@(new NodeQNameTest(attName, Some(Type.ATTRIBUTE)))
//        def \\@(test: Test): Seq[NodeInfo] = find(Axis.DESCENDANT, test)

        def root = nodeInfo.getDocumentRoot
        def rootElement = root / * head

        def att(attName: String) = /@(attName)
        def att(test: Test) = /@(test)
        def child(test: Test) = /(test)
        def descendant(test: Test) = \\(test)

        def attValue(attName: String) = /@(attName).stringValue
        def attValue(attName: QName)  = /@(attName).stringValue
        def attTokens(attName: String) = stringOptionToSet(Some(attValue(attName)))
        def attClasses = attTokens("class")
        def id = attValue("id")
        def hasId = att("id").nonEmpty && attValue("id").trim != ""

        def attValueOpt(attName: String) = /@(attName) match {
            case Seq() ⇒ None
            case s     ⇒ Some(s.stringValue)
        }

        def attValueOpt(attName: QName) = /@(attName) match {
            case Seq() ⇒ None
            case s     ⇒ Some(s.stringValue)
        }

        def elemValue(elemName: String) = /(elemName).stringValue
        def elemValue(elemName: QName)  = /(elemName).stringValue

        def elemValueOpt(elemName: String) = /(elemName) match {
            case Seq() ⇒ None
            case s     ⇒ Some(s.stringValue)
        }

        def elemValueOpt(elemName: QName) = /(elemName) match {
            case Seq() ⇒ None
            case s     ⇒ Some(s.stringValue)
        }

        def idOpt = attValueOpt("id")

        def self(test: Test) = find(Axis.SELF, test)
        def parent(test: Test) = find(Axis.PARENT, test)

        def parentOption: Option[NodeInfo] = Option(nodeInfo.getParent)

        def ancestor(test: Test): Seq[NodeInfo] = find(Axis.ANCESTOR, test)
        def ancestorOrSelf (test: Test): Seq[NodeInfo] = find(Axis.ANCESTOR_OR_SELF, test)
        def descendantOrSelf(test: Test): Seq[NodeInfo] = find(Axis.DESCENDANT_OR_SELF, test)

        def preceding(test: Test): Seq[NodeInfo] = find(Axis.PRECEDING, test) // TODO: use Type/NODE?
        def following(test: Test): Seq[NodeInfo] = find(Axis.FOLLOWING, test) // TODO: use Type/NODE?

        def precedingSibling(test: Test): Seq[NodeInfo] = find(Axis.PRECEDING_SIBLING, test) // TODO: use Type/NODE?
        def followingSibling(test: Test): Seq[NodeInfo] = find(Axis.FOLLOWING_SIBLING, test) // TODO: use Type/NODE?
        def sibling(test: Test): Seq[NodeInfo] = precedingSibling(test) ++ followingSibling(test)

        def namespaces        = find(Axis.NAMESPACE, AnyTest)
        def namespaceMappings = namespaces map (n ⇒ n.getLocalPart → n.getStringValue)

        def prefixesForURI(uri: String) = prefixesForURIImpl(uri, this)
        def nonEmptyPrefixesForURI(uri: String) = prefixesForURI(uri) filter (_ != "")

        def precedingElement = nodeInfo precedingSibling * headOption
        def followingElement = nodeInfo followingSibling * headOption

        def prefix       = nodeInfo.getPrefix

        // Like XPath local-name(), name(), namespace-uri(), resolve-QName()
        def localname    = nodeInfo.getLocalPart
        def name         = nodeInfo.getDisplayName

        def namespaceURI = {
            val uri = nodeInfo.getURI
            if (uri eq null) "" else uri
        }

        private def resolveStructuredQName(lexicalQName: String): StructuredQName = {

            val checker = Name10Checker.getInstance
            val resolver = new InscopeNamespaceResolver(nodeInfo)

            StructuredQName.fromLexicalQName(lexicalQName, true, checker, resolver)
        }

        def resolveURIQualifiedName(lexicalQName: String): (String, String) = {
            val structuredQName = resolveStructuredQName(lexicalQName)
            structuredQName.getNamespaceURI → structuredQName.getLocalName
        }

        def resolveQName(lexicalQName: String): QName = {
            val structuredQName = resolveStructuredQName(lexicalQName)
            QName.get(lexicalQName, structuredQName.getNamespaceURI)
        }

        // Return a qualified name as a (namespace uri, local name) pair
        // NOTE: The "URI qualified name" terminology comes from XPath 3.
        def uriQualifiedName = (nodeInfo.getURI, nodeInfo.getLocalPart)

        def stringValue = nodeInfo.getStringValue

        // Intended to be used for debugging
        def serializeToString: String = {
            val dom4jNode = XFormsUtils.getNodeFromNodeInfoConvert(nodeInfo)
            Dom4jUtils.nodeToString(dom4jNode)
        }

        def hasIdValue(id: String) = nodeInfo /@ "id" === id

        private def find(axisNumber: Byte, test: Test): Seq[NodeInfo] = {
            // We know the result contains only NodeInfo, but ouch, this is a cast!
            val iterator = asScalaIterator(nodeInfo.iterateAxis(axisNumber, test.test(nodeInfo))).asInstanceOf[Iterator[NodeInfo]]
            // FIXME: We should instead return the Iterator. This means all callers need to be adjusted.
            iterator.toStream
        }
    }

    // HACK because of compiler limitation:
    // "implementation restriction: nested class is not allowed in value class
    //  This restriction is planned to be removed in subsequent releases."
    private def prefixesForURIImpl(uri: String, ops: NodeInfoOps) =
        ops.namespaceMappings collect { case (prefix, `uri`) ⇒ prefix }

    // Operations on sequences of NodeInfo
    implicit class NodeInfoSeqOps(val seq: Seq[NodeInfo]) extends AnyVal {

        // Semantic is the same as XPath: at least one value must match
        def ===(s: String) = seq exists (_ === s)
        def !==(s: String) = ! ===(s)

        def /(test: Test): Seq[NodeInfo] = seq flatMap (_ / test)
        def \(test: Test): Seq[NodeInfo] = /(test)
        def \\(test: Test): Seq[NodeInfo] = seq flatMap (_ \\ test)

        def /@(attName: String): Seq[NodeInfo] = seq flatMap (_ /@ attName)
        def /@(attName: QName): Seq[NodeInfo] = seq flatMap (_ /@ attName)
        def /@(attName: (String, String)): Seq[NodeInfo] = seq flatMap (_ /@ attName)
        def /@(test: Test): Seq[NodeInfo] = seq flatMap (_ /@ test)

        def \@(attName: String): Seq[NodeInfo] = /@(attName)
        def \@(attName: QName): Seq[NodeInfo]  = /@(attName)
        def \@(attName: (String, String))      = /@(attName)
        def \@(test: Test): Seq[NodeInfo]      = /@(test)

        // The following doesn't work right now because the DESCENDANT axis doesn't include attributes
//        def \\@(attName: String): Seq[NodeInfo] = seq flatMap (_ \\@ attName)
//        def \\@(attName: QName): Seq[NodeInfo] = seq flatMap (_ \\@ attName)
//        def \\@(attName: (String, String)): Seq[NodeInfo] = seq flatMap (_ \\@ attName)
//        def \\@(test: Test): Seq[NodeInfo] = seq flatMap (_ \\@ test)

        def att(attName: String) = /@(attName)
        def child(test: Test) = /(test)
        def descendant(test: Test) = \\(test)

        def attValue(attName: String) = /@(attName) map (_.stringValue)
        def attValue(attName: QName)  = /@(attName) map (_.stringValue)

        def self(test: Test) = seq flatMap (_ self test)
        def parent(test: Test) = seq flatMap (_ parent test)

        def ancestor(test: Test): Seq[NodeInfo] = seq flatMap (_ ancestor test)
        def ancestorOrSelf (test: Test): Seq[NodeInfo] = seq flatMap (_ ancestorOrSelf test)
        def descendantOrSelf(test: Test): Seq[NodeInfo] = seq flatMap (_ descendantOrSelf test)

        def preceding(test: Test): Seq[NodeInfo] = seq flatMap (_ preceding test)
        def following(test: Test): Seq[NodeInfo] = seq flatMap (_ following test)

        def precedingSibling(test: Test) = seq flatMap (_ precedingSibling test)
        def followingSibling(test: Test) = seq flatMap (_ followingSibling test)
        def sibling(test: Test) = seq flatMap (_ sibling test)

        // The string value is not defined on sequences. We take the first value, for convenience, like in XPath 2.0's
        // XPath 1.0 compatibility mode.
        def stringValue = seq match {
            case Seq() ⇒ ""
            case Seq(nodeInfo, _*) ⇒ nodeInfo.getStringValue
        }
    }

    // Hand-made simple path search
    // - path *must* have the form "foo/bar/baz"
    // - each path element must be a NCName (non-qualified)
    // - as in XPath, non-qualified names mean "in no namespace"
    def path(context: NodeInfo, path: String) = {

        @tailrec def findChild(parent: Option[NodeInfo], tokens: List[String]): Option[NodeInfo] =
            if (tokens.isEmpty)
                parent
            else
                parent match {
                    case Some(p) ⇒ findChild(p child ("" → tokens.head) headOption, tokens.tail)
                    case None    ⇒ None
                }

        val tokens = split[List](path, "/")

        findChild(Some(context), tokens)
    }

    // Convert a Java object to a Saxon Item using the Saxon API
    val anyToItem = new StandardObjectConverter(new SaxonXQDataFactory {
        def getConfiguration = XPath.GlobalConfiguration
    }).convertToItem(_: Any)

    // Convert a Java object to a Saxon Item but keep unchanged if already an Item
    val anyToItemIfNeeded: Any ⇒ Item = {
        case i: Item ⇒ i
        case a       ⇒ anyToItem(a)
    }

    // Other implicits
    implicit def itemToItemSeq(item: Item) = Seq(item)
    implicit def nodeInfoToNodeInfoSeq(node: NodeInfo) = if (node ne null) Seq(node) else Seq()// TODO: don't take null

    implicit def instanceToNodeInfo(instance: XFormsInstance) = instance.rootElement

    implicit def itemSeqToString(items: Seq[Item]): String = itemSeqToStringOption(items).orNull // TODO: don't return null
    implicit def itemSeqToItemOption(items: Seq[Item]): Option[Item] = items.headOption

    implicit def itemSeqToStringOption(items: Seq[Item]): Option[String] =
        items.headOption map (_.getStringValue)

    implicit def itemSeqToBoolean(items: Seq[Item]): Boolean =
        effectiveBooleanValue(new ListIterator(items.asJava))

    implicit def itemSeqToFirstItem(items: Seq[Item]): Item = items.headOption.orNull // TODO: don't return null

    // UNSAFE
    def unwrapElement(nodeInfo: NodeInfo): Element =
        nodeInfo.asInstanceOf[VirtualNode].getUnderlyingNode.asInstanceOf[Element]

    // UNSAFE
    def unwrapDocument(nodeInfo: NodeInfo): Document =
        nodeInfo.asInstanceOf[VirtualNode].getUnderlyingNode.asInstanceOf[Document]

    def elemToSAX(e: Elem, xmlReceiver: XMLReceiver) =
        XMLParsing.stringToSAX(e.toString, "", xmlReceiver, XMLParsing.ParserConfiguration.PLAIN, true)

    def elemToDom4j(e: Elem): Document = Dom4jUtils.readDom4j(e.toString)

    def elemToDocumentInfo(e: Elem, readonly: Boolean = true): DocumentInfo =
        if (readonly)
            TransformerUtils.stringToTinyTree(XPath.GlobalConfiguration, e.toString, false, false)
        else
            new DocumentWrapper(elemToDom4j(e), null, XPath.GlobalConfiguration)

    def nodeInfoToElem(nodeInfo: NodeInfo): Elem =
        scala.xml.XML.loadString(TransformerUtils.tinyTreeToString(nodeInfo))

    implicit def elemToItem(e: Elem): Item = elemToDocumentInfo(e) / * head
    implicit def elemToItemSeq(e: Elem): Seq[Item] = elemToDocumentInfo(e) / *
    implicit def elemToNodeInfo(e: Elem): NodeInfo = elemToDocumentInfo(e) / * head
    implicit def elemToNodeInfoSeq(e: Elem): Seq[NodeInfo] = elemToDocumentInfo(e) / *

    implicit def stringSeqToSequenceIterator(seq: Seq[String]): SequenceIterator =
        new ListIterator(seq map stringToStringValue asJava)

    implicit def itemSeqToSequenceIterator[T <: Item](seq: Seq[T]): SequenceIterator = new ListIterator(seq.asJava)

    implicit def stringToQName(s: String) = QName.get(s ensuring ! s.contains(':'))
    implicit def tupleToQName(name: (String, String)) = QName.get(name._2, "", name._1)
    def stringToStringValue(s: String) = StringValue.makeStringValue(s)

    implicit def saxonIteratorToItem(i: SequenceIterator): Item = i.next()

    implicit def asStringSequenceIterator(i: Iterator[String]): SequenceIterator =
        asSequenceIterator(i map stringToStringValue)

    implicit def asSequenceIterator(i: Iterator[Item]): SequenceIterator = new SequenceIterator {

        private var currentItem: Item = _
        private var _position = 0

        def next() = {
            if (i.hasNext) {
                currentItem = i.next()
                _position += 1
            } else {
                currentItem = null
                _position = -1
            }

            currentItem
        }

        def current = currentItem
        def position = _position
        def close() = ()
        def getAnother = null
        def getProperties = 0
    }

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
