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

import java.io.ByteArrayOutputStream
import java.net.URI

import cats.syntax.option._
import org.orbeon.dom.Document
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.http.HttpMethod.HttpMethodsWithRequestBody
import org.orbeon.oxf.json.Converter
import org.orbeon.oxf.util.{ContentTypes, XPath}
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.util.control.NonFatal


case class SerializationParameters(
  messageBody            : Option[Array[Byte]],
  queryString            : String,
  actualRequestMediatype : String
)

object SerializationParameters {

  def apply(
    submission               : XFormsModelSubmission,
    p                        : SubmissionParameters,
    p2                       : SecondPassParameters,
    requestedSerialization   : String,
    documentToSubmit         : Document,
    overriddenSerializedData : String
  ): SerializationParameters = {

    // Actual request mediatype: the one specified by @mediatype, or the default mediatype for the serialization otherwise
    def actualRequestMediatype(default: String) =
      p.mediatypeOpt getOrElse default

    if (p.serialize) {
      requestedSerialization match {
        case _ if (overriddenSerializedData ne null) && overriddenSerializedData != "" =>
          // Form author set data to serialize
          if (HttpMethodsWithRequestBody(p.httpMethod)) {
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
          if (HttpMethodsWithRequestBody(p.httpMethod)) {
            SerializationParameters(
              messageBody            = SubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, p2.separator).getBytes(CharsetNames.Utf8).some,
              queryString            = null,
              actualRequestMediatype = actualRequestMediatype(serialization)
            )
          } else {
            SerializationParameters(
              messageBody            = None,
              queryString            = SubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, p2.separator),
              actualRequestMediatype = actualRequestMediatype(null)
            )
          }
        case serialization @ ContentTypes.XmlContentType =>
          try {

            val bytes =
              XFormsCrossPlatformSupport.serializeToByteArray(
                documentToSubmit,
                "xml",
                p2.encoding,
                p2.versionOpt,
                p2.indent,
                p2.omitXmlDeclaration,
                p2.standaloneOpt
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
                description = "serializing instance",
                throwable   = throwable
              )
          }
        case serialization @ "application/json" =>

          val result = Converter.xmlToJsonString(
            root   = new DocumentWrapper(documentToSubmit, null, XPath.GlobalConfiguration),
            strict = true
          )

          SerializationParameters(
              messageBody            = result.getBytes(p2.encoding).some,
              queryString            = null,
              actualRequestMediatype = actualRequestMediatype(serialization)
            )

        case serialization @ "multipart/related" =>
          // TODO
          throw new XFormsSubmissionException(
            submission  = submission,
            message     = s"xf:submission: submission serialization not yet implemented: $serialization",
            description = "serializing instance"
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
        case serialization @ "application/octet-stream" =>
          // Binary serialization
          val nodeType = InstanceData.getType(documentToSubmit.getRootElement)
          if (XMLConstants.XS_BASE64BINARY_QNAME == nodeType) {
            // TODO
            throw new XFormsSubmissionException(
              submission  = submission,
              message     = "xf:submission: binary serialization with base64Binary type is not yet implemented.",
              description = "serializing instance"
            )
          } else {
            // Default to anyURI
            // TODO: PERFORMANCE: Must pass InputStream all the way to the submission instead of storing into byte[] in memory!

            // NOTE: We support a relative path, in which case the path is resolved as a service URL
            val resolvedAbsoluteUrl =
              new URI(
                XFormsCrossPlatformSupport.resolveServiceURL(
                  submission.containingDocument,
                  submission.staticSubmission.element,
                  documentToSubmit.getRootElement.getStringValue,
                  URLRewriter.REWRITE_MODE_ABSOLUTE
                )
              )

            try {
              SerializationParameters(
                messageBody            = SubmissionUtils.readByteArray(submission.model, resolvedAbsoluteUrl).some,
                queryString            = null,
                actualRequestMediatype = actualRequestMediatype(serialization)
              )
            } catch {
              case NonFatal(throwable) =>
                throw new XFormsSubmissionException(
                  submission  = submission,
                  message     = "xf:submission: binary serialization with anyURI type failed reading URL.",
                  description = "serializing instance",
                  throwable   = throwable
                )
            }
          }
        case serialization @ (ContentTypes.HtmlContentType | ContentTypes.XhtmlContentType) =>
          // HTML or XHTML serialization
          try {

            val bytes =
              XFormsCrossPlatformSupport.serializeToByteArray(
                documentToSubmit,
                if (serialization == ContentTypes.HtmlContentType) "html" else "xhtml",
                p2.encoding,
                p2.versionOpt,
                p2.indent,
                p2.omitXmlDeclaration,
                p2.standaloneOpt
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
                description = "serializing instance",
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
                p2.encoding,
                None,
                false,
                true,
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
                description = "serializing instance",
                throwable   = throwable
              )
          }
        case serialization =>
          throw new XFormsSubmissionException(
            submission  = submission,
            message     = s"xf:submission: invalid submission serialization requested: $serialization",
            description = "serializing instance"
          )
      }
    } else {
      SerializationParameters(
        queryString            = null,
        messageBody            = None,
        actualRequestMediatype = null
      )
    }
  }
}