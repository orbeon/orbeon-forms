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
import org.orbeon.io.IOUtils.runQuietly
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, NetUtils}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, PutObjectResponse}

import java.io.{ByteArrayInputStream, InputStream}
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Success, Try}

object S3 {
  case class Config(endpoint: String, region: Region, bucket: String, accessKey: String, secretAccessKey: String)

  object Config {
    val PropertyPrefix = "oxf.fr.s3."

    private val DefaultEndpoint = "s3.amazonaws.com"
    private val DefaultRegion   = "aws-global"

    def fromName(configName: String): Try[Config] = {
      val prefix      = s"$PropertyPrefix$configName."
      val propertySet = CoreCrossPlatformSupport.properties

      def value(name: String, defaultOpt: Option[String] = None): Try[String] = {
        val propertyName = prefix + name

        propertySet.getNonBlankString(propertyName) match {
          case Some(value)       => Success(value)
          case None              =>
            defaultOpt match {
              case Some(default) => Success(default)
              case None          => Failure(new Exception(s"Mandatory property $propertyName not found"))
            }
        }
      }

      def regionTry: Try[Region] =
        value("region", DefaultRegion.some).flatMap { regionName =>
          // Convert region name into Region instance
          Region.regions().asScala.find(_.id() == regionName) match {
            case Some(region) => Success(region)
            case None         => Failure(new Exception(s"Invalid region $regionName"))
          }
        }

      for {
        endpoint        <- value("endpoint", DefaultEndpoint.some)
        region          <- regionTry
        bucket          <- value("bucket")
        accessKey       <- value("accesskey")
        secretAccessKey <- value("secretaccesskey")
      } yield
        Config(
          endpoint        = endpoint,
          region          = region,
          bucket          = bucket,
          accessKey       = accessKey,
          secretAccessKey = secretAccessKey
        )
    }
  }

  def write(key: String, bytes: Array[Byte])(implicit config: Config): Try[PutObjectResponse] = {
    val inputStream = new ByteArrayInputStream(bytes)

    try {
      write(key, inputStream, bytes.length.toLong.some)
    } finally {
      runQuietly(inputStream.close())
    }
  }

  def write(
    key             : String,
    inputStream     : InputStream,
    contentLengthOpt: Option[Long]
  )(implicit
    config          : Config
  ): Try[PutObjectResponse] = Try {
    val credentials = AwsBasicCredentials.create(config.accessKey, config.secretAccessKey)

    val s3Client = S3Client.builder()
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .region(config.region)
      .build()

    val putObjectRequest = PutObjectRequest.builder().bucket(config.bucket).key(key).build()

    // If we know the content length, we can pass the input stream to the AWS S3 SDK directly; if not, we'll download
    // the stream into a byte array and pass that to the SDK
    val (requestBody, inputStreamToCloseOpt) = contentLengthOpt match {
      case Some(contentLength) => (RequestBody.fromInputStream(inputStream, contentLength),             inputStream.some)
      case None                => (RequestBody.fromBytes(NetUtils.inputStreamToByteArray(inputStream)), None)
    }

    try {
      s3Client.putObject(putObjectRequest, requestBody)
    } finally {
      inputStreamToCloseOpt.foreach(_.close())
    }
  }
}
