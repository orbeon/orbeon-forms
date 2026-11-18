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

  val DefaultCacheName = "cache.main"
  private val DefaultSize = 200

  private val CachePropertyNamePrefix      = "oxf"
  private val CachePropertyNameSizeSuffix  = "size"
  private val CachePropertyMaxBytesSuffix  = "max-size-bytes"
  private val CachePropertyMaxIdleSuffix   = "max-idle"

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
  def instance(cacheName: String, defaultSize: Int): Cache =
    namedObjectCaches.computeIfAbsent(
      cacheName,
      _ => {
        registeredDefaults.put(cacheName, defaultSize)
        val cache = new MemoryCacheImpl(defaultSize)
        Try(Option(Properties.instance.getPropertySet)).toOption.flatten.foreach { ps =>
          configureCacheFromPropertySet(cacheName, defaultSize, cache, ps)
        }
        cache
      }
    )

  def findCache(cacheName: String): Option[Cache] =
    Option(namedObjectCaches.get(cacheName))

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

  private def propertyPrefixesFor(cacheName: String): List[String] =
    if (cacheName == DefaultCacheName)
      List("oxf.cache", s"$CachePropertyNamePrefix.$DefaultCacheName")
    else
      List(s"$CachePropertyNamePrefix.$cacheName")

  private def readStringOrNumericOpt(propertySet: PropertySet, prefixes: List[String], suffix: String): Option[String] =
    prefixes.iterator
      .flatMap(prefix => propertySet.getObjectOpt(s"$prefix.$suffix"))
      .map(_.toString)
      .flatMap(_.trimAllToOpt)
      .nextOption()

  def configureCacheFromPropertySet(
    cacheName  : String,
    defaultSize: Int,
    cache      : Cache,
    propertySet: PropertySet
  ): Unit = {
    val prefixes = propertyPrefixesFor(cacheName)

    val resolvedMaxSize =
      prefixes.iterator
        .flatMap(prefix => propertySet.getIntOpt(s"$prefix.$CachePropertyNameSizeSuffix"))
        .nextOption()
        .getOrElse(defaultSize)

    val resolvedMaxBytes =
      readStringOrNumericOpt(propertySet, prefixes, CachePropertyMaxBytesSuffix)
        .flatMap(parseMaxSizeBytes(_, Runtime.getRuntime.maxMemory()))

    val resolvedMaxIdle =
      readStringOrNumericOpt(propertySet, prefixes, CachePropertyMaxIdleSuffix)
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
