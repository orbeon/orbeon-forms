/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.cache

import java.time.Duration
import java.util as ju
import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.util.StringUtils.*

object ObjectCache {

  val DefaultCacheName = "core.cache.pipelines"
  private val DefaultSize = 2000

  private val CachePropertyNamePrefix      = "oxf"
  private val CachePropertyNameSizeSuffix  = "size"
  private val CachePropertyMaxBytesSuffix  = "max-size-bytes"
  private val CachePropertyMaxIdleSuffix   = "max-idle"

  private final case class LegacyObjectCacheSpec(
    legacyPrefixes : List[String],
    defaultSize    : Int,
    defaultMaxBytes: String,
    defaultMaxIdle : String
  )

  val LegacyCacheAliases: Map[String, String] = Map(
    "cache.main"                -> "core.cache.pipelines",
    "cache.xpath"               -> "core.cache.xpath",
    "xforms.cache.documents"    -> "xforms.cache.live-documents",
    "xforms.cache.static-state" -> "xforms.cache.static-states"
  )

  private val LegacyObjectCacheSpecs: Map[String, LegacyObjectCacheSpec] = Map(
    "core.cache.pipelines"        -> LegacyObjectCacheSpec(List("oxf.cache", "oxf.cache.main"),   defaultSize = 2000,  defaultMaxBytes = "10%", defaultMaxIdle = ""),
    "core.cache.xpath"            -> LegacyObjectCacheSpec(List("oxf.cache.xpath"),               defaultSize = 20000, defaultMaxBytes = "5%",  defaultMaxIdle = ""),
    "xforms.cache.live-documents" -> LegacyObjectCacheSpec(List("oxf.xforms.cache.documents"),    defaultSize = 500,   defaultMaxBytes = "15%", defaultMaxIdle = ""),
    "xforms.cache.static-states"  -> LegacyObjectCacheSpec(List("oxf.xforms.cache.static-state"), defaultSize = 200,   defaultMaxBytes = "15%", defaultMaxIdle = "")
  )

  private val warnedLegacyProperties = ju.concurrent.ConcurrentHashMap.newKeySet[String]()

  def canonicalCacheName(cacheName: String): String =
    LegacyCacheAliases.getOrElse(cacheName, cacheName)

  private val ByteSpecRegex     = """(?i)^([0-9]+(?:\.[0-9]+)?)\s*(b|k|kb|kib|m|mb|mib|g|gb|gib)?$""".r
  private val DurationSpecRegex = """(?i)^([0-9]+)\s*(ms|s|m|h|d)?$""".r

  private val namedObjectCaches  = new ju.concurrent.ConcurrentHashMap[String, Cache]
  private val registeredDefaults = new ju.concurrent.ConcurrentHashMap[String, Int]

  @volatile private var lastConfiguredPropertySet: PropertySet = null

  locally {
    registeredDefaults.put(DefaultCacheName, DefaultSize)
    namedObjectCaches.put(DefaultCacheName, new MemoryCacheImpl(DefaultSize))
  }

  // Get the instance of the main object cache
  def instance: Cache = namedObjectCaches.get(DefaultCacheName)

  // Get the instance of the object cache specified
  def instance(cacheName: String, defaultSize: Int): Cache = {
    val canonical = canonicalCacheName(cacheName)
    namedObjectCaches.computeIfAbsent(
      canonical,
      _ => {
        registeredDefaults.put(canonical, defaultSize)
        val cache = new MemoryCacheImpl(defaultSize)
        Try(Option(Properties.instance.getPropertySet)).toOption.flatten.foreach { ps =>
          configureCacheFromPropertySet(canonical, defaultSize, cache, ps)
        }
        cache
      }
    )
  }

  def findCache(cacheName: String): Option[Cache] =
    Option(namedObjectCaches.get(canonicalCacheName(cacheName)))

  def allCaches: List[(String, Cache)] =
    namedObjectCaches.entrySet.asScala.map(e => e.getKey -> e.getValue).toList.sortBy(_._1)

  // Fast, allocation-free check invoked per request and when building console snapshots so live property reloads
  // (e.g., from `/fr/orbeon/configuration` or `Reload Properties`) immediately update cache limits.
  def configureAllIfNeeded(propertySet: PropertySet): Unit =
    if ((propertySet ne null) && (propertySet ne lastConfiguredPropertySet)) {
      for (entry <- namedObjectCaches.entrySet.asScala) {
        val name        = entry.getKey
        val defaultSize = registeredDefaults.getOrDefault(name, DefaultSize)
        configureCacheFromPropertySet(name, defaultSize, entry.getValue, propertySet)
      }
      lastConfiguredPropertySet = propertySet
    }

  private def warnLegacyPropertyOnce(legacyName: String, canonicalName: String): Unit =
    if (warnedLegacyProperties.add(legacyName))
      CacheSupport.Logger.warn(s"Deprecated cache property `$legacyName` is in use; rename it to `$canonicalName`.")

