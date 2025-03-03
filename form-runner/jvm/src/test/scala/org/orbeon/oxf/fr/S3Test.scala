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
package org.orbeon.oxf.fr

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.fr.s3.{S3, S3Config}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.scalatest.funspec.AnyFunSpecLike
import software.amazon.awssdk.services.s3.S3Client

import java.util.UUID


class S3Test
  extends DocumentTestBase
    with ResourceManagerSupport
    with AnyFunSpecLike
    with FormRunnerSupport {


  def withTestS3Config(configName: String)(body: S3Config => Unit): Unit = {

    implicit val s3Config: S3Config = S3Config.fromProperties(configName).get

    S3.withS3Client { implicit s3Client =>
      def emptyBucket(): Unit = S3.objects(s3Config.bucket).get foreach { s3Object =>
        S3.deleteObject(s3Config.bucket, s3Object.key).get
      }

      // Create a temporary bucket (if it doesn't exist yet)...
      S3.createBucket(s3Config.bucket).get

      // ...and make sure it is empty
      emptyBucket()

      try {
        body(s3Config)
      } finally {
        // Delete all objects in the bucket...
        emptyBucket()

        // ...and then the bucket itself
        S3.deleteBucket(s3Config.bucket).get
      }
    }
  }

  describe("Form Runner S3 storage") {

    describe("#6751: Store all attachments sent by email in AWS S3") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "6751", "new", initialize = true)

      val doc = docOpt.get

      def testWithS3Config(configName: String)(body: S3Config => S3Client => Unit): Unit =
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            withTestS3Config(configName) { implicit s3Config =>
              S3.withS3Client { implicit s3Client =>
                body(s3Config)(s3Client)
              }
            }
          }
        }

      // Disable actual email sending, just test the S3 storage
      val baseParams = Map[Option[String], String]("send-email".some -> "false")

      def sendEmail(param: (String, String)*): Unit = {
        process.SimpleProcess.trySendEmail(baseParams ++ param.toMap.map(kv => kv._1.some -> kv._2))
      }

      it("must leave S3 bucket untouched if S3 storage disabled") {
        testWithS3Config(configName = "issue-6751") { implicit s3Config => implicit s3Client =>
          sendEmail()
          assert(S3.objects(s3Config.bucket).get.isEmpty)
        }
      }

      it("must store some S3 objects in the bucket if S3 storage enabled") {
        testWithS3Config(configName = "issue-6751") { implicit s3Config => implicit s3Client =>
          val s3Path = UUID.randomUUID().toString

          sendEmail("s3-store" -> "true", "s3-config" -> "issue-6751", "s3-path" -> s"'$s3Path'")
          assert(S3.objects(s3Config.bucket, prefix = s3Path).get.nonEmpty)
        }
      }
    }
  }
}

