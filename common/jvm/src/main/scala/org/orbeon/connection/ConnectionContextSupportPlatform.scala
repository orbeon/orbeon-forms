package org.orbeon.connection

import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.{LoggerFactory, ServiceProviderSupport}
import org.slf4j

import java.net.URI
import scala.jdk.CollectionConverters.*


trait ConnectionContextSupportPlatform extends ConnectionContextSupportTrait {

  private implicit val logger: slf4j.Logger = LoggerFactory.createLogger("org.orbeon.connection.context").logger

  // 2024-10-02: Don't use just a type alias to `AnyRef`, as we use implicits!
  case class ConnectionContext(wrapped: AnyRef)

  def findContext(extension: Map[String, Any]): Option[ConnectionContext] =
    connectionContextProviderOpt.flatMap(provider => Option(provider.getContext(extension.asJava)).map(ConnectionContext.apply))

  def withContext[T](
    url          : URI,
    method       : HttpMethod,
    headers      : Map[String, List[String]],
    extension    : Map[String, Any]
  )(
    body         : => T
  )(implicit
    connectionCtx: Option[ConnectionContext]
  ): T =
    (connectionContextProviderOpt, connectionCtx) match {
      case (Some(provider), Some(ConnectionContext(ctx))) =>
        provider.pushContext(ctx, url, method.entryName, headers.view.mapValues(_.toArray).to(Map).asJava, extension.asJava)
        try
          body
        finally
          provider.popContext(ctx)
      case _ =>
        body
    }

  private lazy val connectionContextProviderOpt: Option[ConnectionContextProvider[AnyRef]] =
    ServiceProviderSupport.loadProvider[ConnectionContextProvider[AnyRef]]("connection context")
}
