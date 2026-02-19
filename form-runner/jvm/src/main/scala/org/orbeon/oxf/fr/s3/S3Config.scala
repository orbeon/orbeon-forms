/**
 * Copyright (C) 2025 Orbeon, Inc.
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

package org.orbeon.oxf.fr.s3

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.saxon.function.Property.evaluateAsAvt
import org.orbeon.xml.NamespaceMapping
import software.amazon.awssdk.regions.Region

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Success, Try}


case class S3Config(endpoint: String, region: Region, bucket: String, accessKey: String, secretAccessKey: String, forcePathStyle: Boolean)

object S3Config {
  val PropertyPrefix        = "oxf.fr.s3."
  val DefaultEndpoint       = "s3.amazonaws.com"
  val DefaultRegion         = "aws-global"
  private val AwsRegionIds  = Region.regions().asScala.map(_.id()).toSet
  private val Logger        = LoggerFactory.createLogger(S3Config.getClass)

  def fromProperties(configName: String): Try[S3Config] = {

    def propertyName(valueName: String): String =
      s"$PropertyPrefix$configName.$valueName"

    def valueFromProperties(valueName: String): Option[String] = {

      val propertyNameForValue = propertyName(valueName)

      CoreCrossPlatformSupport.properties.getPropertyOpt(propertyNameForValue) map { property =>
        property.value match {
          case s: String => evaluateAsAvt(s, NamespaceMapping(property.namespaces))
          case _         => throw new Exception(s"String value expected for property $propertyNameForValue")
        }
      }
    }

    config(
      valueFromName     = valueFromProperties,
      emptyValueError   = valueName => s"Mandatory property ${propertyName(valueName)} must be non-empty",
      missingValueError = valueName => s"Mandatory property ${propertyName(valueName)} not found"
    )
  }

  def fromEnvironmentVariables(namePrefix: String, defaultBucket: Option[String] = None): Try[S3Config] = {

    def variableName(valueName: String): String =
      namePrefix + valueName.toUpperCase

    def valueFromEnvironmentVariables(valueName: String): Option[String] =
      Option(System.getenv(variableName(valueName))) orElse (valueName == "bucket").flatOption(defaultBucket)

    config(
      valueFromName     = valueFromEnvironmentVariables,
      emptyValueError   = valueName => s"Mandatory environment variable ${variableName(valueName)} must be non-empty",
      missingValueError = valueName => s"Mandatory environment variable ${variableName(valueName)} not found"
    )
  }

  def config(
    valueFromName    : String => Option[String],
    emptyValueError  : String => String,
    missingValueError: String => String
  ): Try[S3Config] = {

    def nonEmptyValue(name: String, defaultOpt: Option[String] = None): Try[String] =
      valueFromName(name).map(_.trimAllToEmpty) match {
        case Some("")          => Failure(new Exception(emptyValueError(name)))
        case Some(value)       => Success(value)
        case None              =>
          defaultOpt match {
            case Some(default) => Success(default)
            case None          => Failure(new Exception(missingValueError(name)))
          }
      }

    def booleanValue(name: String, default: Boolean): Try[Boolean] =
      valueFromName(name).map(_.trimAllToEmpty) match {
        case Some("true")  => Success(true)
        case Some("false") => Success(false)
        case Some("")      => Success(default)
        case Some(value)   => Failure(new Exception(s"Boolean value expected for $name, got: $value"))
        case None          => Success(default)
      }

    def regionTry: Try[Region] =
      nonEmptyValue("region", DefaultRegion.some).map { regionName =>
        if (! AwsRegionIds.contains(regionName))
          Logger.warn(s"using non-standard S3 region `$regionName`")
        Region.of(regionName)
      }

    for {
      endpoint        <- nonEmptyValue("endpoint", DefaultEndpoint.some)
      region          <- regionTry
      bucket          <- nonEmptyValue("bucket")
      accessKey       <- nonEmptyValue("accesskey")
      secretAccessKey <- nonEmptyValue("secretaccesskey")
      forcePathStyle  <- booleanValue("force-path-style", default = false)
    } yield
      S3Config(
        endpoint        = endpoint,
        region          = region,
        bucket          = bucket,
        accessKey       = accessKey,
        secretAccessKey = secretAccessKey,
        forcePathStyle  = forcePathStyle
      )
  }
}
