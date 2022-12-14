package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.XMLNames.{FR, FRPrefix}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.oxf.xforms.analysis.model.DependencyAnalyzer
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.expr.{Expression, StringLiteral}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.StructuredQName
import org.orbeon.xml.NamespaceMapping

import java.util.regex.Matcher


object FormRunnerRename {

  def replaceVarReferencesWithFunctionCalls(
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

    val withFunctionReplacement = {
      val xpathStringMaybeWithVarReplacement = withVarReplacement.getOrElse(xpathString)
      (
        xpathStringMaybeWithVarReplacement.contains(s"""'$oldName'""") ||
        xpathStringMaybeWithVarReplacement.contains(s""""$oldName""")
      ) && (
        containsFnWithParam(expr, oldName, FRControlStringValueName, 2) ||
        containsFnWithParam(expr, oldName, FRControlTypedValueName,  2)
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

  private val FRControlStringValueName = new StructuredQName(FRPrefix, FR, "control-string-value")
  private val FRControlTypedValueName  = new StructuredQName(FRPrefix, FR, "control-typed-value")

  private def containsFnWithParam(expr: Expression, oldName: String, fnName: StructuredQName, argPos: Int): Boolean =
    SaxonUtils.iterateFnArg(expr, fnName, argPos) exists {
      case s: StringLiteral if s.getStringValue == oldName => println(s"xxx found fn for $fnName"); true
      case s                                               => println(s"xxx didn't find for $fnName, arg = $s"); false
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

  private def compileExpression(
    xpathString     : String,
    namespaceMapping: NamespaceMapping,
    functionLibrary : FunctionLibrary,
    avt             : Boolean)(implicit
    logger          : IndentedLogger
  ): Expression = {

    val compiledExpr =
      XPath.compileExpression(
        xpathString      = xpathString,
        namespaceMapping = namespaceMapping,
        locationData     = null,
        functionLibrary  = functionLibrary,
        avt              = avt
      )

    compiledExpr.expression.getInternalExpression
  }
}