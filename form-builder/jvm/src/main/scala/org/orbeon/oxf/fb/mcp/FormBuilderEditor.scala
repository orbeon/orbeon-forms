package org.orbeon.oxf.fb.mcp

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fb.{FormBuilder, FormBuilderDocContext, FormBuilderXPathApi, ToolboxOps}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*


private[mcp] final class FormBuilderEditor(sessions: FormBuilderMcpSessionStore) {

  import FormBuilderInternalServices.*
  import FormBuilderXmlSupport.*
  import HeadlessFormBuilderRuntime.*

  def open(documentId: String)(implicit pc: PipelineContext, ec: ExternalContext): String = {
    val originalForm = readXml(readLocalFormBuilderDocument(documentId), s"form-builder-data:$documentId")
    val appForm      = findAppForm(originalForm.rootElement)
    val components   = readXml(readToolbox(appForm), s"form-builder-toolbox:${appForm.app}/${appForm.form}")
    val document     = createHeadlessDocument()

    runInDocument(document) {
      val model = findFormBuilderModel(document)
      val ctx   = FormBuilderDocContext(model)
      replaceInstanceRoot(document, "fb-components-instance", components.rootElement)
      replaceInstanceRoot(document, "fr-form-instance", originalForm.rootElement)
      replaceInstanceRoot(document, "fb-form-instance", annotate(originalForm.rootElement, components.rootElement).rootElement)
      FormBuilderXPathApi.initializeGrids(ctx.formDefinitionRootElem)
      FormBuilderXPathApi.updateSectionTemplateContentHolders(ctx.formDefinitionRootElem)
      ensureSelectedCell(ctx)
    }

    sessions.create(documentId, document)
  }

  def addControl(sessionId: String, label: String): String =
    sessions.withSession(sessionId) { session =>
      runInDocument(session.document) {
        val ctx = FormBuilderDocContext(findFormBuilderModel(session.document))
        ensureSelectedCell(ctx)
        val binding = findTextFieldBinding(ctx)
        val controlName = ToolboxOps.insertNewControl(binding).getOrElse(
          throw new IllegalStateException("Unable to insert a text field")
        )
        FormBuilder.setControlLabelHintHelpOrText(controlName, "label", label, None, isHTML = false)(ctx)
        controlName
      }
    }

  def save(sessionId: String)(implicit pc: PipelineContext, ec: ExternalContext): String =
    sessions.withSession(sessionId) { session =>
      val bytes =
        runInDocument(session.document) {
          val ctx = FormBuilderDocContext(findFormBuilderModel(session.document))
          serializeXml(deannotate(ctx.formDefinitionRootElem))
        }
      putLocalFormBuilderDocument(session.documentId, bytes)
      session.documentId
    }

  private def ensureSelectedCell(ctx: FormBuilderDocContext): Unit =
    if (FormBuilder.findSelectedCell(ctx).isEmpty) {
      val firstCellOpt = (ctx.bodyElem descendant *).find(_.localname == "c")
      firstCellOpt match {
        case Some(cell) =>
          FormBuilder.selectCell(cell)(ctx)
        case None =>
          implicit val implicitCtx: FormBuilderDocContext = ctx
          val (_, grid, _) = ToolboxOps.insertNewSection(withGrid = true, suggestedNameOrNull = null).get
          FormBuilder.selectCell((grid descendant *).find(_.localname == "c").get)(ctx)
      }
    }

  private def findTextFieldBinding(ctx: FormBuilderDocContext): NodeInfo =
    (ctx.formBuilderModel.get.containingDocument.container.findInstance("fb-components-instance").get.rootElement descendant *)
      .find(e => e.localname == "binding" && e.attValueOpt("id").contains("fb-input"))
      .getOrElse(throw new IllegalStateException("Unable to find the Text Field binding"))
}
