package org.orbeon.oxf.fr

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.oxf.xforms.analysis.model.DependencyAnalyzer
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.expr.Expression
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.xml.NamespaceMapping

import java.util.regex.Matcher


object FormRunnerRename {

  def replaceVarReferencesWithFunctionCalls(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean,
    newName          : String => String
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
      replaceSingleVarReferenceUseRegex(
        xpathString,
        oldName,
        newName(oldName)
      )
    }
  }

  def replaceSingleVarReference(
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

    xpathString.contains(s"$$$oldName") && DependencyAnalyzer.containsVariableReference(expr, oldName) option {
      replaceSingleVarReferenceUseRegex(
        xpathString,
        oldName,
        s"$$$newName"
      )
    }
  }

  // What follows is a heuristic, as we don't have exact positions provided by the XPath parser. So we try to avoid
  // matching subsequences by saying that the name must not be followed by a character that can be part of an NCName.
  private def replaceSingleVarReferenceUseRegex(
    xpathString      : String,
    name             : String,
    replacement      : String
  ): String =
    xpathString.replaceAll(s"\\$$$name(?![\\p{IsLetter}\\p{IsDigit}.·\\-_])", Matcher.quoteReplacement(replacement))

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