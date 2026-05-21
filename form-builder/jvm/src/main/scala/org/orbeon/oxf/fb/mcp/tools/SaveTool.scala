package org.orbeon.oxf.fb.mcp.tools

import io.circe.{Json, JsonObject}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fb.mcp.{FormBuilderEditor, FormBuilderMcpTool}
import org.orbeon.oxf.pipeline.api.PipelineContext


private[mcp] final class SaveTool(editor: FormBuilderEditor) extends FormBuilderMcpTool {

  import FormBuilderMcpTool.*

  val name: String = "save"
  val description: String = "Save the edited Form Builder document."
  val inputProperties: Json = Json.obj(
    "session_id" -> stringSchema("Session id returned by open")
  )
  val required: List[String] = List("session_id")

  def call(args: JsonObject)(implicit pc: PipelineContext, ec: ExternalContext): Json =
    Json.obj("document_id" -> Json.fromString(editor.save(requiredSessionId(args))))
}
