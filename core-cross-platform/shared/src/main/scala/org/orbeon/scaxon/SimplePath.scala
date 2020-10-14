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

import org.orbeon.dom._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr.Token
import org.orbeon.saxon.om._
import org.orbeon.saxon.pattern._
import org.orbeon.saxon.tinytree.TinyTree

import scala.jdk.CollectionConverters._


// The idea of this is to provide simple path navigation operations on Saxon nodes, without creating
// full XPath expressions, as those need to be compiled, cached, and run, and can be very complex.
object SimplePath {

  import Private._

  case class URIQualifiedName(uri: String, localName: String) {
    require(Name10Checker.getInstance.isValidNCName(localName))
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
    def test(nodeInfo: NodeInfo) = AnyNodeTest.getInstance
  }

  class NodeLocalNameTest(name: String, nodeKind: Option[Int] = None) extends Test {
    override def test(nodeInfo: NodeInfo) = {

      val pool = nodeInfo.getNamePool

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

    def /(test: Test) = find(Axis.CHILD, test)

    def firstChildOpt(test: Test) = /(test).headOption
    def lastChildOpt(test: Test)  = /(test).lastOption

    // Return an element's attributes
    // Q: Should functions taking a String match on no namespace only?
    // Q: If the QName is specified, zero or one attribute can be returned. Should return `Option`?
    def /@(attName: String): Seq[NodeInfo] = /@(new NodeLocalNameTest(attName, Some(Type.ATTRIBUTE)))
    def /@(attName: QName): Seq[NodeInfo] = /@(new NodeQNameTest((attName.namespace.uri, attName.localName), Some(Type.ATTRIBUTE)))
    def /@(attName: (String, String)): Seq[NodeInfo] = /@(new NodeQNameTest(attName, Some(Type.ATTRIBUTE)))
    def /@(test: Test): Seq[NodeInfo] = find(Axis.ATTRIBUTE, test)

    def namespaceNodes: Seq[NodeInfo] = find(Axis.NAMESPACE, AnyTest)

    // The following doesn't work right now because the DESCENDANT axis doesn't include attributes
//        def //@(attName: String): Seq[NodeInfo] = //@(new NodeLocalNameTest(attName, Some(Type.ATTRIBUTE)))
//        def //@(attName: QName): Seq[NodeInfo] = //@(new NodeQNameTest((attName.getNamespaceURI, attName.getName), Some(Type.ATTRIBUTE)))
//        def //@(attName: (String, String)): Seq[NodeInfo] = //@(new NodeQNameTest(attName, Some(Type.ATTRIBUTE)))
//        def //@(test: Test): Seq[NodeInfo] = find(Axis.DESCENDANT, test)

    def root = nodeInfo.getDocumentRoot
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
    def hasId = att("id").nonEmpty && attValue("id").trimAllToEmpty != ""
    def hasAtt(attName: String) = att(attName).nonEmpty
    def hasAtt(attName: QName) = att(attName).nonEmpty

    def attValueOpt(attName: String) = /@(attName) match {
      case Seq() => None
      case s     => Some(s.stringValue)
    }

    def attValueOpt(attName: QName) = /@(attName) match {
      case Seq() => None
      case s     => Some(s.stringValue)
    }

    def attValueOpt(test: Test) = /@(test) match {
      case Seq() => None
      case s     => Some(s.stringValue)
    }

    def attValueNonBlankOpt(attName: String) = /@(attName) match {
      case Seq() => None
      case s     => Some(s.stringValue) filter (_.nonAllBlank)
    }

    def attValueNonBlankOrThrow(attName: String): String =
      attValueNonBlankOpt(attName) getOrElse
        (throw new IllegalArgumentException(s"attribute `$attName` is required on element `$name`"))


    def elemValue(elemName: String) = /(elemName).stringValue
    def elemValue(elemName: QName)  = /(elemName).stringValue

    def elemValueOpt(elemName: String) = /(elemName) match {
      case Seq() => None
      case s     => Some(s.stringValue)
    }

    def elemValueOpt(elemName: QName) = /(elemName) match {
      case Seq() => None
      case s     => Some(s.stringValue)
    }

    def elemWithLangOpt(elemName: QName, lang: String): Option[NodeInfo] =
      /(elemName) find { _ attValueOpt "*:lang" contains lang }

    def idOpt = attValueOpt("id")

    def self(test: Test) = find(Axis.SELF, test)
    def parent(test: Test) = find(Axis.PARENT, test)

    def parentOption: Option[NodeInfo] = Option(nodeInfo.getParent)
    def parentUnsafe: NodeInfo = parentOption getOrElse (throw new NoSuchElementException)

    def ancestor(test: Test): Seq[NodeInfo] = find(Axis.ANCESTOR, test)
    def ancestorOrSelf (test: Test): Seq[NodeInfo] = find(Axis.ANCESTOR_OR_SELF, test)
    def descendant(test: Test): Seq[NodeInfo] = find(Axis.DESCENDANT, test)
    def descendantOrSelf(test: Test): Seq[NodeInfo] = find(Axis.DESCENDANT_OR_SELF, test)

    def preceding(test: Test): Seq[NodeInfo] = find(Axis.PRECEDING, test) // TODO: use Type/NODE?
    def following(test: Test): Seq[NodeInfo] = find(Axis.FOLLOWING, test) // TODO: use Type/NODE?

    def precedingSibling(test: Test): Seq[NodeInfo] = find(Axis.PRECEDING_SIBLING, test) // TODO: use Type/NODE?
    def followingSibling(test: Test): Seq[NodeInfo] = find(Axis.FOLLOWING_SIBLING, test) // TODO: use Type/NODE?
    def sibling(test: Test): Seq[NodeInfo] = precedingSibling(test) ++ followingSibling(test)

    def namespaces        = find(Axis.NAMESPACE, AnyTest)
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

    private def resolveStructuredQName(lexicalQName: String): StructuredQName = {

      val checker = Name10Checker.getInstance
      val resolver = new InscopeNamespaceResolver(nodeInfo)

      StructuredQName.fromLexicalQName(lexicalQName, true, checker, resolver)
    }

    def resolveURIQualifiedName(lexicalQName: String): URIQualifiedName = {
      val structuredQName = resolveStructuredQName(lexicalQName)
      URIQualifiedName(structuredQName.getNamespaceURI, structuredQName.getLocalName)
    }

    def resolveQName(lexicalQName: String): QName = {
      val structuredQName = resolveStructuredQName(lexicalQName)
      QName(structuredQName.getLocalName, structuredQName.getPrefix, structuredQName.getNamespaceURI)
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

    private def find(axisNumber: Byte, test: Test): Seq[NodeInfo] = {
      // We know the result contains only NodeInfo, but ouch, this is a cast!
      val iterator = Implicits.asScalaIterator(nodeInfo.iterateAxis(axisNumber, test.test(nodeInfo))).asInstanceOf[Iterator[NodeInfo]]
      // FIXME: We should instead return the Iterator. This means all callers need to be adjusted.
      iterator.toStream
    }
  }

  // Operations on sequences of NodeInfo
  implicit class NodeInfoSeqOps(private val seq: Seq[NodeInfo]) extends AnyVal {

    // Semantic is the same as XPath: at least one value must match
    def ===(s: String) = seq exists (_ === s)
    def !==(s: String) = ! ===(s)

    def /(test: Test): Seq[NodeInfo] = seq flatMap (_ / test)

    def /@(attName: String): Seq[NodeInfo] = seq flatMap (_ /@ attName)
    def /@(attName: QName): Seq[NodeInfo] = seq flatMap (_ /@ attName)
    def /@(attName: (String, String)): Seq[NodeInfo] = seq flatMap (_ /@ attName)
    def /@(test: Test): Seq[NodeInfo] = seq flatMap (_ /@ test)

    def elemWithLangOpt(elemName: QName, lang: String): Option[NodeInfo] =
      seq flatMap (_.elemWithLangOpt(elemName, lang)) headOption

    def ids: Seq[String] = seq flatMap (_.idOpt)

    def namespaceNodes: Seq[NodeInfo] = seq flatMap (_.namespaceNodes)

    def att(attName: String)      = /@(attName)
    def att(test: Test)           = /@(test)
    def child(test: Test)         = /(test)
    def firstChildOpt(test: Test) = /(test).headOption
    def lastChildOpt(test: Test)  = /(test).lastOption

    def attValue(attName: String) = /@(attName) map (_.stringValue)
    def attValue(attName: QName)  = /@(attName) map (_.stringValue)

    def self(test: Test) = seq flatMap (_ self test)
    def parent(test: Test) = seq flatMap (_ parent test)

    def ancestor(test: Test): Seq[NodeInfo] = seq flatMap (_ ancestor test)
    def ancestorOrSelf (test: Test): Seq[NodeInfo] = seq flatMap (_ ancestorOrSelf test)
    def descendant(test: Test): Seq[NodeInfo] = seq flatMap (_ descendant test)
    def descendantOrSelf(test: Test): Seq[NodeInfo] = seq flatMap (_ descendantOrSelf test)

    def preceding(test: Test): Seq[NodeInfo] = seq flatMap (_ preceding test)
    def following(test: Test): Seq[NodeInfo] = seq flatMap (_ following test)

    def precedingSibling(test: Test) = seq flatMap (_ precedingSibling test)
    def followingSibling(test: Test) = seq flatMap (_ followingSibling test)
    def sibling(test: Test) = seq flatMap (_ sibling test)

    // The string value is not defined on sequences. We take the first value, for convenience, like in XPath 2.0's
    // XPath 1.0 compatibility mode.
    def stringValue = seq match {
      case Seq() => ""
      case Seq(nodeInfo, _*) => nodeInfo.getStringValue
    }

    def effectiveBooleanValue: Boolean =
      SaxonUtils.effectiveBooleanValue(new ListIterator(seq.asJava))
  }

  private object Private {

    val ElementOrAttribute = Set(Type.ELEMENT, Type.ATTRIBUTE)

    class LocalNameOnlyTest(pool: NamePool, localName: String) extends NodeTest {

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

    class NameOnlyTest(pool: NamePool, uri: String, localName: String) extends NodeTest {

      private val fingerprint = pool.allocate("", uri, localName) & NamePool.FP_MASK

      // Matches the name only, but not the node kind
      def matches(nodeKind: Int, fingerprint: Int, annotation: Int) =
        ElementOrAttribute(nodeKind.toShort) && (fingerprint & NamePool.FP_MASK) == this.fingerprint

      override def matches(tree: TinyTree, nodeNr: Int) =
        ElementOrAttribute(tree.getNodeKind(nodeNr).toShort) && (tree.getNameCode(nodeNr) & NamePool.FP_MASK) == this.fingerprint

      override def matches(node: NodeInfo) =
        ElementOrAttribute(node.getNodeKind.toShort) && (
          node match {
            case _: FingerprintedNode => node.getFingerprint == fingerprint
            case _                    => localName == node.getLocalPart && uri == node.getURI
          }
        )

      override def getNodeKindMask = 1 << Type.ELEMENT | 1 << Type.ATTRIBUTE
      override def toString = pool.getClarkName(fingerprint)

      def getDefaultPriority = -0.25 // probably not used right now
    }

    class NodeKindTestBase(nodeKind: Short) extends Test {
      private val test = NodeKindTest.makeNodeKindTest(nodeKind)
      def test(nodeInfo: NodeInfo) = test
    }

    // HACK because of compiler limitation:
    // "implementation restriction: nested class is not allowed in value class
    //  This restriction is planned to be removed in subsequent releases."
    def prefixesForURIImpl(uri: String, ops: NodeInfoOps) =
      ops.namespaceMappings collect { case (prefix, `uri`) => prefix }
  }
}
