package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary
import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.saxon.functions.FunctionLibraryList
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xml.NamespaceMapping


trait FormRunnerComponents {

  private val Logger = LoggerFactory.createLogger(FormRunner.getClass)
  private val indentedLogger: IndentedLogger = new IndentedLogger(Logger)
  private val DefaultNorewriteSet = Set("fr-lang")

  // In an XPath expression, replace non-local variable references.
  //@XPathFunction
  def replaceVarReferencesWithFunctionCalls(
    elemOrAtt   : NodeInfo,
    xpathString : String,
    avt         : Boolean,
    libraryName : String,
    norewrite   : Array[String]
  ): String =
    FormRunnerRename.replaceVarReferencesWithFunctionCalls(
      xpathString,
      NamespaceMapping(elemOrAtt.namespaceMappings.toMap),
      new FunctionLibraryList                         |!>
        (_.addFunctionLibrary(XFormsFunctionLibrary)) |!>
        (_.addFunctionLibrary(FormRunnerFunctionLibrary)),
      avt,
      name =>
        if ((DefaultNorewriteSet ++ norewrite)(name))
          s"$$$name"
        else
          s"frf:controlVariableValue('$name', ${libraryName.trimAllToOpt.map("'" + _ + "'").getOrElse("()")})"
    )(indentedLogger)
}
