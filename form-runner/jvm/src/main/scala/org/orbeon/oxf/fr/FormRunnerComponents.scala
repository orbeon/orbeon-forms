package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xml.NamespaceMapping


trait FormRunnerComponents {

  private val Logger = LoggerFactory.createLogger(FormRunner.getClass)
  private val indentedLogger: IndentedLogger = new IndentedLogger(Logger)

  // In an XPath expression, replace non-local variable references.
  //@XPathFunction
  def replaceVarReferencesWithFunctionCalls(elemOrAtt: NodeInfo, avt: Boolean): String =
    FormRunnerRename.replaceVarReferencesWithFunctionCalls(
      elemOrAtt.stringValue,
      NamespaceMapping(elemOrAtt.namespaceMappings.toMap),
      FormRunnerFunctionLibrary,
      avt,
      name => s"(fr:control-string-value('$name'))"
    )(indentedLogger)
}
