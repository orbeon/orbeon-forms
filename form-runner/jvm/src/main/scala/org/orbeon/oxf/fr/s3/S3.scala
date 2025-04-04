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

import org.orbeon.connection.Content
import org.orbeon.io.IOUtils
import org.orbeon.io.IOUtils.runQuietly
import org.orbeon.oxf.util.NetUtils
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*

import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.{Failure, Success, Try}


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
    key     : String,
    content : Content
  )(implicit
    s3Config: S3Config,
    s3Client: S3Client
  ): Try[PutObjectResponse] = Try {

    val putObjectRequestBuilder = PutObjectRequest.builder().bucket(s3Config.bucket).key(key)

    val putObjectRequest = content.contentType match {
      case Some(contentType) => putObjectRequestBuilder.contentType(contentType).build()
      case None              => putObjectRequestBuilder.build()
    }

    // If we know the content length, we can pass the input stream to the AWS S3 SDK directly; if not, we'll download
    // the stream into a byte array and pass that to the SDK
    val requestBody = content.contentLength match {
      case Some(contentLength) => RequestBody.fromInputStream(content.stream, contentLength)
      case None                => RequestBody.fromBytes(NetUtils.inputStreamToByteArray(content.stream))
    }

    try {
      s3Client.putObject(putObjectRequest, requestBody)
    } finally {
      // We have to close the input stream in both cases above (not closed neither by AWS SDK nor by NetUtils)
      content.stream.close()
    }
  }

  def objectAsString(
    bucket  : String,
    key     : String
  )(implicit
    s3Client: S3Client
  ): Try[String] = {
    val getObjectRequest = GetObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build()

    Try(IOUtils.readStreamAsStringAndClose(s3Client.getObject(getObjectRequest), charset = None))
  }

  def headBucket(bucketName: String)(implicit s3Client: S3Client): Try[HeadBucketResponse] = {
    val headBucketRequest = HeadBucketRequest.builder().bucket(bucketName).build()
    Try(s3Client.headBucket(headBucketRequest))
  }

  def bucketExists(bucketName: String)(implicit s3Client: S3Client): Try[Boolean] =
    headBucket(bucketName) match {
      case Success(_)                        => Success(true)
      case Failure(_: NoSuchBucketException) => Success(false)
      case Failure(t)                        => Failure(t)
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
    val listObjectsRequest  = ListObjectsRequest.builder().bucket(bucketName).prefix(prefix).build()
    val listObjectsResponse = s3Client.listObjects(listObjectsRequest)
    Try(listObjectsResponse.contents().asScala.toList)
  }

  def objectMetadata(bucketName: String, key: String)(implicit s3Client: S3Client): Try[HeadObjectResponse] = {
    val headObjectRequest = HeadObjectRequest.builder().bucket(bucketName).key(key).build()
    Try(s3Client.headObject(headObjectRequest))
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
