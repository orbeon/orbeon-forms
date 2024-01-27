package org.orbeon.connection

import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.{LoggerFactory, ServiceProviderSupport}
import org.slf4j

import java.net.URI
import scala.jdk.CollectionConverters._


trait ConnectionContextSupportPlatform extends ConnectionContextSupportTrait {

  private implicit val logger: slf4j.Logger = LoggerFactory.createLogger("org.orbeon.connection.context").logger

  type ConnectionContext = AnyRef

  def getContext(extension: Map[String, Any]): Option[ConnectionContext] =
    connectionContextProviderOpt.flatMap(provider => Option(provider.getContext(extension.asJava)))

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
      case (Some(provider), Some(ctx)) =>
        provider.pushContext(ctx, url, method.entryName, headers.mapValues(_.toArray).asJava, extension.asJava)
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
