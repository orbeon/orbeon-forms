package org.orbeon.oxf.xml

import org.orbeon.dom.QName
import org.orbeon.oxf.util.StaticXPath.{AxisType, SaxonConfiguration, ValueRepresentationType}
import org.orbeon.saxon.expr.{Expression, FunctionCall}
import org.orbeon.saxon.om
import org.orbeon.saxon.value.{AtomicValue, StringValue}

import scala.util.Try


// Version of `StringValue` which supports `equals()` (universal equality).
// Saxon throws on `equals()` to make a point that a collation should be used for `StringValue` comparison.
// Here, we don't really care about equality, but we want to implement `equals()` as e.g. Jetty calls `equals()` on
// objects stored into the session. See:
// http://forge.ow2.org/tracker/index.php?func=detail&aid=315528&group_id=168&atid=350207
class StringValueWithEquals(value: CharSequence) extends StringValue(value) {
  override def equals(other: Any): Boolean = {
    // Compare the CharSequence
    other.isInstanceOf[StringValue] && getStringValueCS == other.asInstanceOf[StringValue].getStringValueCS
  }

  override def hashCode(): Int = value.hashCode()
}

trait SaxonUtilsTrait {

  // Duplicate constant from Saxon because they are in different packages between Saxon versions
  object NodeType {
    val Element              : Short = 1
    val Attribute            : Short = 2
    val Text                 : Short = 3
    val ProcessingInstruction: Short = 7
    val Comment              : Short = 8
    val Document             : Short = 9
    val Namespace            : Short = 13
  }

  def effectiveBooleanValue(iterator: om.SequenceIterator): Boolean
  def iterateExpressionTree(e: Expression): Iterator[Expression]
  def iterateExternalVariableReferences(expr: Expression): Iterator[String]
  def containsFnWithParam(expr: Expression, fnName: om.StructuredQName, arity: Int, oldName: String, argPos: Int): Boolean

  // Parse the given qualified name and return the separated prefix and local name
  def parseQName(lexicalQName: String): (String, String)

  // Make an NCName out of a non-blank string
  // Any characters that do not belong in an NCName are converted to `_`.
  // If `keepFirstIfPossible == true`, prepend `_` if first character is allowed within NCName and keep first character.
  //@XPathFunction
  def makeNCName(name: String, keepFirstIfPossible: Boolean): String

  // 2020-12-05:
  //
  // - Called only from `XFormsVariableControl`
  // - With Saxon 10, this will be one of :
  //   - `SequenceExtent` reduced to `AtomicValue`, `NodeInfo`, or `Function` (unsupported yet below)
  //   - `SequenceExtent` with more than one item
  //   - `EmptySequence`
  // - Also, if a sequence, it's already reduced.
  //
  def compareValueRepresentations(valueRepr1: ValueRepresentationType, valueRepr2: ValueRepresentationType): Boolean

  // Whether two sequences contain identical items
  def compareItemSeqs(nodeset1: Iterable[om.Item], nodeset2: Iterable[om.Item]): Boolean

  def compareItems(item1: om.Item, item2: om.Item): Boolean

  def deepCompare(
    config                     : SaxonConfiguration,
    it1                        : Iterator[om.Item],
    it2                        : Iterator[om.Item],
    excludeWhitespaceTextNodes : Boolean
  ): Boolean

  // These are here to abstract some differences between Saxon 9 and 10
  def getStructuredQNameLocalPart(qName: om.StructuredQName): String
  def getStructuredQNameURI      (qName: om.StructuredQName): String

  def itemIterator   (i: om.Item)                : om.SequenceIterator
  def listIterator   (s: collection.Seq[om.Item]): om.SequenceIterator
  def emptyIterator                              : om.SequenceIterator
  def valueAsIterator(v: ValueRepresentationType): om.SequenceIterator

  def selectID(node: om.NodeInfo, id: String): Option[om.NodeInfo]
  def newMapItem(map: Map[AtomicValue, ValueRepresentationType]): om.Item

  // Types are different between Saxon 9 and 10
//  def newArrayItem(v: Vector[ValueRepresentationType]): om.Item

  // We pass a language in the format returned by `xxf:lang()`, which is a BCP47 tag, e.g. zh-Hans, while Saxon's
  // numberer resolution takes only the letters from the language code, e.g. zhHans, so implementations need to do
  // the conversion.
  def hasXPathNumberer(lang: String): Boolean
  def isValidNCName   (name: String): Boolean
  def isValidNmtoken  (name: String): Boolean

  val ChildAxisInfo: AxisType
  val AttributeAxisInfo: AxisType

  // Given a display path, get an internal path (for unit tests).
  def getInternalPathForDisplayPath(namespaces: Map[String, String], path: String): String

  def attCompare(boundNodeOpt: Option[om.NodeInfo], att: om.NodeInfo): Boolean

  def xsiType(elem: om.NodeInfo): Option[QName]

  def convertType(
    value               : StringValue,
    newTypeLocalName    : String,
    config              : SaxonConfiguration
  ): Try[Option[AtomicValue]]

  // Create a fingerprinted path of the form: `3142/1425/@1232` from a node.
  def createFingerprintedPath(node: om.NodeInfo): String

  def fixStringValue[V <: om.Item](item: V): V =
    item match {
      case v: StringValue => new StringValueWithEquals(v.getStringValueCS).asInstanceOf[V] // we know it's ok...
      case v              => v
    }

  def iterateFunctions(expr: Expression, fnName: om.StructuredQName): Iterator[FunctionCall] =
    iterateExpressionTree(expr) collect {
      case fn: FunctionCall if fn.getFunctionName == fnName => fn
    }

  protected def findNodePosition(node: om.NodeInfo): Int

  def buildNodePath(node: om.NodeInfo): List[String] = {

    def buildOne(node: om.NodeInfo): List[String] = {

      def buildNameTest(node: om.NodeInfo): String =
        if (node.getURI == "")
          node.getLocalPart
        else
          s"*:${node.getLocalPart}[namespace-uri() = '${node.getURI}']"

      if (node ne null) {
        val parent = node.getParent
        node.getNodeKind match {
          case NodeType.Document =>
            Nil
          case NodeType.Element =>
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
          case NodeType.Attribute =>
            ("@" + buildNameTest(node)) :: buildOne(parent)
          case NodeType.Text =>
            ("text()[" + findNodePosition(node) + ']') :: buildOne(parent)
          case NodeType.Comment =>
            "comment()[" + findNodePosition(node) + ']' :: buildOne(parent)
          case NodeType.ProcessingInstruction =>
            ("processing-instruction()[" + findNodePosition(node) + ']') :: buildOne(parent)
          case NodeType.Namespace =>
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

  // Return `true` iif `potentialAncestor` is an ancestor of `potentialDescendant`
  def isFirstNodeAncestorOfSecondNode(
    potentialAncestor   : om.NodeInfo,
    potentialDescendant : om.NodeInfo,
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
}
