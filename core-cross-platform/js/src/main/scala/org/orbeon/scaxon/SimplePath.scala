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

import org.orbeon.dom.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.expr.parser.Token
import org.orbeon.saxon.model.Type
import org.orbeon.saxon.om.*
import org.orbeon.saxon.pattern.*
import org.orbeon.saxon.tree.iter.ListIterator

import scala.collection.Seq
import scala.jdk.CollectionConverters.*


// The idea of this is to provide simple path navigation operations on Saxon nodes, without creating
// full XPath expressions, as those need to be compiled, cached, and run, and can be very complex.
object SimplePath {

  type NodeColl = LazyList[NodeInfo]

  import Private._

  case class URIQualifiedName(uri: String, localName: String) {
    require(NameChecker.isValidNCName(localName))
  }

  // Passing a string as test means to test on the local name of an element or attribute
  implicit def stringToTest(s: String): Test = new NodeLocalNameTest(s)

  implicit def qNameToTest(attName: QName): Test = new NodeQNameTest((attName.namespace.uri, attName.localName))
  implicit def pairToTest(s: (String, String)): Test = new NodeQNameTest(s)
  implicit def uriQualifiedNameToTest(name: URIQualifiedName): Test = new NodeQNameTest(name.uri -> name.localName)

  // Node test
  abstract class Test {
    def test(nodeInfo: NodeInfo): NodeTest

    // Combinators
    def or(that: Test)     = new OrTest(this, that)
    def and(that: Test)    = new AndTest(this, that)
    def except(that: Test) = new ExceptTest(this, that)

    // Symbolic equivalents
    def ||(that: Test)     = or(that)
    def &&(that: Test)     = and(that)
    def -(that: Test)      = except(that)
  }

  object AnyTest extends Test {
    def test(nodeInfo: NodeInfo): NodeTest = AnyNodeTest.getInstance
  }

  class NodeLocalNameTest(name: String, nodeKind: Option[Int] = None) extends Test {
    def test(nodeInfo: NodeInfo): NodeTest = {

      val pool = nodeInfo.getConfiguration.getNamePool

      // For now just test on the local name
      // TODO: support for testing on qualified name -> requires namespace context
//                    val fingerprint = pool.getFingerprint(uri, qName._2)
//                    val test = new NameTest(nodeKind, fingerprint, pool)

      val qName = SaxonUtils.parseQName(name)

      // Warn in case the caller used "foo:bar". We don't warn for plain "foo" as that's a common use case for
      // attributes, even through it's actually wrong. However most attributes are unprefixed anyway so it will
      // be rare, but we need to support qualified names quickly.
      if (! Set("", "*")(qName._1))
        throw new IllegalArgumentException("""Only local name tests of the form "*:foo" are supported.""")

      nodeKind map (new LocalNameTest(pool, _, qName._2)) getOrElse
        new CombinedNodeTest(
          new LocalNameTest(pool, Type.ELEMENT, qName._2),
          Token.UNION,
          new LocalNameTest(pool, Type.ATTRIBUTE, qName._2)
        )
    }
  }

  class NodeQNameTest(name: (String, String), nodeKind: Option[Int] = None) extends Test {
    override def test(nodeInfo: NodeInfo): NodeTest = {
      val pool = nodeInfo.getConfiguration.getNamePool
      nodeKind map (new NameTest(_, name._1, name._2, pool)) getOrElse
        new CombinedNodeTest(
          new NameTest(Type.ELEMENT, name._1, name._2, pool),
          Token.UNION,
          new NameTest(Type.ATTRIBUTE, name._1, name._2, pool)
        )
    }
  }

  class OrTest(s1: Test, s2: Test) extends Test {
    def test(nodeInfo: NodeInfo): NodeTest = new CombinedNodeTest(s1.test(nodeInfo), Token.UNION, s2.test(nodeInfo))
  }

  class AndTest(s1: Test, s2: Test) extends Test {
    def test(nodeInfo: NodeInfo): NodeTest = new CombinedNodeTest(s1.test(nodeInfo), Token.INTERSECT, s2.test(nodeInfo))
  }

  class ExceptTest(s1: Test, s2: Test) extends Test {
    def test(nodeInfo: NodeInfo): NodeTest = new CombinedNodeTest(s1.test(nodeInfo), Token.EXCEPT, s2.test(nodeInfo))
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

