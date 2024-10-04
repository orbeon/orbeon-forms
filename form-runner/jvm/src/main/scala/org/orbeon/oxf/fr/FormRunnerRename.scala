package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.XMLNames.{FR, FRPrefix}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.analysis.model.DependencyAnalyzer
import org.orbeon.oxf.xml.{SaxonUtils, ShareableXPathStaticContext}
import org.orbeon.saxon.expr.{Expression, StringLiteral}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.{NodeInfo, StructuredQName}
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xml.NamespaceMapping

import java.util.regex.Matcher


object FormRunnerRename {

  def replaceVarReferencesWithFunctionCallsFromString(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean,
    replace          : String => String
  )(implicit
    logger           : IndentedLogger
  ): String = {

    val expr =
      compileExpression(
        xpathString      = xpathString,
        namespaceMapping = namespaceMapping,
        functionLibrary  = functionLibrary,
        avt              = avt
      )

    // `sortBy` and `reverse` were introduced to avoid substring matches in variable  references. But we also use a
    // regex to prevent matching a substring, so they are probably not needed.
    val varRefsByLengthDesc =
      SaxonUtils.iterateExternalVariableReferences(expr).toList.sortBy(_.length).reverse

    varRefsByLengthDesc.foldLeft(xpathString) { case (xpathString, oldName) =>
      replaceVarReferenceUseRegex(
        xpathString,
        oldName,
        replace(oldName)
      )
    }
  }

  def replaceVarAndFnReferences(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean,
    oldName          : String,
    newName          : String)(implicit
    logger          : IndentedLogger
  ): Option[String] = {

    val expr =
      compileExpression(
        xpathString      = xpathString,
        namespaceMapping = namespaceMapping,
        functionLibrary  = functionLibrary,
        avt              = avt
      )

    val withVarReplacement =
      xpathString.contains(s"$$$oldName") && DependencyAnalyzer.containsVariableReference(expr, oldName) option {
        replaceVarReferenceUseRegex(
          xpathString,
          oldName,
          s"$$$newName"
        )
      }

    // Here we have to use a heuristic because we don't have exact information about position of the function
    // parameters. In addition, if an expression contains a mix of functions of different arity, we can't know for sure
    // which function we are dealing with. So we just replace and hope for the best! We could try to be smarter and
    // detect that we are in a most likely unsupported case, and not perform the replacement in that case, but then
    // we would need to have some UI to report the problem to the user.
    val withFunctionReplacement = {
      val xpathStringMaybeWithVarReplacement = withVarReplacement.getOrElse(xpathString)
      (
        xpathStringMaybeWithVarReplacement.contains(s"""'$oldName'""") ||
        xpathStringMaybeWithVarReplacement.contains(s""""$oldName""")
      ) && (
        containsFnWithParam(expr, FRControlStringValueName, 2, oldName, 0) ||
        containsFnWithParam(expr, FRControlTypedValueName,  2, oldName, 0) ||
        containsFnWithParam(expr, FRControlStringValueName, 3, oldName, 2) ||
        containsFnWithParam(expr, FRControlTypedValueName,  3, oldName, 2)
      ) option {
        replaceQuotedStringUseRegex(
          xpathStringMaybeWithVarReplacement,
          oldName,
          newName
        )
      }
    }

    withFunctionReplacement.orElse(withVarReplacement)
  }

  def findUnresolvedVariableReferences(
    elemOrAtt     : NodeInfo,
    avt           : Boolean,
    validBindNames: => String => Boolean)(implicit // by-name so we can avoid costly computation if not needed
    logger        : IndentedLogger
  ): Iterator[String] =
    elemOrAtt.stringValue.trimAllToOpt match {
      case Some(xpathString) if ! xpathString.contains("$") =>
        // Optimization
        Iterator.empty
      case Some(xpathString) =>

        val expr =
          compileExpression(
            xpathString      = xpathString,
            namespaceMapping = NamespaceMapping(elemOrAtt.namespaceMappings.toMap),
            functionLibrary  = inScopeContainingDocument.partAnalysis.functionLibrary, // or could just use the Form Runner function library
            avt              = avt
          )

        DependencyAnalyzer.findMissingVariableReferences(expr, validBindNames)
      case None =>
        Iterator.empty
    }

  private val FRControlStringValueName = new StructuredQName(FRPrefix, FR, "control-string-value")
  private val FRControlTypedValueName  = new StructuredQName(FRPrefix, FR, "control-typed-value")

  private def containsFnWithParam(expr: Expression, fnName: StructuredQName, arity: Int, oldName: String, argPos: Int): Boolean =
    SaxonUtils.iterateFunctions(expr, fnName) exists {
      case fn if fn.getArguments.size == arity =>
        fn.getArguments()(argPos) match {
          case s: StringLiteral if s.getStringValue == oldName => true
          case _                                               => false
        }
      case _ => false
    }

  // What follows is a heuristic, as we don't have exact positions provided by the XPath parser. So we try to avoid
  // matching subsequences by saying that the name must not be followed by a character that can be part of an NCName.
  private def replaceVarReferenceUseRegex(
    xpathString      : String,
    name             : String,
    replacement      : String
  ): String =
    xpathString.replaceAll(s"\\$$$name(?![\\p{IsLetter}\\p{IsDigit}.·\\-_])", Matcher.quoteReplacement(replacement))

  private def replaceQuotedStringUseRegex(
    xpathString      : String,
    name             : String,
    replacement      : String
  ): String =
    xpathString
      .replaceAll(s"""'$name'""", Matcher.quoteReplacement(s"""'$replacement'"""))
      .replaceAll(s""""$name"""", Matcher.quoteReplacement(s""""$replacement""""))

  // Compile expression but don't `typeCheck()` it so that we don't throw static XPath errors at this point, but later
  // when we have the option to gather XPath errors in the static state or the running form.
  private def compileExpression(
    xpathString     : String,
    namespaceMapping: NamespaceMapping,
    functionLibrary : FunctionLibrary,
    avt             : Boolean)(implicit
    logger          : IndentedLogger
  ): Expression =
    XPath.compileExpressionMinimal(
      staticContext = new ShareableXPathStaticContext(
          XPath.GlobalConfiguration,
          namespaceMapping,
          functionLibrary
        ),
      xpathString   = xpathString,
      avt           = avt
    )
}