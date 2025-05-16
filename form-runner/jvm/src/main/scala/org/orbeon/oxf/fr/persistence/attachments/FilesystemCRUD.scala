/**
 * Copyright (C) 2023 Orbeon, Inc.
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

import org.log4s.Logger
import org.orbeon.io.IOUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.persistence.attachments.CRUD.AttachmentInformation
import org.orbeon.oxf.fr.{AppForm, FormOrData}
import org.orbeon.oxf.http.{FileRangeInputStream, HttpRange, HttpRanges, StatusCode}
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.saxon.function.Property.evaluateAsAvt

import java.io.*
import java.nio.file.{Files, Path, Paths}
import scala.util.Try


trait FilesystemCRUD extends CRUDMethods {
  private implicit val logger: Logger = LoggerFactory.createLogger(classOf[FilesystemCRUD])

  // We let exceptions propagate up to withFile, which will set the HTTP status code accordingly in case of error

  override def head(
    attachmentInformation : AttachmentInformation,
    httpRanges            : HttpRanges)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit =
    withFile(attachmentInformation, httpRequest, httpResponse) { fileToAccess =>
      if (fileToAccess.exists()) {
        httpResponse.addHeaders(HttpRanges.acceptRangesHeader(fileToAccess.length()))
        httpResponse.setStatus(StatusCode.Ok)
      } else {
        httpResponse.setStatus(StatusCode.NotFound)
      }
    }

  override def get(
    attachmentInformation : AttachmentInformation,
    httpRanges            : HttpRanges)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit =
    withFile(attachmentInformation, httpRequest, httpResponse) { fileToRead =>
      CRUDMethods.get(
        httpRanges         = httpRanges,
        length             = fileToRead.length(),
        partialInputStream = (httpRange: HttpRange) => FileRangeInputStream(fileToRead, httpRange),
        fullInputStream    = new FileInputStream(fileToRead),
      )
    }

  override def put(
    attachmentInformation : AttachmentInformation)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit =
    withFile(attachmentInformation, httpRequest, httpResponse) { fileToWrite =>
      fileToWrite.getParentFile.mkdirs()

      val inputStream      = httpRequest.getInputStream
      val fileOutputStream = new FileOutputStream(fileToWrite)

      IOUtils.copyStreamAndClose(inputStream, fileOutputStream)

      httpResponse.setStatus(StatusCode.NoContent)
    }

  override def delete(
    attachmentInformation : AttachmentInformation)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit =
    withFile(attachmentInformation, httpRequest, httpResponse) { fileToDelete =>
      if (fileToDelete.delete()) {
        httpResponse.setStatus(StatusCode.NoContent)
      } else {
        httpResponse.setStatus(StatusCode.InternalServerError)
      }
    }

  private def withFile(
    attachmentInformation : AttachmentInformation,
    httpRequest           : Request,
    httpResponse          : Response)(
    body                  : File => Unit
  ): Unit = {
    val fileToAccess = file(attachmentInformation)

    logger.info(s"Filesystem attachments provider: ${httpRequest.getMethod} ${fileToAccess.getAbsolutePath}")

    try {
      body(fileToAccess)
    } catch {
      case e: FileNotFoundException =>
        logger.error(e)(s"File not found for path ${httpRequest.getRequestPath}")
        httpResponse.setStatus(StatusCode.NotFound)
      case e: SecurityException =>
        logger.error(e)(s"Security violation for path ${httpRequest.getRequestPath}")
        httpResponse.setStatus(StatusCode.InternalServerError)
      case e: IOException =>
        logger.error(e)(s"I/O error for path ${httpRequest.getRequestPath}")
        httpResponse.setStatus(StatusCode.InternalServerError)
    }
  }

  private def file(attachmentInformation: AttachmentInformation): File = {
    val basePath = FilesystemCRUD.basePath(attachmentInformation.appForm, attachmentInformation.formOrData)
    val path     = Paths.get(basePath.toString, attachmentInformation.pathSegments*)

    path.toFile
  }
}

object FilesystemCRUD extends CRUDConfig {
  case class Config(provider: String, basePath: String)

  type C = Config

  override def config(appForm: AppForm, formOrData: FormOrData): Config = {
    val provider = this.provider(appForm, formOrData)

    val (rawBasePath, basePathNamespaces) =
      Try(providerPropertyWithNs(provider, "base-path", defaultOpt = None)).getOrElse {
        // Compatibility property
        providerPropertyWithNs(provider, "directory", defaultOpt = None)
      }

    // Evaluate the base path as an AVT
    Config(provider, evaluateAsAvt(rawBasePath, basePathNamespaces))
  }

  def basePath(appForm: AppForm, formOrData: FormOrData): Path = {
    val config = this.config(appForm, formOrData)
    val path   = Paths.get(config.basePath)

    if (!Files.exists(path)) {
      throw new OXFException(s"Directory `${config.basePath}` does not exist for provider `${config.provider}`")
    }

    path.toRealPath()
  }
}