package org.orbeon.connection

import org.log4s.Logger
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.LoggerFactory

import java.net.URI
import java.util.ServiceLoader
import scala.collection.compat._
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.control.NonFatal


trait ConnectionContextSupportPlatform extends ConnectionContextSupportTrait {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.connection.context")

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
    loadProvider[ConnectionContextProvider[AnyRef]]

  private def loadProvider[T <: ConnectionContextProvider[AnyRef] : ClassTag]: Option[T] =
    try {

      val runtimeClass = implicitly[ClassTag[T]].runtimeClass

      Option(ServiceLoader.load(runtimeClass)).flatMap { serviceLoader =>
        serviceLoader.iterator.asScala.nextOption().map { provider =>
          logger.info(s"Initializing connection context provider for class `${runtimeClass.getName}`")
          val withInit = provider.asInstanceOf[T] // it better be but we can't prove it in code!
          withInit
        }
      }
    } catch {
      case NonFatal(t) =>
        logger.error(s"Failed to obtain connection context provider:\n${OrbeonFormatter.format(t)}")
        None
    }
}
