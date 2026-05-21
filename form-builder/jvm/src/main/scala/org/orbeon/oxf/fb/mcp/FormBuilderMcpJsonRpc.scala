package org.orbeon.oxf.fb.mcp

import io.circe.{Json, JsonObject}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext

import scala.util.control.NonFatal


private[mcp] object FormBuilderMcpJsonRpc {

  def handle(
    json: Json
  )(
    dispatch: FormBuilderMcpJsonRpcRequest => Option[Json]
  )(implicit pc: PipelineContext, ec: ExternalContext): Option[Json] = {
    val request = parseRequest(json)

    try {
      dispatch(request)
    } catch {
      case e: HttpStatusCodeException =>
        request.failure(-32000, Option(e.getMessage).getOrElse(s"HTTP ${e.code}"))
      case NonFatal(t) =>
        request.failure(-32000, t.getMessage)
    }
  }

  def paramsObject(paramsOpt: Option[Json]): JsonObject =
    paramsOpt.flatMap(_.asObject).getOrElse(JsonObject.empty)

  private def parseRequest(json: Json): FormBuilderMcpJsonRpcRequest = {
    val obj       = json.asObject.getOrElse(throw HttpStatusCodeException(StatusCode.BadRequest))
    val idOpt     = obj("id")
    val method    = obj("method").flatMap(_.asString).getOrElse(throw HttpStatusCodeException(StatusCode.BadRequest))
    val paramsOpt = obj("params")

    FormBuilderMcpJsonRpcRequest(idOpt, method, paramsOpt)
  }
}

private[mcp] final case class FormBuilderMcpJsonRpcRequest(
  idOpt    : Option[Json],
  method   : String,
  paramsOpt: Option[Json]
) {
  def success(result: Json): Option[Json] =
    idOpt.map(id => Json.obj("jsonrpc" -> Json.fromString("2.0"), "id" -> id, "result" -> result))

  def failure(code: Int, message: String): Option[Json] =
    idOpt.map(id => Json.obj(
      "jsonrpc" -> Json.fromString("2.0"),
      "id"      -> id,
      "error"   -> Json.obj("code" -> Json.fromInt(code), "message" -> Json.fromString(message))
    ))
}
