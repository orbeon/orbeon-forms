package org.orbeon.xbl

import cats.effect.IO
import org.orbeon.connection.AsyncConnectionResult
import org.orbeon.connection.ConnectionContextSupport.{ConnectionContexts, EmptyConnectionContexts}
import org.orbeon.oxf.externalcontext.SafeRequestContext
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.{Connection, CoreCrossPlatformSupport, IndentedLogger, ResourceResolver}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalajs.dom

import java.net.URI
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.typedarray.Uint8Array


object ImageAttachmentSupport extends ImageAttachmentSupportTrait {

  def retrieveResource(
    containingDocument: XFormsContainingDocument,
    forEffectiveId    : String,
    uri               : URI,
    contentType       : Option[String]
  )(implicit
    logger            : IndentedLogger
  ): IO[String] = {

    // TODO: Ideally, this would use HTTP caching, so we could do a conditional `GET`, for example.
    def fromConnection: IO[AsyncConnectionResult] = {

      implicit val safeRequestCtx  : SafeRequestContext       = SafeRequestContext(CoreCrossPlatformSupport.externalContext)
      implicit val connectionCtx   : ConnectionContexts       = EmptyConnectionContexts
      implicit val resourceResolver: Option[ResourceResolver] = containingDocument.staticState.resourceResolverOpt

      Connection.connectAsync(
        method      = HttpMethod.GET,
        url         = uri,
        credentials = None,
        content     = None,
        headers     = Map.empty,
        loadState   = false,
        logBody     = false
      )
    }

    def createBlobFromByteArray(byteArray: Array[Byte]): dom.Blob =
      new dom.Blob(
        blobParts = Array[dom.BlobPart](new Uint8Array(byteArray.toJSArray.asInstanceOf[js.Array[Short]]).buffer).toJSArray,
        options   = new dom.BlobPropertyBag { `type` = contentType.orUndefined }
      )

    fromConnection
      .flatMap(_.content.stream.compile.to(Array) /* no `Collector` for `js.Array` yet */)
      .map(createBlobFromByteArray)
      .map(dom.URL.createObjectURL)
  }
}