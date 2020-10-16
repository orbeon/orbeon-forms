/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import java.net.URI

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr.{Expression, ExpressionTool}
import org.orbeon.saxon.functions.DeepEqual
import org.orbeon.saxon.om._
import org.orbeon.saxon.pattern.{NameTest, NodeKindTest}
import org.orbeon.saxon.sort.{CodepointCollator, GenericAtomicComparer}
import org.orbeon.saxon.value._
import org.orbeon.saxon.xqj.{SaxonXQDataFactory, StandardObjectConverter}
import org.orbeon.saxon.{Configuration, om}
import org.orbeon.scaxon.Implicits

import scala.jdk.CollectionConverters._


object SaxonUtils {

  // Version of StringValue which supports equals().
  // Saxon throws on equals() to make a point that a collation should be used for StringValue comparison.
  // Here, we don't really care about equality, but we want to implement equals() as e.g. Jetty calls equals() on
  // objects stored into the session. See:
  // http://forge.ow2.org/tracker/index.php?func=detail&aid=315528&group_id=168&atid=350207
  class StringValueWithEquals(value: CharSequence) extends StringValue(value) {
    override def equals(other: Any): Boolean = {
      // Compare the CharSequence
      other.isInstanceOf[StringValue] && getStringValueCS == other.asInstanceOf[StringValue].getStringValueCS
    }

    override def hashCode(): Int = value.hashCode()
  }

  def fixStringValue[V <: Item](item: V): V =
    item match {
      case v: StringValue => new StringValueWithEquals(v.getStringValueCS).asInstanceOf[V] // we know it's ok...
      case v              => v
    }

  // Effective boolean value of the iterator
  def effectiveBooleanValue(iterator: SequenceIterator): Boolean =
    ExpressionTool.effectiveBooleanValue(iterator)

  def iterateExpressionTree(e: Expression): Iterator[Expression] =
    Iterator(e) ++
      (e.iterateSubExpressions.asScala.asInstanceOf[Iterator[Expression]] flatMap iterateExpressionTree)

  // Parse the given qualified name and return the separated prefix and local name
  def parseQName(lexicalQName: String): (String, String) = {
    val checker = Name10Checker.getInstance
    val parts   = checker.getQNameParts(lexicalQName)

    (parts(0), parts(1))
  }

  // Make an NCName out of a non-blank string
  // Any characters that do not belong in an NCName are converted to `_`.
  // If `keepFirstIfPossible == true`, prepend `_` if first character is allowed within NCName and keep first character.
  //@XPathFunction
  def makeNCName(name: String, keepFirstIfPossible: Boolean): String = {

    require(name.nonAllBlank, "name must not be blank or empty")

    val name10Checker = Name10Checker.getInstance
    if (name10Checker.isValidNCName(name)) {
      name
    } else {
      val sb = new StringBuilder
      val start = name.charAt(0)

      if (name10Checker.isNCNameStartChar(start))
        sb.append(start)
      else if (keepFirstIfPossible && name10Checker.isNCNameChar(start)) {
        sb.append('_')
        sb.append(start)
      } else
        sb.append('_')

      for (i <- 1 until name.length) {
        val ch = name.charAt(i)
        sb.append(if (name10Checker.isNCNameChar(ch)) ch else '_')
      }
      sb.toString
    }
  }

  def compareValueRepresentations(valueRepr1: ValueRepresentation, valueRepr2: ValueRepresentation): Boolean =
    (valueRepr1, valueRepr2) match {
      // Ideally we wouldn't support null here (XFormsVariableControl passes null)
      case (null,             null)            => true
      case (null,             _)               => false
      case (_,                null)            => false
      case (v1: Value, v2: Value)              => compareValues(v1, v2)
      case (v1: NodeInfo, v2: NodeInfo)        => v1 == v2
      // 2014-08-18: Checked Saxon class hierarchy
      // Saxon type hierarchy is closed (ValueRepresentation = NodeInfo | Value)
      case _                                   => throw new IllegalStateException
    }

  def compareValues(value1: Value, value2: Value): Boolean = {
    val iter1 = Implicits.asScalaIterator(value1.iterate)
    val iter2 = Implicits.asScalaIterator(value2.iterate)

    iter1.zipAll(iter2, null, null) forall (compareItems _).tupled
  }

  // Whether two sequences contain identical items
  def compareItemSeqs(nodeset1: Seq[Item], nodeset2: Seq[Item]): Boolean =
    nodeset1.size == nodeset2.size &&
      (nodeset1.iterator.zip(nodeset2.iterator) forall (compareItems _).tupled)

