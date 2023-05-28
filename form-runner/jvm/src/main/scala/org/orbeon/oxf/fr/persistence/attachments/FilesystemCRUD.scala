/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import org.orbeon.io.IOUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.persistence.attachments.CRUD.AttachmentInformation
import org.orbeon.oxf.fr.{AppForm, FormOrData, FormRunnerPersistence}
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary
import org.orbeon.oxf.util.{LoggerFactory, XPathCache}
import org.orbeon.saxon.function.{EnvironmentVariable, EnvironmentVariableAlwaysEnabled}
import org.orbeon.xml.NamespaceMapping

import java.io._
import java.nio.file.{Files, Path, Paths}

trait FilesystemCRUD extends CRUDMethods {
  private val logger = LoggerFactory.createLogger(classOf[FilesystemCRUD])

  override def head(
    attachmentInformation : AttachmentInformation)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit = withFile(attachmentInformation, httpRequest, httpResponse) { fileToAccess =>
    ()
  }

  override def get(
    attachmentInformation : AttachmentInformation)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit = withFile(attachmentInformation, httpRequest, httpResponse) { fileToRead =>
    val fileInputStream = new FileInputStream(fileToRead)
    val outputStream = httpResponse.getOutputStream

    IOUtils.copyStreamAndClose(fileInputStream, outputStream)
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
    }

  override def delete(
    attachmentInformation : AttachmentInformation)(implicit
    httpRequest           : Request,
    httpResponse          : Response
  ): Unit =
    withFile(attachmentInformation, httpRequest, httpResponse) { fileToDelete =>
      if (!fileToDelete.delete()) {
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
    val baseDirectory = FilesystemCRUD.baseDirectory(attachmentInformation.appForm, attachmentInformation.formOrData)

    val pathSegments = Seq(
      attachmentInformation.appForm.app,
      attachmentInformation.appForm.form,
      attachmentInformation.formOrData.entryName) ++
      attachmentInformation.documentId.toSeq ++
      attachmentInformation.version.map(_.toString).toSeq :+
      attachmentInformation.filename

    val path = Paths.get(baseDirectory.toString, pathSegments: _*)

    path.toFile
  }
}

object FilesystemCRUD {
  def baseDirectory(appForm: AppForm, formOrData: FormOrData): Path = {
    val provider = FormRunnerPersistence.findAttachmentsProvider(
      appForm,
      formOrData
    ).getOrElse {
      val propertySegment = Seq(
        appForm.app,
        appForm.form,
        formOrData.entryName
      ).mkString(".")

      throw new OXFException(s"Could not find attachments provider for `$propertySegment`")
    }

    val DirectoryProperty = "directory"

    val rawDirectory = Option(FormRunnerPersistence.providerPropertyAsURL(
      provider,
      DirectoryProperty
    )).getOrElse {
      throw new OXFException(s"Could not find directory property for provider `$provider`")
    }

    val directoryNamespaces = NamespaceMapping(
      FormRunnerPersistence.providerPropertyOpt(provider, DirectoryProperty)
        .map(_.namespaces)
        .getOrElse(Map[String, String]())
    )

    val directory = evaluateAsAvt(rawDirectory, directoryNamespaces)

    val path = Paths.get(directory)

    if (!Files.exists(path)) {
      throw new OXFException(s"Directory `$directory` does not exist for provider `$provider`")
    }

    path.toRealPath()
  }

  object FunctionLibrary extends PipelineFunctionLibrary {
    override protected lazy val environmentVariableClass: Class[_ <: EnvironmentVariable] = classOf[EnvironmentVariableAlwaysEnabled]
  }

  def evaluateAsAvt(value: String, namespaceMapping: NamespaceMapping): String =
    XPathCache.evaluateAsAvt(
      contextItem = null,
      value,
      namespaceMapping,
      variableToValueMap = null,
      FunctionLibrary,
      functionContext = null,
      baseURI = null,
      locationData = null,
      reporter = null
    )
}