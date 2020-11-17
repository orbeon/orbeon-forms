package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom
import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsStaticState
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.xforms.xbl.Scope


trait NestedPartAnalysis extends PartAnalysis

object ElementAnalysisTreeBuilder {

  private[analysis] def throwError(): Nothing =
    throw new UnsupportedOperationException("xxf:dynamic is not supported in offline mode")

  def createOrUpdateStaticShadowTree(
    partAnalysisCtx   : NestedPartAnalysis,
    existingComponent : ComponentControl,
    elemInSource      : Option[dom.Element])(implicit
    logger            : IndentedLogger
  ): Unit =
    throwError()

  def clearShadowTree(
    partAnalysisCtx   : NestedPartAnalysis,
    existingComponent : ComponentControl
  ): Unit =
    throwError()
}

object PartAnalysisBuilder {

  def rebuildBindTree(
    partAnalysisCtx : NestedPartAnalysis,
    model           : Model,
    rawModelElement : Element)(implicit
    logger          : IndentedLogger
  ): Unit =
    ElementAnalysisTreeBuilder.throwError()

 def createPart(
    staticState  : XFormsStaticState,
    parent       : PartAnalysis,
    formDocument : Document,
    startScope   : Scope)(implicit
    logger       : IndentedLogger
  ): (SAXStore, NestedPartAnalysis) =
   ElementAnalysisTreeBuilder.throwError()
}