  def compareItems(item1: Item, item2: Item): Boolean =
    (item1, item2) match {
      // We probably shouldn't support null at all here!
      case (null,             null)            => true
      case (null,             _)               => false
      case (_,                null)            => false
      // StringValue.equals() throws (Saxon equality requires a collation)
      case (v1: StringValue,  v2: StringValue) => v1.codepointEquals(v2)
      case (v1: StringValue,  v2 )             => false
      case (v1,               v2: StringValue) => false
      // AtomicValue.equals() may throw (Saxon changes the standard equals() contract)
      case (v1: AtomicValue,  v2: AtomicValue) => v1 == v2
      case (v1,               v2: AtomicValue) => false
      case (v1: AtomicValue,  v2)              => false
      // NodeInfo
      case (v1: NodeInfo,     v2: NodeInfo)    => v1 == v2
      // 2014-08-18: Checked Saxon class hierarchy
      // Saxon type hierarchy is closed (Item = NodeInfo | AtomicValue)
      case _                                   => throw new IllegalStateException
    }

  def buildNodePath(node: NodeInfo): List[String] = {

    def findNodePosition(node: NodeInfo): Int = {

      val nodeTestForSameNode =
        node.getFingerprint match {
          case -1 => NodeKindTest.makeNodeKindTest(node.getNodeKind)
          case _  => new NameTest(node)
        }

      val precedingAxis =
        node.iterateAxis(Axis.PRECEDING_SIBLING, nodeTestForSameNode)

      var i: Int = 1
      while (precedingAxis.next ne null) {
        i += 1
      }
      i
    }

    def buildOne(node: NodeInfo): List[String] = {

      def buildNameTest(node: NodeInfo) =
        if (node.getURI == "")
          node.getLocalPart
        else
          s"*:${node.getLocalPart}[namespace-uri() = '${node.getURI}']"

      if (node ne null) {
        val parent = node.getParent
        node.getNodeKind match {
          case Type.DOCUMENT =>
            Nil
          case Type.ELEMENT =>
            if (parent eq null) {
              List(buildNameTest(node))
            } else {
              val pre = buildOne(parent)
              if (pre == Nil) {
                buildNameTest(node) :: pre
              } else {
                (buildNameTest(node) + '[' + findNodePosition(node) + ']') :: pre
              }
            }
          case Type.ATTRIBUTE =>
            ("@" + buildNameTest(node)) :: buildOne(parent)
          case Type.TEXT =>
            ("text()[" + findNodePosition(node) + ']') :: buildOne(parent)
          case Type.COMMENT =>
            "comment()[" + findNodePosition(node) + ']' :: buildOne(parent)
          case Type.PROCESSING_INSTRUCTION =>
            ("processing-instruction()[" + findNodePosition(node) + ']') :: buildOne(parent)
          case Type.NAMESPACE =>
            var test = node.getLocalPart
            if (test.isEmpty) {
              test = "*[not(local-name()]"
            }
            ("namespace::" + test) :: buildOne(parent)
          case _ =>
            throw new IllegalArgumentException
        }
      } else {
        throw new IllegalArgumentException
      }
    }

    buildOne(node).reverse
  }

  def convertJavaObjectToSaxonObject(o: Any): ValueRepresentation =
    o match {
      case v: ValueRepresentation => v
      case v: String              => new StringValue(v)
      case v: java.lang.Boolean   => BooleanValue.get(v)
      case v: java.lang.Integer   => new Int64Value(v.toLong)
      case v: java.lang.Float     => new FloatValue(v)
      case v: java.lang.Double    => new DoubleValue(v)
      case v: URI                 => new AnyURIValue(v.toString)
      case _                      => throw new OXFException(s"Invalid variable type: ${o.getClass}")
    }

  // Return `true` iif `potentialAncestor` is an ancestor of `potentialDescendant`
  def isFirstNodeAncestorOfSecondNode(
    potentialAncestor   : NodeInfo,
    potentialDescendant : NodeInfo,
    includeSelf         : Boolean
  ): Boolean = {
    var parent = if (includeSelf) potentialDescendant else potentialDescendant.getParent
    while (parent ne null) {
      if (parent.isSameNodeInfo(potentialAncestor))
        return true
      parent = parent.getParent
    }
    false
  }

  def deepCompare(
    config                     : Configuration,
    it1                        : Iterator[om.Item],
    it2                        : Iterator[om.Item],
    excludeWhitespaceTextNodes : Boolean
  ): Boolean = {

    // Do our own filtering of top-level items as Saxon's `DeepEqual` doesn't
    def filterWhitespaceNodes(item: om.Item) = item match {
      case n: om.NodeInfo => ! Whitespace.isWhite(n.getStringValueCS)
      case _ => true
    }

    DeepEqual.deepEquals(
      Implicits.asSequenceIterator(if (excludeWhitespaceTextNodes) it1 filter filterWhitespaceNodes else it1),
      Implicits.asSequenceIterator(if (excludeWhitespaceTextNodes) it2 filter filterWhitespaceNodes else it2),
      new GenericAtomicComparer(CodepointCollator.getInstance, config.getConversionContext),
      config,
      DeepEqual.INCLUDE_PREFIXES                  |
        DeepEqual.INCLUDE_COMMENTS                |
        DeepEqual.COMPARE_STRING_VALUES           |
        DeepEqual.INCLUDE_PROCESSING_INSTRUCTIONS |
        (if (excludeWhitespaceTextNodes) DeepEqual.EXCLUDE_WHITESPACE_TEXT_NODES else 0)
    )
  }
}