  private def resolveRawStringWithLegacyFallback(
    propertySet     : PropertySet,
    canonicalName   : String,
    suffix          : String,
    specOpt         : Option[LegacyObjectCacheSpec],
    builtInDefaultFn: LegacyObjectCacheSpec => String
  ): Option[String] = {
    val canonicalProp = s"$CachePropertyNamePrefix.$canonicalName.$suffix"
    val newRawOpt     = propertySet.getObjectOpt(canonicalProp).map(_.toString.trim)
    val legacyHitOpt  =
      specOpt.toList.flatMap(_.legacyPrefixes).iterator
        .flatMap(p => propertySet.getObjectOpt(s"$p.$suffix").map(v => s"$p.$suffix" -> v.toString.trim))
        .nextOption()

    legacyHitOpt match {
      case Some((legacyProp, legacyVal)) if newRawOpt.forall(v => specOpt.exists(s => builtInDefaultFn(s) == v)) =>
        warnLegacyPropertyOnce(legacyProp, canonicalProp)
        legacyVal.trimAllToOpt
      case _ =>
        newRawOpt.flatMap(_.trimAllToOpt)
    }
  }

  def configureCacheFromPropertySet(
    cacheName  : String,
    defaultSize: Int,
    cache      : Cache,
    propertySet: PropertySet
  ): Unit = {
    val canonical     = canonicalCacheName(cacheName)
    val specOpt       = LegacyObjectCacheSpecs.get(canonical)
    val canonicalSize = s"$CachePropertyNamePrefix.$canonical.$CachePropertyNameSizeSuffix"
    val newSizeOpt    = propertySet.getIntOpt(canonicalSize)
    val legacySizeOpt =
      specOpt.toList.flatMap(_.legacyPrefixes).iterator
        .flatMap(p => propertySet.getIntOpt(s"$p.$CachePropertyNameSizeSuffix").map(v => s"$p.$CachePropertyNameSizeSuffix" -> v))
        .nextOption()

    val resolvedMaxSize =
      legacySizeOpt match {
        case Some((legacyProp, legacySize)) if newSizeOpt.forall(v => specOpt.exists(_.defaultSize == v)) =>
          warnLegacyPropertyOnce(legacyProp, canonicalSize)
          legacySize
        case _ =>
          newSizeOpt.getOrElse(defaultSize)
      }

    val resolvedMaxBytes =
      resolveRawStringWithLegacyFallback(propertySet, canonical, CachePropertyMaxBytesSuffix, specOpt, _.defaultMaxBytes)
        .flatMap(parseMaxSizeBytes(_, Runtime.getRuntime.maxMemory()))

    val resolvedMaxIdle =
      resolveRawStringWithLegacyFallback(propertySet, canonical, CachePropertyMaxIdleSuffix, specOpt, _.defaultMaxIdle)
        .flatMap(parseDurationMillis)

    cache.setMaxSize(resolvedMaxSize)
    cache.setMaxSizeBytes(resolvedMaxBytes)
    cache.setMaxIdleMillis(resolvedMaxIdle)
  }

  /**
   * Parse byte limit specifications such as:
   * - `""`, `"0"`, `"-1"` -> `None` (unbounded bytes)
   * - `"15%"` -> 15% of `maxHeapBytes`
   * - `"256m"`, `"256mb"`, `"1g"`, `"512k"`, or plain byte integer `"104857600"`
   */
  def parseMaxSizeBytes(raw: String, maxHeapBytes: Long = Runtime.getRuntime.maxMemory()): Option[Long] =
    raw.trimAllToOpt.flatMap { trimmed =>
      if (trimmed == "0" || trimmed == "-1") {
        None
      } else if (trimmed.endsWith("%")) {
        Try(trimmed.dropRight(1).trim.toDouble).toOption
          .filter(pct => pct > 0.0 && pct <= 100.0 && maxHeapBytes > 0L)
          .map(pct => math.max(1L, ((maxHeapBytes.toDouble * pct) / 100.0).toLong))
      } else {
        trimmed match {
          case ByteSpecRegex(numStr, unitOrNull) =>
            val multiplier = Option(unitOrNull).map(_.toLowerCase).getOrElse("b") match {
              case "b"                 => 1L
              case "k" | "kb" | "kib"  => 1024L
              case "m" | "mb" | "mib"  => 1024L * 1024L
              case "g" | "gb" | "gib"  => 1024L * 1024L * 1024L
              case _                   => 1L
            }
            Try((BigDecimal(numStr) * BigDecimal(multiplier)).toLong).toOption.filter(_ > 0L)
          case _ =>
            None
        }
      }
    }

  /**
   * Parse idle duration specifications such as:
   * - `""`, `"0"`, `"-1"` -> `None` (no idle expiration)
   * - `"30s"`, `"15m"`, `"2h"`, `"1d"`, `"500ms"`, ISO-8601 `"PT30M"`, or plain milliseconds `"1800000"`
   */
  def parseDurationMillis(raw: String): Option[Long] =
    raw.trimAllToOpt.flatMap { trimmed =>
      if (trimmed == "0" || trimmed == "-1") {
        None
      } else if (trimmed.headOption.exists(c => c == 'P' || c == 'p')) {
        Try(Duration.parse(trimmed.toUpperCase).toMillis).toOption.filter(_ > 0L)
      } else {
        trimmed match {
          case DurationSpecRegex(numStr, unitOrNull) =>
            val multiplier = Option(unitOrNull).map(_.toLowerCase).getOrElse("ms") match {
              case "ms" => 1L
              case "s"  => 1000L
              case "m"  => 60_000L
              case "h"  => 3_600_000L
              case "d"  => 86_400_000L
              case _    => 1L
            }
            Try(math.multiplyExact(numStr.toLong, multiplier)).toOption.filter(_ > 0L)
          case _ =>
            None
        }
      }
    }
}
