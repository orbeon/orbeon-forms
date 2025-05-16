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
package org.orbeon.oxf.fr.persistence.attachments

import cats.implicits.catsSyntaxOptionId
import org.log4s.Logger
import org.orbeon.connection.StreamedContent
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.persistence.attachments.CRUD.AttachmentInformation
import org.orbeon.oxf.fr.s3.{S3, S3Config}
import org.orbeon.oxf.fr.{AppForm, FormOrData}
import org.orbeon.oxf.http.{HttpRange, HttpRanges, StatusCode}
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{NoSuchBucketException, NoSuchKeyException, S3Exception}


trait S3CRUD extends CRUDMethods {
  private implicit val logger: Logger = LoggerFactory.createLogger(classOf[S3CRUD])

  // We let exceptions propagate up to withS3Context, which will set the HTTP status code accordingly in case of error

  override def head(
    attachmentInformation : AttachmentInformation,
    httpRanges            : HttpRanges)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit = withS3Context(attachmentInformation, httpRequest, httpResponse) { s3Context =>

    implicit val s3Config: S3Config = s3Context.s3Config
    implicit val s3Client: S3Client = s3Context.s3Client

    val key          = s3Context.key(attachmentInformation)
    val objectLength = S3.objectMetadata(s3Config.bucket, key).get.contentLength()

    httpResponse.addHeaders(HttpRanges.acceptRangesHeader(objectLength))
    httpResponse.setStatus(StatusCode.Ok)
  }

  override def get(
    attachmentInformation : AttachmentInformation,
    httpRanges            : HttpRanges)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit = withS3Context(attachmentInformation, httpRequest, httpResponse) { s3Context =>

    implicit val s3Config: S3Config = s3Context.s3Config
    implicit val s3Client: S3Client = s3Context.s3Client

    val key = s3Context.key(attachmentInformation)

    CRUDMethods.get(
      httpRanges         = httpRanges,
      length             = S3.objectMetadata(s3Config.bucket, key).get.contentLength(),
      partialInputStream = (httpRange: HttpRange) => S3.inputStream(key, httpRange.some).get,
      fullInputStream    = S3.inputStream(key, httpRangeOpt = None).get,
    )
  }

  override def put(
    attachmentInformation : AttachmentInformation)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit =
    withS3Context(attachmentInformation, httpRequest, httpResponse) { s3Context =>

      implicit val s3Config: S3Config = s3Context.s3Config
      implicit val s3Client: S3Client = s3Context.s3Client

      val key = s3Context.key(attachmentInformation)

      val content = StreamedContent(
        inputStream   = httpRequest.getInputStream,
        contentType   = httpRequest.getContentType.some,
        contentLength = httpRequest.contentLengthOpt,
        title         = None
      )

      S3.write(key, content).get

      httpResponse.setStatus(StatusCode.NoContent)
    }

  override def delete(
    attachmentInformation : AttachmentInformation)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit =
    withS3Context(attachmentInformation, httpRequest, httpResponse) { s3Context =>

      implicit val s3Config: S3Config = s3Context.s3Config
      implicit val s3Client: S3Client = s3Context.s3Client

      val key = s3Context.key(attachmentInformation)

      S3.deleteObject(s3Config.bucket, key).get

      httpResponse.setStatus(StatusCode.NoContent)
    }

  private def withS3Context(
    attachmentInformation : AttachmentInformation,
    httpRequest           : Request,
    httpResponse          : Response)(
    body                  : S3CRUD.S3Context => Unit
  ): Unit = {

    val config                      = S3CRUD.config(attachmentInformation.appForm, attachmentInformation.formOrData)
    implicit val s3Config: S3Config = config.s3Config

    S3.withS3Client { s3Client =>

      val s3Context  = S3CRUD.S3Context(s3Client, s3Config, config.basePath)
      val s3Key      = s3Context.key(attachmentInformation)
      val s3Location = List(s3Config.endpoint, s3Config.bucket, s3Key).mkString(" / ")

      logger.info(s"S3 attachments provider: ${httpRequest.getMethod} $s3Location")

      try {
        body(s3Context)
      } catch {
        case e: NoSuchKeyException =>
          logger.error(e)(s"S3 object not found for path ${httpRequest.getRequestPath}")
          httpResponse.setStatus(StatusCode.NotFound)
        case e: NoSuchBucketException =>
          logger.error(e)(s"S3 bucket not found for path ${httpRequest.getRequestPath}")
          httpResponse.setStatus(StatusCode.NotFound)
        case e: S3Exception =>
          logger.error(e)(s"S3 error for path ${httpRequest.getRequestPath}")
          httpResponse.setStatus(StatusCode.InternalServerError)
      }
    }
  }
}

object S3CRUD extends CRUDConfig {
  case class Config(provider: String, s3Config: S3Config, basePath: String)

  type C = Config

  case class S3Context(s3Client: S3Client, s3Config: S3Config, basePath: String) {
    def key(attachmentInformation : AttachmentInformation): String =
      (basePath +: attachmentInformation.pathSegments)
        .flatMap(_.trimAllToOpt)
        .map(_.stripPrefix("/").stripSuffix("/"))
        .mkString("/")
  }

  override def config(appForm: AppForm, formOrData: FormOrData): Config = {
    val provider     = this.provider(appForm, formOrData)
    val s3ConfigName = providerProperty(provider, "s3-config", defaultOpt = "default".some)
    val s3Config     = S3Config.fromProperties(s3ConfigName).get
    val basePath     = providerProperty(provider, "base-path", defaultOpt = "".some)

    // TODO: should we interpret basePath as AVT (evaluateAsAvt), like with "filesystem" attachments providers?

    Config(provider, s3Config, basePath)
  }
}