package org.orbeon.oxf.util

import org.slf4j

import java.util.ServiceLoader
import scala.collection.compat._
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.control.NonFatal


object ServiceProviderSupport {

  def loadProvider[T : ClassTag](
    providerName  : String,
    checkVersion  : () => Unit = () => (), // can throw to indicate that the feature is not supported
  )(implicit
    logger        : slf4j.Logger
  ): Option[T] =
    try {
      val runtimeClass = implicitly[ClassTag[T]].runtimeClass
      Option(ServiceLoader.load(runtimeClass)).flatMap { serviceLoader =>
        serviceLoader.iterator.asScala.nextOption().map { provider =>
          checkVersion()
          logger.info(s"Loading $providerName provider for class `${runtimeClass.getName}`")
          provider.asInstanceOf[T] // it better be but we can't prove it in code!
        }
      }
    } catch {
      case NonFatal(t) =>
        logger.error(s"Failed to load $providerName provider", t)
        None
    }
}
