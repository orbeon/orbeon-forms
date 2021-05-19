package org.orbeon.oxf.xforms.function

import org.orbeon.oxf.json.Converter
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsCrossPlatformSupport


class XFormsParse extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): om.NodeInfo = {

    implicit val ctx = xpathContext

    val inputString  = stringArgument(0)
    val formatTokens = stringArgumentOpt(1).getOrElse("xml").splitTo[Set]()

    if (formatTokens("xml"))
      XFormsCrossPlatformSupport.stringToTinyTree(
        XPath.GlobalConfiguration,
        inputString,
        handleXInclude = false,
        handleLexical  = true
      ).rootElement
    else if (formatTokens("json"))
      Converter.jsonStringToXmlDoc(inputString).rootElement
    else
      throw new IllegalArgumentException(formatTokens.mkString(" "))
  }
}