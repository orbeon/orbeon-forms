package org.orbeon.oxf.fb.mcp

import io.circe.{Json, JsonObject}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext


private[mcp] trait FormBuilderMcpTool {

  def name: String
  def aliases: List[String] = Nil
  def description: String
  def inputProperties: Json
  def required: List[String]
  def call(args: JsonObject)(implicit pc: PipelineContext, ec: ExternalContext): Json

  final def definition: Json =
    Json.obj(
      "name"        -> Json.fromString(name),
      "description" -> Json.fromString(description),
      "inputSchema" -> Json.obj(
        "type"       -> Json.fromString("object"),
        "properties" -> inputProperties,
        "required"   -> Json.arr(required.map(Json.fromString)*)
      )
    )
}

private[mcp] object FormBuilderMcpTool {

  def stringSchema(description: String): Json =
    Json.obj(
      "type"        -> Json.fromString("string"),
      "description" -> Json.fromString(description)
    )

  def requiredSessionId(args: JsonObject): String =
    args("session_id").orElse(args("uuid")).flatMap(_.asString).getOrElse(
      throw new IllegalArgumentException("Missing `session_id`")
    )

  def requiredString(args: JsonObject, name: String): String =
    args(name).flatMap(_.asString).getOrElse(throw new IllegalArgumentException(s"Missing `$name`"))
}
