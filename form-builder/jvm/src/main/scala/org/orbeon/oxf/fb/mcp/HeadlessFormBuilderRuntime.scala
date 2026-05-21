package org.orbeon.oxf.fb.mcp

import org.orbeon.oxf.fr.Names
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsContainingDocumentBuilder}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions


private[mcp] object HeadlessFormBuilderRuntime {

  def runInDocument[T](document: XFormsContainingDocument)(body: => T): T =
    XFormsAPI.withContainingDocument(document) {
      document.withOutermostActionHandler {
        body
      }
    }

  def createHeadlessDocument(): XFormsContainingDocument = {
    implicit val logger: IndentedLogger = XFormsStateManager.newIndentedLogger
    val staticState = PartAnalysisBuilder.createFromDocument(NodeConversions.elemToOrbeonDom(HeadlessFormBuilderDocument.Xml))._2
    val document    = XFormsContainingDocumentBuilder(staticState, None, None, mustInitialize = true)
    document.afterInitialResponse()
    document.beforeExternalEvents(null, submissionIdOpt = None)
    document
  }

  def replaceInstanceRoot(document: XFormsContainingDocument, instanceId: String, root: NodeInfo): Unit = {
    val instance = document.container.findInstance(instanceId).get
    instance.replace(
      newDocumentInfo = XFormsInstance.createDocumentInfo(Right(TransformerUtils.extractAsMutableDocument(root)), instance.instance.exposeXPathTypes),
      collector       = EventCollector.Throw
    )
  }

  def findFormBuilderModel(document: XFormsContainingDocument): XFormsModel =
    document.models.find(_.getId == Names.FormModel).get
}