  // Operations on NodeInfo
  implicit class NodeInfoOps(private val nodeInfo: NodeInfo) extends AnyVal {

    def ===(s: String) = (s eq null) && (nodeInfo eq null) || (nodeInfo ne null) && nodeInfo.getStringValue == s
    def !==(s: String) = ! ===(s)

    def /(test: Test) = find(AxisInfo.CHILD, test)

    def firstChildOpt(test: Test) = /(test).headOption
    def lastChildOpt(test: Test)  = /(test).lastOption

    // Return an element's attributes
    // Q: Should functions taking a String match on no namespace only?
    // Q: If the QName is specified, zero or one attribute can be returned. Should return `Option`?
    def /@(attName: String): NodeColl = /@(new NodeLocalNameTest(attName, Some(Type.ATTRIBUTE)))
    def /@(attName: QName): NodeColl = /@(new NodeQNameTest((attName.namespace.uri, attName.localName), Some(Type.ATTRIBUTE)))
    def /@(attName: (String, String)): NodeColl = /@(new NodeQNameTest(attName, Some(Type.ATTRIBUTE)))
    def /@(test: Test): NodeColl = find(AxisInfo.ATTRIBUTE, test)

    def namespaceNodes: NodeColl = find(AxisInfo.NAMESPACE, AnyTest)

    // The following doesn't work right now because the DESCENDANT axis doesn't include attributes
//        def //@(attName: String): NodeColl = //@(new NodeLocalNameTest(attName, Some(Type.ATTRIBUTE)))
//        def //@(attName: QName): NodeColl = //@(new NodeQNameTest((attName.getNamespaceURI, attName.getName), Some(Type.ATTRIBUTE)))
//        def //@(attName: (String, String)): NodeColl = //@(new NodeQNameTest(attName, Some(Type.ATTRIBUTE)))
//        def //@(test: Test): NodeColl = find(Axis.DESCENDANT, test)

    def root: NodeInfo = nodeInfo.getRoot // Saxon 10: may or may not be a document node
    def rootElement = root / * head

    def att(attName: String) = /@(attName)
    def att(test: Test) = /@(test)
    def child(test: Test) = /(test)

    def attValue(attName: String)  = /@(attName).stringValue
    def attValue(attName: QName)   = /@(attName).stringValue
    def attValue(test: Test)       = /@(test).stringValue
    def attTokens(attName: String) = attValue(attName).tokenizeToSet
    def attTokens(attName: QName)  = attValue(attName).tokenizeToSet
    def attClasses = attTokens("class")
    def id = attValue("id")
    def hasId = att("id").nonEmpty && attValue("id").nonAllBlank
    def hasAtt(attName: String) = att(attName).nonEmpty
    def hasAtt(attName: QName) = att(attName).nonEmpty

    def attOpt(attName: String): Option[NodeInfo] = /@(attName) match {
      case Seq() => None
      case s     => s.headOption
    }

    def attOpt(attName: QName): Option[NodeInfo] = /@(attName) match {
      case Seq() => None
      case s     => s.headOption
    }

    def attValueOpt(attName: String) = /@(attName) match {
      case LazyList() => None
      case s          => Some(s.stringValue)
    }

    def attValueOpt(attName: QName) = /@(attName) match {
      case LazyList() => None
      case s          => Some(s.stringValue)
    }

    def attValueOpt(test: Test) = /@(test) match {
      case LazyList() => None
      case s          => Some(s.stringValue)
    }

    def attValueNonBlankOpt(attName: String) = /@(attName) match {
      case LazyList() => None
      case s          => Some(s.stringValue) filter (_.nonAllBlank)
    }

    def attValueNonBlankOrThrow(attName: String): String =
      attValueNonBlankOpt(attName) getOrElse
        (throw new IllegalArgumentException(s"attribute `$attName` is required on element `$name`"))


    def elemValue(elemName: String) = /(elemName).stringValue
    def elemValue(elemName: QName)  = /(elemName).stringValue

    def elemValueOpt(elemName: String) = /(elemName) match {
      case LazyList() => None
      case s          => Some(s.stringValue)
    }

    def elemValueOpt(elemName: QName) = /(elemName) match {
      case LazyList() => None
      case s          => Some(s.stringValue)
    }

    def elemWithLangOpt(elemName: QName, lang: String): Option[NodeInfo] =
      /(elemName) find { _ attValueOpt "*:lang" contains lang }

    def idOpt = attValueOpt("id")

    def self(test: Test) = find(AxisInfo.SELF, test)
    def parent(test: Test) = find(AxisInfo.PARENT, test)

    def parentOption: Option[NodeInfo] = Option(nodeInfo.getParent)
    def parentUnsafe: NodeInfo = parentOption getOrElse (throw new NoSuchElementException)

    def ancestor(test: Test): NodeColl = find(AxisInfo.ANCESTOR, test)
    def ancestorOrSelf (test: Test): NodeColl = find(AxisInfo.ANCESTOR_OR_SELF, test)
    def descendant(test: Test): NodeColl = find(AxisInfo.DESCENDANT, test)
    def descendantOrSelf(test: Test): NodeColl = find(AxisInfo.DESCENDANT_OR_SELF, test)

    def preceding(test: Test): NodeColl = find(AxisInfo.PRECEDING, test) // TODO: use Type/NODE?
    def following(test: Test): NodeColl = find(AxisInfo.FOLLOWING, test) // TODO: use Type/NODE?

    def precedingSibling(test: Test): NodeColl = find(AxisInfo.PRECEDING_SIBLING, test) // TODO: use Type/NODE?
    def followingSibling(test: Test): NodeColl = find(AxisInfo.FOLLOWING_SIBLING, test) // TODO: use Type/NODE?
    def sibling(test: Test): NodeColl = precedingSibling(test) ++ followingSibling(test)

    def namespaces        = find(AxisInfo.NAMESPACE, AnyTest)
    def namespaceMappings = namespaces map (n => n.getLocalPart -> n.getStringValue)

    def prefixesForURI(uri: String) = prefixesForURIImpl(uri, this)
    def nonEmptyPrefixesForURI(uri: String) = prefixesForURI(uri) filter (_ != "")

    def precedingElement = nodeInfo precedingSibling * headOption
    def followingElement = nodeInfo followingSibling * headOption

    def prefix       = nodeInfo.getPrefix

    // Like XPath local-name(), name(), namespace-uri(), resolve-QName()
    def localname: String = nodeInfo.getLocalPart
    def name     : String = nodeInfo.getDisplayName

    def namespaceURI: String = {
      val uri = nodeInfo.getURI
      if (uri eq null) "" else uri
    }

    private def resolveStructuredQName(lexicalQName: String): StructuredQName =
      StructuredQName.fromLexicalQName(lexicalQName, useDefault = true, allowEQName = false, nodeInfo.getAllNamespaces)

    def resolveURIQualifiedName(lexicalQName: String): URIQualifiedName = {
      val structuredQName = resolveStructuredQName(lexicalQName)
      URIQualifiedName(structuredQName.getURI, structuredQName.getLocalPart)
    }

    def resolveQName(lexicalQName: String): QName = {
      val structuredQName = resolveStructuredQName(lexicalQName)
      QName(structuredQName.getLocalPart, structuredQName.getPrefix, structuredQName.getURI)
    }

    // Return a qualified name as a (namespace uri, local name) pair
    // NOTE: The "URI qualified name" terminology comes from XPath 3:
    // ([`URIQualifiedName`](https://www.w3.org/TR/xpath-31/#doc-xpath31-URIQualifiedName)).
    def uriQualifiedName: URIQualifiedName = URIQualifiedName(nodeInfo.getURI, nodeInfo.getLocalPart)

    def stringValue: String = nodeInfo.getStringValue

    def hasIdValue(id: String): Boolean = nodeInfo.id == id

    def isAttribute          : Boolean = nodeInfo.getNodeKind.toShort == Type.ATTRIBUTE
    def isElement            : Boolean = nodeInfo.getNodeKind.toShort == Type.ELEMENT
    def isElementOrAttribute : Boolean = ElementOrAttribute(nodeInfo.getNodeKind.toShort)
    def isDocument           : Boolean = nodeInfo.getNodeKind.toShort == Type.DOCUMENT

    // Whether the given node has at least one child element
    def hasChildElement      : Boolean = nodeInfo child * nonEmpty

    // True if the node is an element, doesn't have child elements, and has at least one non-empty child text node,
    // NOTE: This ignores PI and comment nodes
    def hasSimpleContent: Boolean =
      nodeInfo.isElement && ! nodeInfo.hasChildElement && hasNonEmptyChildNode

    // True if the node is an element, has at least one child element, and has at least one non-empty child text node
    // NOTE: This ignores PI and comment nodes
    def hasMixedContent: Boolean =
      nodeInfo.isElement && nodeInfo.hasChildElement && hasNonEmptyChildNode

    // True if the node is an element and has no children nodes
    // NOTE: This ignores PI and comment nodes
    def hasEmptyContent: Boolean =
      nodeInfo.isElement && (nodeInfo child Node isEmpty)

    // True if the node is an element, has at least one child element, and doesn't have non-empty child text nodes
    // NOTE: This ignores PI and comment nodes
    def hasElementOnlyContent(nodeInfo: NodeInfo): Boolean =
      nodeInfo.isElement && nodeInfo.hasChildElement && ! hasNonEmptyChildNode

    // Whether the node is an element and "supports" simple content, that is doesn't have child elements
    // NOTE: This ignores PI and comment nodes
    def supportsSimpleContent: Boolean =
      nodeInfo.isElement && ! nodeInfo.hasChildElement

    private def hasNonEmptyChildNode: Boolean =
      nodeInfo child Text exists (_.stringValue.nonEmpty)

    private def find(axisNumber: Int, test: Test): NodeColl =
      Implicits.asScalaIterator(nodeInfo.iterateAxis(axisNumber, test.test(nodeInfo)))
        .asInstanceOf[Iterator[NodeInfo]] // we know the result contains only `NodeInfo`, but ouch, this is a cast!
        .to(LazyList)
  }

