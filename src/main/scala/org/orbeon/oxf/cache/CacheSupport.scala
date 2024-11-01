package org.orbeon.oxf.cache

import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.slf4j

import java.io


trait CacheProviderApi {
  def get(cacheName: String): Option[CacheApi]
  def close(): Unit
}

trait CacheApi {
  def put(k: io.Serializable, v: io.Serializable): Unit
  def putIfAbsent(k: io.Serializable, v: io.Serializable): Unit
  def get(k: io.Serializable): Option[io.Serializable]
  def remove(k: io.Serializable): Boolean

  def getName: String

  def getMaxEntriesLocalHeap: Option[Long]
  def getLocalHeapSize: Option[Long]
}

// Abstraction for the cache provider. For backward compatibility, we default to Ehcache 2.x without going through
// the JCache API (JSR-107). See https://github.com/orbeon/orbeon-forms/issues/5399.
object CacheSupport {

  val Logger: slf4j.Logger = slf4j.LoggerFactory.getLogger("org.orbeon.caches")

  private def propertyPrefix (store: Boolean) = s"oxf.xforms.${if (store) "store" else "cache"}"

  def providerPropertyName   (store: Boolean) = s"${propertyPrefix(store)}.provider"
  def classnameRePropertyName(store: Boolean) = s"${propertyPrefix(store)}.jcache.classname"
  def resourcePropertyName   (store: Boolean) = s"${propertyPrefix(store)}.jcache.resource"
  def uriPropertyName        (store: Boolean) = s"${propertyPrefix(store)}.jcache.uri"

  def nonBlankString(store: Boolean, name: Boolean => String)(properties: PropertySet): Option[(String, String)] =
    (if (store) properties.getNonBlankString(name = name(true)).map(name(true) -> _) else None)
      .orElse(properties.getNonBlankString(name(false)).map(name(false) -> _))

  private val AllPropertyNames = List(
    providerPropertyName   (store = false), providerPropertyName   (store = true),
    resourcePropertyName   (store = false), resourcePropertyName   (store = true),
    uriPropertyName        (store = false), uriPropertyName        (store = true),
    classnameRePropertyName(store = false), classnameRePropertyName(store = true),
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

  private def loadProvider(store: Boolean): CacheProviderApi =
    nonBlankString(store, providerPropertyName)(Properties.instance.getPropertySetOrThrow) match {
      case None | Some((_, "ehcache2")) => Ehcache2Provider
      case        Some((_, "jcache"))   => new JCacheProvider(store)
      case        Some((name, other))   => throw new IllegalArgumentException(s"invalid value for `$name`: `$other` (must be one of `ehcache2` or `jcache`)")
    }

  // We thought about implementing provider deduplication, but we don't need to: `Eh2CacheSupport` is an object, and so
  // we get the same reference anyway. `JCacheSupport` is a class, but it finds a JCache provider and then gets a
  // `CacheManager` from the provider. If the configurations are the same, the JCache API says that "calls to this
  // method with the same `URI` and `ClassLoader` must return the same `CacheManager` instance". So we might have two
  // different instances of `JCacheSupport`, but they will point to the same `CacheManager` instance.
  private lazy val (storeProvider, cacheProvider): (CacheProviderApi, CacheProviderApi) =
    (loadProvider(store = true), loadProvider(store = false))

  def close(): Unit = {
    storeProvider.close()
    if (storeProvider ne cacheProvider)
      cacheProvider.close()
  }

  def getOrElseThrow(cacheName: String, store: Boolean): CacheApi =
    (if (store) storeProvider else cacheProvider)
      .get(cacheName)
      .getOrElse(throw new IllegalStateException(s"no cache found for `$cacheName` (store = $store)"))
}
