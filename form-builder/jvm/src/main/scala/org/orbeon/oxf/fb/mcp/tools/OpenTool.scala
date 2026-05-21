package org.orbeon.oxf.fb.mcp.tools

import io.circe.{Json, JsonObject}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fb.mcp.{FormBuilderEditor, FormBuilderMcpTool}
import org.orbeon.oxf.pipeline.api.PipelineContext


private[mcp] final class OpenTool(editor: FormBuilderEditor) extends FormBuilderMcpTool {

  import FormBuilderMcpTool.*

  val name: String = "open"
  val description: String = "Open a Form Builder document for editing."
  val inputProperties: Json = Json.obj(
    "document_id" -> stringSchema("Form Builder document id")
  )
  val required: List[String] = List("document_id")

  def call(args: JsonObject)(implicit pc: PipelineContext, ec: ExternalContext): Json =
    Json.obj("session_id" -> Json.fromString(editor.open(requiredString(args, "document_id"))))
}
