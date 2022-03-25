package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, XPath}
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.expr.Expression
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xml.NamespaceMapping


trait FormRunnerComponents {

  private val Logger = LoggerFactory.createLogger(FormRunner.getClass)
  private val indentedLogger: IndentedLogger = new IndentedLogger(Logger)

  // In an XPath expression, replace non-local variable references.
  //
  //@XPathFunction
  def replaceVarReferencesWithFunctionCalls(elemOrAtt: NodeInfo, avt: Boolean): String = {

    val xpathString = elemOrAtt.stringValue
    val expr        = compileExpression(xpathString, elemOrAtt, FormRunnerFunctionLibrary, avt = avt)(indentedLogger)

    SaxonUtils.iterateExternalVariableReferences(expr).foldLeft(xpathString) { case (xp, name) =>
      xp.replace(s"$$$name", s"(fr:control-string-value('$name'))")
    }
  }

  private def compileExpression(
    xpathString     : String,
    nodeForNs       : NodeInfo,
    functionLibrary : FunctionLibrary,
    avt             : Boolean)(implicit
    logger          : IndentedLogger
  ): Expression = {

    val compiledExpr =
      XPath.compileExpression(
        xpathString      = xpathString,
        namespaceMapping = NamespaceMapping(nodeForNs.parentUnsafe.namespaceMappings.toMap),
        locationData     = null,
        functionLibrary  = functionLibrary,
        avt              = avt
      )

    compiledExpr.expression.getInternalExpression
  }
}
