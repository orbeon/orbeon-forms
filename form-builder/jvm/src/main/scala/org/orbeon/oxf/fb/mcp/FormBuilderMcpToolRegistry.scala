package org.orbeon.oxf.fb.mcp

import io.circe.{Json, JsonObject}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext


private[mcp] final class FormBuilderMcpToolRegistry(tools: Seq[FormBuilderMcpTool]) {

  private val toolEntries: Seq[(String, FormBuilderMcpTool)] =
    tools.flatMap(tool => (tool.name :: tool.aliases).map(_ -> tool))

  require(
    toolEntries.groupMap(_._1)(_._2).forall(_._2.size == 1),
    "Duplicate MCP tool names or aliases"
  )

  private val toolsByName: Map[String, FormBuilderMcpTool] =
    toolEntries.toMap

  def toolsJson: Json =
    Json.arr(tools.map(_.definition)*)

  def call(name: String, args: JsonObject)(implicit pc: PipelineContext, ec: ExternalContext): Json =
    toolsByName.getOrElse(name, throw new IllegalArgumentException(s"Unknown tool: $name")).call(args)
}
