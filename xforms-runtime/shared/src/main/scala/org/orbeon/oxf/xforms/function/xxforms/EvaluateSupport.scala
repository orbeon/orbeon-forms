package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.StaticXPath.{CompiledExpression, VariableResolver}
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsContainingDocument
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
    expr              : CompiledExpression,
    exprContextItem   : om.Item,
    exprVarEffectiveId: String,                   // top-level XForms object used to resolve XPath variables
    xfcd              : XFormsContainingDocument
  ): LazyList[om.Item] = {

    val varFunctionContext =
      XFormsFunction.Context( // this will have an empty `BindingContext`
        xfcd.findObjectByEffectiveId(exprVarEffectiveId)
          .getOrElse(throw new IllegalArgumentException(exprVarEffectiveId))
      )

    val variableResolver: VariableResolver = {

      // `varFunctionContext.bindingContext` is empty so `varFunctionContext.bindingContext.getInScopeVariables`
      // only returns model variables. We make this explicit here.
      val inScopeModelVariables = varFunctionContext.modelOpt.map(_.getTopLevelVariables).getOrElse(Map.empty)

      (variableQName: om.StructuredQName, _: XPathContext) => {
        inScopeModelVariables
          .getOrElse(
            SaxonUtils.getStructuredQNameLocalPart(variableQName),
            throw new ValidationException("Undeclared variable in XPath expression: $" + variableQName.getClarkName, null)
          )
      }
    }

    XPath.evaluateKeepItems(
      contextItems       = List(exprContextItem).asJava,
      contextPosition    = 1,
      compiledExpression = expr,
      functionContext    = varFunctionContext,
      variableResolver   = variableResolver
    )(
      reporter           = null
    )
  }
}
