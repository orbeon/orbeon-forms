package org.orbeon.fr.rpc

import cats.effect.IO
import io.circe.{Json, parser}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.{debug, error}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.xforms.Constants
import sloth.*

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


trait SlothRouterBase {

  private implicit def logger: IndentedLogger = XFormsAPI.inScopeContainingDocument.getIndentedLogger("rpc")

  // Provided by the concrete implementation
  val ResponseEventName: String
  protected val router: RouterCo[Json, Future]

  private val Separator = "/"

  // When our server receives an RPC call, we call this method which take the parameters, decode them, and call
  // Sloth to route it. This results in an actual method call on the API implementation. Then serialize the result
  // to a string to send back to the client.
  //@XPathFunction
  def processRequest(requestId: String, path: String, argsString: String): Unit = {
    val splitPath = path.split(Separator)
    val argJson   = parser.parse(argsString).getOrElse(???) // xxx TODO
    try {
      debug("RPC: Processing request", List("method" -> splitPath.mkString(Separator)))

      val computation =
        IO.fromFuture(
          IO.pure(
            router.apply(Request[Json](Method(traitName = splitPath.init.mkString(Separator), methodName = splitPath.last), argJson)) match {
              case Right(response) => response
              case Left(ServerFailure.MethodNotFound(m))    => Future.failed[Json](new RuntimeException(s"Method not found: `$m`"))
              case Left(ServerFailure.HandlerError(t))      => Future.failed[Json](t)
              case Left(ServerFailure.DeserializerError(t)) => Future.failed[Json](t)
            }
          )
        )

      def dispatchRpcResponse(xfcd: XFormsContainingDocument, responseJson: Json): Unit = {
        implicit def _xfcd: XFormsContainingDocument = xfcd
        XFormsAPI.dispatch(
          name     = ResponseEventName,
          targetId = Constants.DocumentId,
          properties = Map(
            "id"       -> Some(requestId),
            "response" -> Some(responseJson.noSpaces)
          )
        )
      }

      val continuation: (XFormsContainingDocument, Try[Json]) => Either[Try[Unit], Future[Unit]] = {
        case (xfcd, Success(v)) =>
          dispatchRpcResponse(xfcd, v)
          Left(Success(()))
        case (xfcd, Failure(t)) =>
          error(
            "RPC: Handler error",
            List(
              "method"    -> splitPath.mkString(Separator),
              "throwable" -> OrbeonFormatter.format(t)
            )
          )
          dispatchRpcResponse(xfcd, Json.obj("error" -> Json.fromString(t.toString)))
          Left(Success(()))
      }

      inScopeContainingDocument.getAsynchronousSubmissionManager.addAsynchronousCompletion(
        description           = s"Submitting completion for Form Runner RPC API",
        computation           = computation,
        continuation          = continuation,
        awaitInCurrentRequest = Some(Duration.Inf)
      )
    } catch {
      case NonFatal(t) =>
        error(
          "RPC: Error processing Form Runner RPC API call",
          List(
            "method"    -> splitPath.mkString(Separator),
            "throwable" -> OrbeonFormatter.format(t)
          )
        )
        throw t
    }
  }
}
