package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.Item

import scala.jdk.CollectionConverters.*


object EvaluateSupport {

  def evaluateInContext(
    expr       : String,
    effectiveId: String
  )(implicit
    xpc        : XPathContext,
    xfc        : XFormsFunction.Context
  ): List[Item] = {

    val xfcd = xfc.containingDocument
    val contextObject =
      xfcd.findObjectByEffectiveId(effectiveId)
        .getOrElse(throw new IllegalArgumentException(effectiveId))

    val newXPc = XFormsFunction.Context(contextObject)

    XPathCache.evaluateKeepItems(
      contextItems       = List(xpc.getContextItem).asJava,
      contextPosition    = 1,
      xpathString        = expr,
      namespaceMapping   = contextObject.elementAnalysis.namespaceMapping,
      variableToValueMap = newXPc.bindingContext.getInScopeVariables,
      functionLibrary    = xfcd.functionLibrary,
      functionContext    = newXPc,
      baseURI            = null,
      locationData       = contextObject.elementAnalysis.locationData,
      reporter           = null
    )
  }
}
