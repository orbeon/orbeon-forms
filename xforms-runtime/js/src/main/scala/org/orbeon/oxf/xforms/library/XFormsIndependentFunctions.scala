package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.json.Converter
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsCrossPlatformSupport


trait XFormsIndependentFunctions extends OrbeonFunctionLibrary {

  @XPathFunction
  def parse(inputString: String, format: String = "xml"): om.NodeInfo = {

    val formatTokens = format.splitTo[Set]()

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
      throw new IllegalArgumentException(format)
  }
}
