package org.orbeon.oxf.fb.mcp

import io.circe.{Json, JsonObject}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext


private[mcp] final class FormBuilderMcpServer(registry: FormBuilderMcpToolRegistry) {

  def handleJsonRpc(json: Json)(implicit pc: PipelineContext, ec: ExternalContext): Option[Json] =
    FormBuilderMcpJsonRpc.handle(json) { request =>
      request.method match {
        case "initialize" =>
          request.success(Json.obj(
            "protocolVersion" -> Json.fromString("2025-06-18"),
            "capabilities"    -> Json.obj("tools" -> Json.obj()),
            "serverInfo"      -> Json.obj(
              "name"    -> Json.fromString("orbeon-form-builder-poc"),
              "version" -> Json.fromString("0.1.0")
            )
          ))
        case "notifications/initialized" =>
          None
        case "ping" =>
          request.success(Json.obj())
        case "tools/list" =>
          request.success(Json.obj("tools" -> registry.toolsJson))
        case "tools/call" =>
          val params = FormBuilderMcpJsonRpc.paramsObject(request.paramsOpt)
          val name   = FormBuilderMcpTool.requiredString(params, "name")
          val args   = params("arguments").flatMap(_.asObject).getOrElse(JsonObject.empty)
          request.success(toolResult(registry.call(name, args)))
        case "open" | "add_control" | "add_text_field" | "save" =>
          request.success(registry.call(request.method, FormBuilderMcpJsonRpc.paramsObject(request.paramsOpt)))
        case _ =>
          request.failure(-32601, s"Unknown method: ${request.method}")
      }
    }

  private def toolResult(result: Json): Json =
    Json.obj(
      "content"           -> Json.arr(Json.obj("type" -> Json.fromString("text"), "text" -> Json.fromString(result.noSpaces))),
      "structuredContent" -> result
    )
}
