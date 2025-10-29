package org.orbeon.saxon.function

import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xml.DefaultFunctionSupport
import org.orbeon.saxon.event.ComplexContentOutputter
import org.orbeon.saxon.expr.{Expression, ExpressionVisitor, XPathContext}
import org.orbeon.saxon.om.{Item, NamespaceConstant, StandardNames}
import org.orbeon.saxon.regex.RegularExpression
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.{Configuration, om}
import org.orbeon.scaxon.Implicits.*


class AnalyzeString extends DefaultFunctionSupport {

  import AnalyzeString.*

  private var staticBaseUri: String = _

  override def preEvaluate(visitor: ExpressionVisitor): Expression = {
    staticBaseUri = visitor.getStaticContext.getBaseURI
    super.preEvaluate(visitor)
  }

  override def evaluateItem(context: XPathContext): om.NodeInfo = {

    implicit val ctx: XPathContext = context

    val input = stringArgumentOpt(0).getOrElse("")
    val re    = getRegularExpression(arguments)
    val iter  = re.analyze(input)

    val builder = context.getController.makeBuilder
    val out     = new ComplexContentOutputter
    out.setReceiver(builder)
    builder.setBaseURI(staticBaseUri)
    out.open()
    out.startElement(resultName, StandardNames.XS_UNTYPED, 0, 0)
    out.startContent()
    var item: Item = null
    while ({
      item = iter.next()
      item
    } != null)
      if (iter.isMatching) {
        out.startElement(matchName, StandardNames.XS_UNTYPED, 0, 0)
        out.startContent()

        // NOTE: With this version of Saxon regex APIs, I don't think we can get strings outside the groups. So this
        // will work for `\s*([()^*/+-])\s*` if we don't care about knowing about the whitespace information, for
        // example, but it is not a fully-compliant implementation of `analyze-string()` and this should only be seen
        // as a temporary measure until we can bring XPath processors in sync.
        iter.getRegexGroupIterator.zipWithIndex.foreach { case (groupValue, index0) =>
          out.startElement(groupName, StandardNames.XS_UNTYPED, 0, 0)
          out.attribute(groupNrName, StandardNames.XS_UNTYPED_ATOMIC, (index0 + 1).toString, 0, 0)
          out.characters(groupValue.getStringValueCS, 0, 0)
          out.endElement()
        }
        out.endElement()
      } else {
        out.startElement(nonMatchName, StandardNames.XS_UNTYPED, 0, 0)
        out.startContent()
        out.characters(item.getStringValueCS, 0, 0)
        out.endElement()
      }
    out.endElement()
    out.close()
    builder.getCurrentRoot
  }

  private def getRegularExpression(args: Seq[Expression])(implicit ctx: XPathContext): RegularExpression = {
    val re = stringArgument(1)
    val flags = stringArgumentOpt(2).getOrElse("")
    val regex = Configuration.getPlatform.compileRegularExpression(re, Configuration.XML10, RegularExpression.XPATH_SYNTAX, flags)
    if (regex.matches(""))
      throw new XPathException(
        "The regular expression must not be one that matches a zero-length string",
        "FORX0003"
      )
    regex
  }
}

private object AnalyzeString {
  private val (
    resultName  : Int,
    nonMatchName: Int,
    matchName   : Int,
    groupName   : Int,
    groupNrName : Int
  ) = {

    val pool = StaticXPath.GlobalNamePool

    def fingerprintForFn(localname: String): Int =
      pool.allocate("", NamespaceConstant.FN, localname)

    (
      fingerprintForFn("analyze-string-result"),
      fingerprintForFn("non-match"),
      fingerprintForFn("match"),
      fingerprintForFn("group"),
      pool.allocate("", "", "nr")
    )
  }
}