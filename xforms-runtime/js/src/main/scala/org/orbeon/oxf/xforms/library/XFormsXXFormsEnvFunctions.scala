package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.xforms.NodeInfoFactory.{attributeInfo, elementInfo}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.value.{AtomicValue, StringValue}


trait XFormsXXFormsEnvFunctions extends OrbeonFunctionLibrary {

  @XPathFunction
  def element(name: AtomicValue, content: Iterable[om.Item] = Iterable.empty)(implicit xpc: XPathContext): om.NodeInfo =
    elementInfo(XFormsFunction.getQNameFromItem(name), content.toList)

  @XPathFunction
  def attribute(name: AtomicValue, value: AtomicValue = StringValue.EMPTY_STRING)(implicit xpc: XPathContext): om.NodeInfo =
    attributeInfo(XFormsFunction.getQNameFromItem(name), value.getStringValue)

//    Fun("case", classOf[XFormsCase], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//  }
}
