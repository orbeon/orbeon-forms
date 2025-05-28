package org.orbeon.oxf.util

import org.slf4j

import java.util.ServiceLoader
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Try}


object ServiceProviderSupport {

  def loadProvider[T : ClassTag](
    providerName: String,
    checkVersion: () => Unit = () => (), // can throw to indicate that the feature is not supported
  )(implicit
    logger      : slf4j.Logger
  ): Option[T] = {
    val runtimeClass = implicitly[ClassTag[T]].runtimeClass
    try {
      Option(ServiceLoader.load(runtimeClass)).flatMap { serviceLoader =>
        serviceLoader.iterator.asScala.nextOption().map { provider =>
          checkVersion()
          logger.info(s"Loading $providerName provider for class `${runtimeClass.getName}`")
          provider.asInstanceOf[T] // it better be but we can't prove it in code!
        }
      }
    } catch {
      case NonFatal(t) =>
        logger.error(s"Failed to load $providerName provider for class `${runtimeClass.getName}`", t)
        None
    }
  }

  def loadProviders[T : ClassTag](
    providerName: String
  )(implicit
    logger      : slf4j.Logger
  ): List[T] = {
    val runtimeClass = implicitly[ClassTag[T]].runtimeClass
    Try(Option(ServiceLoader.load(runtimeClass)))
      .map { serviceLoaderOpt =>
        serviceLoaderOpt
          .iterator
          .flatten(_.iterator.asScala)
          .map { provider =>
            logger.info(s"Loading $providerName provider for class `${runtimeClass.getName}`")
            provider.asInstanceOf[T] // it better be but we can't prove it in code!
          }
          .toList
      }
      .recoverWith {
        case NonFatal(t) =>
          logger.error(s"Failed to load $providerName providers for class `${runtimeClass.getName}`", t)
          Failure(t)
      }
      .getOrElse(Nil)
  }
}
