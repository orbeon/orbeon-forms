package org.orbeon.oxf.cache

import org.orbeon.oxf.properties.{PropertyLoader, PropertySet}
import org.slf4j

import java.io
import java.util.concurrent.atomic.AtomicReference


trait CacheConfigResolver {
  def resolveResource(path: String): Option[() => io.InputStream]
  def resolveUri(uri: String)      : Option[() => io.InputStream]
}

case class CacheProviderStats(
  name                : String,
  provider            : String,
  isStore             : Boolean,
  heapEntries         : Long,
  totalEntries        : Option[Long],
  maxEntriesLocalHeap : Option[Long],
  maxBytesLocalHeap   : Option[Long],
  estimatedHeapBytes  : Long,
  diskStoreBytes      : Option[Long],
  diskStoreFiles      : Option[Int],
  diskStorePathOpt    : Option[String],
  maxIdleMs           : Option[Long],
  lifespanMs          : Option[Long],
  passivation         : Boolean,
  hitCount            : Option[Long],
  missCount           : Option[Long],
  storeCount          : Option[Long],
  evictionCount       : Option[Long]
) {
  def hitRateOpt: Option[Double] =
    for {
      h <- hitCount
      m <- missCount
      total = h + m
      if total > 0
    } yield h.toDouble / total.toDouble
}

trait CacheProviderApi {
  val ProviderName: String
  def get(cacheName: String): Option[CacheApi]
  def listRunningCacheSnapshots: List[CacheProviderStats]
  def close(): Unit
}

trait CacheApi {
  def put(k: io.Serializable, v: io.Serializable): Unit
  def putIfAbsent(k: io.Serializable, v: io.Serializable): Unit
  def get(k: io.Serializable): Option[io.Serializable]
  def remove(k: io.Serializable): Boolean
  def clear(): Unit

  def getName: String

  def getCurrentEntriesLocalHeap: Option[Long]
  def getMaxEntriesLocalHeap    : Option[Long]
  def getMaxBytesLocalHeap      : Option[Long]
  def statsSnapshot             : Option[CacheProviderStats]
}

object CacheSupport {

  val Logger: slf4j.Logger = slf4j.LoggerFactory.getLogger("org.orbeon.caches")

  private val configResolverRef = new AtomicReference[Option[CacheConfigResolver]](None)

  def registerConfigResolver(resolver: CacheConfigResolver): Unit =
    configResolverRef.set(Some(resolver))

  private[cache] def configResolverOpt: Option[CacheConfigResolver] =
    configResolverRef.get()

  private def propertyPrefix (store: Boolean) = s"oxf.xforms.${if (store) "store" else "cache"}"

  private[cache] def providerPropertyName   (store: Boolean) = s"${propertyPrefix(store)}.provider"
  private[cache] def classnameRePropertyName(store: Boolean, provider: String) = s"${propertyPrefix(store)}.$provider.classname"
  private[cache] def resourcePropertyName   (store: Boolean, provider: String) = s"${propertyPrefix(store)}.$provider.resource"
  private[cache] def uriPropertyName        (store: Boolean, provider: String) = s"${propertyPrefix(store)}.$provider.uri"

  private[cache] def nonBlankString(store: Boolean, name: Boolean => String)(properties: PropertySet): Option[(String, String)] =
    (if (store) properties.getNonBlankString(name = name(true)).map(name(true) -> _) else None)
      .orElse(properties.getNonBlankString(name(false)).map(name(false) -> _))

  private val AllPropertyNames = List(
    providerPropertyName   (store = false), providerPropertyName   (store = true),
    resourcePropertyName   (store = false, "infinispan"), resourcePropertyName(store = true, "infinispan"),
    uriPropertyName        (store = false, "infinispan"), uriPropertyName     (store = true, "infinispan"),
    resourcePropertyName   (store = false, "jcache"),     resourcePropertyName(store = true, "jcache"),
    uriPropertyName        (store = false, "jcache"),     uriPropertyName     (store = true, "jcache"),
    classnameRePropertyName(store = false, "jcache"),     classnameRePropertyName(store = true, "jcache"),
  )

  private[cache]
  def logProperties(properties: PropertySet, log: String => Unit): Unit = {
    log("Cache configuration properties:")
    AllPropertyNames.foreach { propertyName =>
      properties.getNonBlankString(propertyName) match {
        case Some(value) => log(s"  property `$propertyName` set to `$value`")
        case None        => log(s"  property `$propertyName` not set")
      }
    }
  }

  private def loadProvider(store: Boolean, propertySet: PropertySet): CacheProviderApi =
    nonBlankString(store, providerPropertyName)(propertySet) match {
      case None | Some((_, "infinispan")) => new InfinispanProvider(store, Some(propertySet))
      case        Some((_, "jcache"))     => new JCacheProvider(store)
      case        Some((_, "ehcache2"))   => Ehcache2Provider
      case        Some((name, other))     => throw new IllegalArgumentException(s"invalid value for `$name`: `$other` (must be one of `ehcache2`, `jcache`, or `infinispan`)")
    }

  private lazy val (storeProvider, cacheProvider): (CacheProviderApi, CacheProviderApi) = {
    val propertySet = PropertyLoader.getPropertyStore(None).globalPropertySet
    (loadProvider(store = true, propertySet), loadProvider(store = false, propertySet))
  }

  def close(): Unit = {
    storeProvider.close()
    if (storeProvider ne cacheProvider)
      cacheProvider.close()
  }

  def findCache(cacheName: String, store: Boolean): Option[CacheApi] =
    (if (store) storeProvider else cacheProvider).get(cacheName)

  def getOrElseThrow(cacheName: String, store: Boolean): CacheApi =
    findCache(cacheName, store)
      .getOrElse(throw new IllegalStateException(s"no cache found for `$cacheName` (store = $store)"))

  def allRunningCacheSnapshots: List[CacheProviderStats] = {
    val storeSnaps = storeProvider.listRunningCacheSnapshots
    val cacheSnaps =
      if (storeProvider eq cacheProvider) Nil
      else cacheProvider.listRunningCacheSnapshots.filterNot(c => storeSnaps.exists(_.name == c.name))
    (storeSnaps ++ cacheSnaps).sortBy(_.name)
  }
}
