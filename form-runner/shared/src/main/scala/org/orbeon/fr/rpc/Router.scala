package org.orbeon.fr.rpc

import autowire.Core.Request
import cats.effect.IO
import io.circe.parser.*
import io.circe.{Decoder, Encoder, Json}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.xforms.Constants
import org.orbeon.xforms.rpc.JsonSerializers

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


object Router
  extends autowire.Server[Json, Decoder, Encoder]
    with JsonSerializers {

  implicit def logger: IndentedLogger = XFormsAPI.inScopeContainingDocument.getIndentedLogger("rpc")

  // This is necessary for the routing below
  import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext

  private val routes: Router = route[FormRunnerRpcApi](FormRunnerRpcApiImpl)

  // When our server receives an RPC call, we call this method which take the parameters, decode them, and call
  // Autowire to route it. This results in an actual method call on the API implementation. Then serialize the result
  // to a string to send back to the client.
  // TODO: Handle failure response so we can tell the client that the call has failed.
  //@XPathFunction
  def processRequest(id: String, path: String, argsString: String): Unit = {
    val splitPath = path.split("/")
    try {
      debug("RPC: Processing request", List("method" -> (splitPath mkString ".")))

      val computation =
        IO.fromFuture(
          IO.pure(
            routes.apply(Request(splitPath.toSeq, read[Json](parse(argsString).toTry.get).asObject.get.toMap))
          )
        )
        .map(_.noSpaces)

      val continuation: (XFormsContainingDocument, Try[String]) => Either[Try[Unit], Future[Unit]] = {
        case (xfcd, Success(v)) =>
          implicit def _xfcd: XFormsContainingDocument = xfcd
          XFormsAPI.dispatch(
            name     = "fr-rpc-response",
            targetId = Constants.DocumentId,
            properties = Map(
              "id"       -> Some(id),
              "response" -> Some(v)
            )
          )
          Left(Success(()))
        case (_, Failure(t)) =>
          throw t
      }

      inScopeContainingDocument.getAsynchronousSubmissionManager.addAsynchronousCompletion(
        description           = s"Submitting completion for Form Runner RPC API",
        computation           = computation,
        continuation          = continuation,
        awaitInCurrentRequest = None
      )
    } catch {
      case NonFatal(t) =>
        error(
          "RPC: Error processing Form Runner RPC API call",
          List(
            "method"    -> (splitPath mkString "."),
            "throwable" -> OrbeonFormatter.format(t)
          )
        )
        throw t
    }
  }
}
