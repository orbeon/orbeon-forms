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
import org.orbeon.oxf.util.NetUtils
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*

import java.io.{ByteArrayInputStream, InputStream}
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Try


object S3 {
  def withS3Client[T](body: S3Client => T)(implicit s3Config: S3Config): T = {
    val client = newS3Client()

    try {
      body(client)
    } finally {
      runQuietly(client.close())
    }
  }

  def write(
    key: String, bytes: Array[Byte]
  )(implicit
    s3Config        : S3Config,
    s3Client        : S3Client
  ): Try[PutObjectResponse] = {
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
    s3Config        : S3Config,
    s3Client        : S3Client
  ): Try[PutObjectResponse] = Try {

    val putObjectRequest = PutObjectRequest.builder().bucket(s3Config.bucket).key(key).build()

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

  def createBucket(bucketName: String)(implicit s3Client: S3Client): Try[CreateBucketResponse] = {
    val createBucketRequest = CreateBucketRequest.builder().bucket(bucketName).build()
    Try(s3Client.createBucket(createBucketRequest))
  }

  def deleteBucket(bucketName: String)(implicit s3Client: S3Client): Try[DeleteBucketResponse] = {
    val deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build()
    Try(s3Client.deleteBucket(deleteBucketRequest))
  }

  // No streaming for now
  def objects(bucketName: String, prefix: String = "")(implicit s3Client: S3Client): Try[List[S3Object]] = {
    val listObjectsRequest = ListObjectsRequest.builder().bucket(bucketName).prefix(prefix).build()
    val listObjectsResponse = s3Client.listObjects(listObjectsRequest)
    Try(listObjectsResponse.contents().asScala.toList)
  }

  def deleteObject(bucketName: String, key: String)(implicit s3Client: S3Client): Try[DeleteObjectResponse] = {
    val deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(key).build()
    Try(s3Client.deleteObject(deleteObjectRequest))
  }

  def newS3Client()(implicit config: S3Config): S3Client = {
    val credentials = AwsBasicCredentials.create(config.accessKey, config.secretAccessKey)

    S3Client.builder()
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .region(config.region)
      .build()
  }
}