  // Operations on sequences of NodeInfo
  implicit class NodeInfoSeqOps(private val _seq: Iterable[NodeInfo]) extends AnyVal {

    @inline
    private def lazyList = _seq.to(LazyList)

    // Semantic is the same as XPath: at least one value must match
    def ===(s: String) = lazyList exists (_ === s)
    def !==(s: String) = ! ===(s)

    def /(test: Test): NodeColl = lazyList flatMap (_ / test)

    def /@(attName: String): NodeColl = lazyList flatMap (_ /@ attName)
    def /@(attName: QName): NodeColl = lazyList flatMap (_ /@ attName)
    def /@(attName: (String, String)): NodeColl = lazyList flatMap (_ /@ attName)
    def /@(test: Test): NodeColl = lazyList flatMap (_ /@ test)

    def elemWithLangOpt(elemName: QName, lang: String): Option[NodeInfo] =
      lazyList flatMap (_.elemWithLangOpt(elemName, lang)) headOption

    def ids: Seq[String] = lazyList flatMap (_.idOpt)

    def namespaceNodes: NodeColl = lazyList flatMap (_.namespaceNodes)

    def att(attName: String)      = /@(attName)
    def att(test: Test)           = /@(test)
    def child(test: Test)         = /(test)
    def firstChildOpt(test: Test) = /(test).headOption
    def lastChildOpt(test: Test)  = /(test).lastOption

    def attValue(attName: String) = /@(attName) map (_.stringValue)
    def attValue(attName: QName)  = /@(attName) map (_.stringValue)

    def self(test: Test) = lazyList flatMap (_ self test)
    def parent(test: Test) = lazyList flatMap (_ parent test)

    def ancestor(test: Test): NodeColl = lazyList flatMap (_ ancestor test)
    def ancestorOrSelf (test: Test): NodeColl = lazyList flatMap (_ ancestorOrSelf test)
    def descendant(test: Test): NodeColl = lazyList flatMap (_ descendant test)
    def descendantOrSelf(test: Test): NodeColl = lazyList flatMap (_ descendantOrSelf test)

    def preceding(test: Test): NodeColl = lazyList flatMap (_ preceding test)
    def following(test: Test): NodeColl = lazyList flatMap (_ following test)

    def precedingSibling(test: Test) = lazyList flatMap (_ precedingSibling test)
    def followingSibling(test: Test) = lazyList flatMap (_ followingSibling test)
    def sibling(test: Test) = lazyList flatMap (_ sibling test)

    // The string value is not defined on sequences. We take the first value, for convenience, like in XPath 2.0's
    // XPath 1.0 compatibility mode.
    def stringValue = lazyList match {
      case LazyList()             => ""
      case LazyList(nodeInfo, _*) => nodeInfo.getStringValue
    }

    def effectiveBooleanValue: Boolean =
      SaxonUtils.effectiveBooleanValue(new ListIterator(lazyList.asJava))
  }

  private object Private {

    val ElementOrAttribute = Set(Type.ELEMENT, Type.ATTRIBUTE)

    class NodeKindTestBase(nodeKind: Short) extends Test {
      private val test = NodeKindTest.makeNodeKindTest(nodeKind)
      def test(nodeInfo: NodeInfo): NodeTest = test
    }

    // HACK because of compiler limitation:
    // "implementation restriction: nested class is not allowed in value class
    //  This restriction is planned to be removed in subsequent releases."
    def prefixesForURIImpl(uri: String, ops: NodeInfoOps): LazyList[String] =
      ops.namespaceMappings collect { case (prefix, `uri`) => prefix }
  }
}
