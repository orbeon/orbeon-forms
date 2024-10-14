package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.StaticXPath.{CompiledExpression, VariableResolver}
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om

import scala.jdk.CollectionConverters.*


object EvaluateSupport {

  def evaluateInContextFromXPathString(
    expr       : String,
    effectiveId: String
  )(implicit
    xpc        : XPathContext,
    xfc        : XFormsFunction.Context
  ): List[om.Item] = {

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

  def evaluateInContextFromXPathExpr(
    expr       : CompiledExpression,
    effectiveId: String
  )(implicit
    xpc        : XPathContext,
    xfc        : XFormsFunction.Context
  ): LazyList[om.Item] = {

    val xfcd = xfc.containingDocument
    val contextObject =
      xfcd.findObjectByEffectiveId(effectiveId)
        .getOrElse(throw new IllegalArgumentException(effectiveId))

    val newXPc = XFormsFunction.Context(contextObject)

    val variableResolver: VariableResolver = {
      val inScopeVariables = newXPc.bindingContext.getInScopeVariables
      (variableQName: om.StructuredQName, _: XPathContext) => {
        Option(inScopeVariables.get(SaxonUtils.getStructuredQNameLocalPart(variableQName)))
          .getOrElse(throw new ValidationException("Undeclared variable in XPath expression: $" + variableQName.getClarkName, null))
      }
    }

    XPath.evaluateKeepItems(
      contextItems       = List(xpc.getContextItem).asJava,
      contextPosition    = 1,
      compiledExpression = expr,
      functionContext    = newXPc,
      variableResolver   = variableResolver
    )(
      reporter           = null
    )
  }
}
