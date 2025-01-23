/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.submission

import cats.syntax.option.*
import org.orbeon.dom.Document
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.http.HttpMethod.HttpMethodsWithRequestBody
import org.orbeon.oxf.json.Converter
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.io.ByteArrayOutputStream
import java.net.URI
import scala.util.control.NonFatal


case class SerializationParameters(
  messageBody            : Option[Array[Byte]], // TODO: this should be a stream
  queryString            : String,
  actualRequestMediatype : String
)

object SerializationParameters {

  def apply(
    submission              : XFormsModelSubmission,
    submissionParameters    : SubmissionParameters,
    requestedSerialization  : String,
    documentToSubmitOpt     : Option[URI Either Document],
    overriddenSerializedData: String
  )(implicit
    refContext          : RefContext
  ): SerializationParameters = {

    // Actual request mediatype: the one specified by `@mediatype`, or the default mediatype for the serialization otherwise
    def actualRequestMediatype(default: String): String =
      submissionParameters.mediatypeOpt getOrElse default

    documentToSubmitOpt match {
      case Some(Left(uri)) =>
        requestedSerialization match {
          case serialization @ ContentTypes.OctetStreamContentType =>
            InstanceData.getType(refContext.refNodeInfo) match {
              case XMLConstants.XS_BASE64BINARY_QNAME =>
                // TODO
                throw new XFormsSubmissionException(
                  submission  = submission,
                  message     = "xf:submission: binary serialization with base64Binary type is not yet implemented.",
                  description = ErrorDescription
                )
              case _ =>
                // Default to anyURI
                // TODO: PERFORMANCE: Must pass InputStream all the way to the submission instead of storing into byte[] in memory!

                // NOTE: We support a relative path, in which case the path is resolved as a service URL
                val resolvedAbsoluteUrl =
                  URI.create(
                    XFormsCrossPlatformSupport.resolveServiceURL(
                      submission.containingDocument,
                      submission.staticSubmission.element,
                      uri.toString,
                      UrlRewriteMode.Absolute
                    )
                  )

                try {
                  implicit val logger          : IndentedLogger = submission.model.indentedLogger
                  implicit val externalContext : ExternalContext = XFormsCrossPlatformSupport.externalContext
                  implicit val resourceResolver: Option[ResourceResolver] = submission.containingDocument.staticState.resourceResolverOpt

                  SerializationParameters(
                    messageBody            = SubmissionUtils.readByteArray(submission.containingDocument.headersGetter, resolvedAbsoluteUrl).some,
                    queryString            = null,
                    actualRequestMediatype = actualRequestMediatype(serialization)
                  )
                } catch {
                  case NonFatal(throwable) =>
                    throw new XFormsSubmissionException(
                      submission  = submission,
                      message     = "xf:submission: binary serialization with anyURI type failed reading URL.",
                      description = ErrorDescription,
                      throwable   = throwable
                    )
                }
            }
          case serialization =>
            throw new XFormsSubmissionException(
              submission  = submission,
              message     = s"xf:submission: illegal state: invalid submission serialization requested: $serialization",
              description = ErrorDescription
            )
        }
      case Some(Right(documentToSubmit)) =>

        requestedSerialization match {
          case _ if (overriddenSerializedData ne null) && overriddenSerializedData != "" =>
            // Form author set data to serialize
            if (HttpMethodsWithRequestBody(submissionParameters.httpMethod)) {
              SerializationParameters(
                messageBody            = overriddenSerializedData.getBytes(CharsetNames.Utf8).some,
                queryString            = null,
                actualRequestMediatype = actualRequestMediatype(ContentTypes.XmlContentType)
              )
            } else {
              SerializationParameters(
                messageBody            = None,
                queryString            = overriddenSerializedData.encode,
                actualRequestMediatype = actualRequestMediatype(null)
              )
            }
          case serialization @ "application/x-www-form-urlencoded" =>
            if (HttpMethodsWithRequestBody(submissionParameters.httpMethod)) {
              SerializationParameters(
                messageBody            = SubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, submissionParameters.separator).getBytes(CharsetNames.Utf8).some,
                queryString            = null,
                actualRequestMediatype = actualRequestMediatype(serialization)
              )
            } else {
              SerializationParameters(
                messageBody            = None,
                queryString            = SubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, submissionParameters.separator),
                actualRequestMediatype = actualRequestMediatype(null)
              )
            }
          case serialization @ ContentTypes.XmlContentType =>
            try {

              val bytes =
                XFormsCrossPlatformSupport.serializeToByteArray(
                  documentToSubmit,
                  "xml",
                  submissionParameters.encoding,
                  submissionParameters.versionOpt,
                  submissionParameters.indent,
                  submissionParameters.omitXmlDeclaration,
                  submissionParameters.standaloneOpt
                )

              SerializationParameters(
                messageBody            = bytes.some,
                queryString            = null,
                actualRequestMediatype = actualRequestMediatype(serialization)
              )
            } catch {
              case NonFatal(throwable) =>
                throw new XFormsSubmissionException(
                  submission  = submission,
                  message     = "xf:submission: exception while serializing instance to XML.",
                  description = ErrorDescription,
                  throwable   = throwable
                )
            }
          case serialization @ "application/json" =>

            val result = Converter.xmlToJsonString(
              root   = new DocumentWrapper(documentToSubmit, null, XPath.GlobalConfiguration),
              strict = true
            )

            SerializationParameters(
                messageBody            = result.getBytes(submissionParameters.encoding).some,
                queryString            = null,
                actualRequestMediatype = actualRequestMediatype(serialization)
              )

          case serialization @ "multipart/related" =>
            // TODO
            throw new XFormsSubmissionException(
              submission  = submission,
              message     = s"xf:submission: submission serialization not yet implemented: $serialization",
              description = ErrorDescription
            )
          case "multipart/form-data" =>
            // Build multipart/form-data body
            val os = new ByteArrayOutputStream
            val multipartContentType = XFormsCrossPlatformSupport.writeMultipartFormData(documentToSubmit, os)

            // The mediatype also contains the boundary
            SerializationParameters(
              messageBody            = os.toByteArray.some,
              queryString            = null,
              actualRequestMediatype = actualRequestMediatype(multipartContentType)
            )
          case serialization @ ContentTypes.OctetStreamContentType =>
            throw new XFormsSubmissionException(
              submission  = submission,
              message     = s"xf:submission: illegal state: invalid submission serialization requested: $serialization",
              description = ErrorDescription
            )
          case serialization @ (ContentTypes.HtmlContentType | ContentTypes.XhtmlContentType) =>
            // HTML or XHTML serialization
            try {

              val bytes =
                XFormsCrossPlatformSupport.serializeToByteArray(
                  documentToSubmit,
                  if (serialization == ContentTypes.HtmlContentType) "html" else "xhtml",
                  submissionParameters.encoding,
                  submissionParameters.versionOpt,
                  submissionParameters.indent,
                  submissionParameters.omitXmlDeclaration,
                  submissionParameters.standaloneOpt
                )

              SerializationParameters(
                messageBody            = bytes.some,
                queryString            = null,
                actualRequestMediatype = actualRequestMediatype(serialization)
              )
            } catch {
              case NonFatal(throwable) =>
                throw new XFormsSubmissionException(
                  submission  = submission,
                  message     = "xf:submission: exception while serializing instance to HTML or XHTML.",
                  description = ErrorDescription,
                  throwable   = throwable
                )
            }
          case serialization if ContentTypes.isTextOrJSONContentType(serialization) =>
            // Text serialization
            try {
              val bytes =
                XFormsCrossPlatformSupport.serializeToByteArray(
                  documentToSubmit,
                  "text",
                  submissionParameters.encoding,
                  None,
                  indent = false,
                  omitXmlDeclaration = true,
                  false.some
                )

              SerializationParameters(
                messageBody            = bytes.some,
                queryString            = null,
                actualRequestMediatype = actualRequestMediatype(serialization)
              )
            } catch {
              case NonFatal(throwable) =>
                throw new XFormsSubmissionException(
                  submission  = submission,
                  message     = "xf:submission: exception while serializing instance to text.",
                  description = ErrorDescription,
                  throwable   = throwable
                )
            }
          case serialization =>
            throw new XFormsSubmissionException(
              submission  = submission,
              message     = s"xf:submission: invalid submission serialization requested: $serialization",
              description = ErrorDescription
            )
        }
      case None =>
        SerializationParameters(
          queryString            = null,
          messageBody            = None,
          actualRequestMediatype = null
        )
    }
  }

  private val ErrorDescription = "serializing instance"
}