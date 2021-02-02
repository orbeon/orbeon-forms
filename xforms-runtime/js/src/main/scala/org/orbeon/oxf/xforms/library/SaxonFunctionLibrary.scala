package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsId

import scala.jdk.CollectionConverters._


object SaxonFunctionLibrary extends OrbeonFunctionLibrary {

  lazy val namespaces = List("http://saxon.sf.net/" -> "saxon")

  @XPathFunction
  def evaluate(expr: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[om.Item] = {

    val xfcd = xfc.containingDocument
    val elem = xfcd.staticOps.findControlAnalysis(XFormsId.getPrefixedId(xfc.sourceEffectiveId)) getOrElse
      (throw new IllegalStateException(xfc.sourceEffectiveId))

    XPathCache.evaluateKeepItems(
      contextItems       = List(xpc.getContextItem).asJava,
      contextPosition    = 1,
      xpathString        = expr,
      namespaceMapping   = elem.namespaceMapping,
      variableToValueMap = xfc.bindingContext.getInScopeVariables,
      functionLibrary    = xfcd.functionLibrary,
      functionContext    = xfc,
      baseURI            = null,
      locationData       = elem.locationData,
      reporter           = null
    )
  }

}
