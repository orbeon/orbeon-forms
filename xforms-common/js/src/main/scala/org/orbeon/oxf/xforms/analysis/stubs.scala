package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping


// TODO: Temporary stubs for the Scala.js side.

trait StaticState {
  def functionLibrary: FunctionLibrary // for XPath MIPs compilation
}

// TODO: What's needed for construction vs other?
trait PartAnalysisImpl {

  def staticState: StaticState

  def isTopLevel: Boolean

  def getIndentedLogger: IndentedLogger // in `PartAnalysis`

  def getModel: Model // PartGlobalOps
  def getModel(prefixedId: String): Model // PartModelAnalysis
  def getDefaultModelForScope(scope: Scope): Option[Model] // PartModelAnalysis

  def getAttributeControl(prefixedForAttribute: String, attributeName: String): AttributeControl // PartGlobalOps
  def elementInParent: Option[ElementAnalysis] // in `PartAnalysis`
  def getEventHandlers(observerPrefixedId: String): List[EventHandler]
}