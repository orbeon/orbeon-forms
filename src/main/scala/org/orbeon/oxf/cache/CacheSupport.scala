package org.orbeon.oxf.cache

import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.slf4j.LoggerFactory

import java.io.Serializable


trait CacheProviderApi {
  def get(cacheName: String): Option[CacheApi]
  def close(): Unit
}

trait CacheApi {
  def put(k: Serializable, v: Serializable): Unit
  def putIfAbsent(k: Serializable, v: Serializable): Unit
  def get(k: Serializable): Option[Serializable]
  def remove(k: Serializable): Boolean

  def getName: String

  def getMaxEntriesLocalHeap: Option[Long]
  def getLocalHeapSize: Option[Long]
}

// Abstraction for the cache provider. For backward compatibility, we default to Ehcache 2.x without going through
// the JCache API (JSR-107). See https://github.com/orbeon/orbeon-forms/issues/5399.
object CacheSupport {

  val Logger = LoggerFactory.getLogger("org.orbeon.caches")

  val ProviderPropertyName    = "oxf.xforms.cache.provider"
  val ResourcePropertyName    = "oxf.xforms.cache.jcache.resource"
  val UriPropertyName         = "oxf.xforms.cache.jcache.uri"
  val ClassnameRePropertyName = "oxf.xforms.cache.jcache.classname"

  private val AllPropertyNames = List(
    ProviderPropertyName,
    ResourcePropertyName,
    UriPropertyName,
    ClassnameRePropertyName
  )

  def logProperties(properties: PropertySet, log: String => Unit): Unit = {
    log("Cache configuration properties:")
    AllPropertyNames.foreach { propertyName =>
      properties.getNonBlankString(propertyName) match {
        case Some(value) => log(s"  property `$propertyName` set to `$value`")
        case None        => log(s"  property `$propertyName` not set")
      }
    }
  }

  private lazy val provider: CacheProviderApi =
    Properties.instance.getPropertySetOrThrow.getNonBlankString(ProviderPropertyName) match {
      case None | Some("ehcache2") => Eh2CacheSupport
      case Some("jcache")          => JCacheSupport
      case Some(other)             => throw new IllegalArgumentException(s"invalid value for `$ProviderPropertyName`: `$other` (must be one of `ehcache2` or `jcache`)")
    }

  def close(): Unit =
    provider.close()

  def getOrElseThrow(cacheName: String): CacheApi =
    provider.get(cacheName).getOrElse(throw new IllegalStateException(s"no cache found for `$cacheName`"))
}
