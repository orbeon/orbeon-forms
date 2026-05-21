package org.orbeon.oxf.fb.mcp.tools

import io.circe.{Json, JsonObject}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fb.mcp.{FormBuilderEditor, FormBuilderMcpTool}
import org.orbeon.oxf.pipeline.api.PipelineContext


private[mcp] final class AddControlTool(editor: FormBuilderEditor) extends FormBuilderMcpTool {

  import FormBuilderMcpTool.*

  val name: String = "add_control"
  override val aliases: List[String] = List("add_text_field")
  val description: String = "Add a Text Field control and set its label."
  val inputProperties: Json = Json.obj(
    "session_id" -> stringSchema("Session id returned by open"),
    "label"      -> stringSchema("Control label")
  )
  val required: List[String] = List("session_id", "label")

  def call(args: JsonObject)(implicit pc: PipelineContext, ec: ExternalContext): Json =
    Json.obj("control_name" -> Json.fromString(editor.addControl(requiredSessionId(args), requiredString(args, "label"))))
}
