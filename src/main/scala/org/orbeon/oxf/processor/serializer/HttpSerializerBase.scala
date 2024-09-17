/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.processor.serializer

import org.orbeon.dom.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.controller.PageFlowControllerProcessor
import org.orbeon.oxf.externalcontext.{ExternalContext, ResponseWrapper}
import org.orbeon.oxf.http.{PathType, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl.*
import org.orbeon.oxf.processor.ProcessorUtils.selectBooleanValue
import org.orbeon.oxf.processor.serializer.CachedSerializer.*
import org.orbeon.oxf.processor.serializer.store.ResultStoreOutputStream
import org.orbeon.oxf.processor.{CacheableInputReader, ProcessorInput, ProcessorInputOutputInfo}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{ContentTypes, LoggerFactory, URLRewriterUtils}
import org.orbeon.oxf.xml.XPathUtils.*

import java.io.OutputStream
import java.net.SocketException
import java.lang as jl
import scala.collection.mutable
import scala.jdk.CollectionConverters.*


object HttpSerializerBase {

  private val logger = LoggerFactory.createLogger(classOf[HttpSerializerBase])

  private val DefaultStatusCode: Int = StatusCode.Ok

  private val DefaultForceContentType          = false
  private val DefaultIgnoreDocumentContentType = false
  private val DefaultForceEncoding             = false
  private val DefaultIgnoreDocumentEncoding    = false

  /**
   * Represent the complete serializer configuration.
   */
  case class Config(

    // HTTP-specific configuration
    statusCode               : Int,
    errorCode                : Int,
    contentTypeOpt           : Option[String],
    forceContentType         : Boolean,
    ignoreDocumentContentType: Boolean,
    encodingOpt              : Option[String],
    forceEncoding            : Boolean,
    ignoreDocumentEncoding   : Boolean,
    headers                  : List[(String, String)],
    headersToForward         : List[String],
    cacheUseLocalCache       : Boolean,
    empty                    : Boolean,

    // XML / HTML / Text configuration
    methodOpt                : Option[String],
    versionOpt               : Option[String],
    publicDoctypeOpt         : Option[String],
    systemDoctypeOpt         : Option[String],
    omitXMLDeclaration       : Boolean,
    standalone               : Option[Boolean],
    indent                   : Boolean,
    indentAmount             : Int,
  ) {
    def contentTypeOrNull  : String     = contentTypeOpt.orNull
    def encodingOrNull     : String     = encodingOpt.orNull

    def methodOr (default: String): String = methodOpt.getOrElse(default)
    def versionOr(default: String): String = versionOpt.getOrElse(default)

    def publicDoctypeOrNull: String     = publicDoctypeOpt.orNull
    def systemDoctypeOrNull: String     = systemDoctypeOpt.orNull
    def standaloneOrNull   : jl.Boolean = standalone.map(Boolean.box).orNull

    def contentTypeOrDefault(default: String): String =
      if (forceContentType)
        contentTypeOpt.getOrElse(throw new IllegalStateException)
      else
        contentTypeOpt.getOrElse(default)

    def encodingOrDefault(default: String): String =
      if (forceEncoding)
        encodingOpt.getOrElse(throw new IllegalStateException)
      else
        encodingOpt.getOrElse(default)

    def encodingWithCharset(defaultContentType: String, defaultCharset: String): String =
      ContentTypes.makeContentTypeCharset(encodingOrDefault(defaultContentType), Some(encodingOrDefault(defaultCharset)))
  }

  /**
   * ResultStoreOutputStream with additional content-type storing.
   */
  private class ExtendedResultStoreOutputStream(out: OutputStream)
    extends ResultStoreOutputStream(out) {

    var contentType: Option[String] = None
    var status     : Option[Int]    = None

    private var headers: mutable.LinkedHashMap[String, String] = _

    def setHeader(name: String, value: String): Unit = {
      if (headers == null)
        headers = new mutable.LinkedHashMap[String, String]
      headers.put(name, value)
    }

    def getHeaders: Iterable[(String, String)] = if (headers ne null) headers else Nil
  }
}

/**
 * Base class for all HTTP serializers.
 */
abstract class HttpSerializerBase protected
  extends CachedSerializer[HttpSerializerBase.Config] {

  import HttpSerializerBase._

  addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, getConfigSchemaNamespaceURI))

  /**
   * Return the default content type for this serializer. Must be overridden by subclasses.
   */
//  protected
  def getDefaultContentType: String

  /**
   * Return the namespace URI of the schema validating the config input. Can be overridden by
   * subclasses.
   */
  protected def getConfigSchemaNamespaceURI: String = SerializerConfigNamespaceUri

  override def start(pipelineContext: PipelineContext): Unit = {

    val config    = readConfig(pipelineContext)
    val dataInput = getInputByName(INPUT_DATA)

    val externalContext =
      pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT).asInstanceOf[ExternalContext]

    val response = externalContext.getResponse

    try {
      // Send an error if needed and return immediately
      // 2024-04-08: No uses of this in the codebase.
      val errorCode = config.errorCode
      if (errorCode != DefaultErrorCode) {
        response.sendError(errorCode)
        return
      }

      // Don't output caching headers or new status code if a non-success status code has been set
      if (! StatusCode.isNonSuccessCode(response.getStatus)) {

        // Get last modification date and compute last modified if possible
        // NOTE: It is not clear if this is right! We had a discussion to "remove serializer last modified,
        // and use oxf:request-generator default validity".
        val lastModified = findInputLastModified(pipelineContext, dataInput, false)

        // Set caching headers and force revalidation
        val pathTypeOpt = Option(pipelineContext.getAttribute(PageFlowControllerProcessor.PathTypeKey).asInstanceOf[PathType])
        response.setPageCaching(lastModified, pathTypeOpt.getOrElse(PathType.Page))

        // Check if we are processing a forward. If so, we cannot tell the client that the content has not been modified.
        if (! URLRewriterUtils.isForwarded(externalContext.getRequest)) {
          // Check If-Modified-Since (conditional GET) and don't return content if condition is met
          if (! response.checkIfModifiedSince(externalContext.getRequest, lastModified)) {
            response.setStatus(StatusCode.NotModified)
            logger.debug("Sending SC_NOT_MODIFIED")
            return
          }
        }
        // STATUS CODE: Set status code based on the configuration
        // An XML processing instruction may override this when the input is being read
        response.setStatus(config.statusCode)
      }
      // Set custom headers
      config.headers.foreach(kv => response.setHeader(kv._1, kv._2))

      // If we have an empty body, return without reading the data input
      if (config.empty)
        return

      val httpOutputStream = response.getOutputStream

      // If local caching of the data is enabled and if the configuration status code is a success code, use
      // the caching API. It doesn't make sense in HTTP to allow caching of non-successful responses.
      if (
        config.cacheUseLocalCache &&
        StatusCode.isSuccessCode(config.statusCode) &&
        ! StatusCode.isNonSuccessCode(response.getStatus)
      ) {
        var wasRead = false
        val resultStore =
          readCacheInputAsObject(
            pipelineContext,
            dataInput,
            new CacheableInputReader[ExtendedResultStoreOutputStream] {

              private var statusCode = config.statusCode

              override def read(pipelineContext: PipelineContext, input: ProcessorInput): ExtendedResultStoreOutputStream = {
                wasRead = true
                logger.debug("Output not cached")

                  val resultStoreOutputStream = new ExtendedResultStoreOutputStream(httpOutputStream)
                  // NOTE: readInput will call response.setContentType() and other methods so we intercept and save the
                  // values.
                  readInput(
                    pipelineContext,
                    new ResponseWrapper(response) {

                      override def setContentType(contentType: String): Unit = {
                        resultStoreOutputStream.contentType = Option(contentType)
                        super.setContentType(contentType)
                      }

                      override def setHeader(name: String, value: String): Unit = {
                        resultStoreOutputStream.setHeader(name, value)
                        super.setHeader(name, value)
                      }

                      override def getOutputStream: OutputStream = resultStoreOutputStream

                      override def setStatus(status: Int): Unit = {
                        // STATUS CODE: This typically is overridden via a processing instruction.
                        statusCode = status
                        resultStoreOutputStream.status = Some(status)
                        super.setStatus(status)
                      }
                    },
                    input,
                    config
                  )
                  resultStoreOutputStream.close()
                  resultStoreOutputStream
              }

              // It doesn't make sense in HTTP to allow caching of non-successful responses
              override def allowCaching: Boolean =
                StatusCode.isSuccessCode(statusCode) && !StatusCode.isNonSuccessCode(response.getStatus)
            }
        )
        // If the output was obtained from the cache, just write it
        if (! wasRead) {
          logger.debug("Serializer output cached")

          resultStore.status
            .foreach(response.setStatus)
          resultStore.contentType
            .foreach(response.setContentType)
          for ((key, value) <- resultStore.getHeaders)
            response.setHeader(key, value)
          // Set length since we know it
          response.setContentLength(resultStore.length(pipelineContext))

          // Replay content
          resultStore.replay(pipelineContext)
        }
      } else {
        // Local caching is not enabled, just read the input
        readInput(pipelineContext, response, dataInput, config)
        httpOutputStream.close()
      }
    } catch {
      case _: SocketException =>
        // In general there is no point doing much with such exceptions. They are thrown in particular when the
        // client has closed the connection.
        logger.info("SocketException in serializer")
    }
  }

  protected def readConfig(context: PipelineContext): Config =
    readCacheInputAsObject(
      context,
      getInputByName(INPUT_CONFIG),
      (context: PipelineContext, input: ProcessorInput) => {

        val configElement = readInputAsOrbeonDom(context, input).getRootElement

        val contentTypeOpt   = selectStringValueNormalizeOpt(configElement, "/config/content-type")
        val forceContentType = selectBooleanValue           (configElement, "/config/force-content-type", DefaultForceContentType)
        val encodingOpt      = selectStringValueNormalizeOpt(configElement, "/config/encoding")
        val forceEncoding    = selectBooleanValue           (configElement, "/config/force-encoding",     DefaultForceEncoding)

        if (forceContentType && contentTypeOpt.isEmpty)
          throw new OXFException("The force-content-type element requires a content-type element.")
        if (forceEncoding && encodingOpt.isEmpty)
          throw new OXFException("The force-encoding element requires an encoding element.")

        val headersIt =
          for {
            headerNode <- selectNodeIterator(configElement, "/config/header").asScala
            headerElem = headerNode.asInstanceOf[Element]
            name       = headerElem.element("name").getTextTrim
            value      = headerElem.element("value").getTextTrim
          } yield
            name -> value

        Config(
          // HTTP configuration
          statusCode                = selectIntegerValue           (configElement, "/config/status-code",                   DefaultStatusCode),
          errorCode                 = selectIntegerValue           (configElement, "/config/error-code",                    DefaultErrorCode),
          contentTypeOpt            = contentTypeOpt,
          forceContentType          = forceContentType,
          ignoreDocumentContentType = selectBooleanValue           (configElement, "/config/ignore-document-content-type",  DefaultIgnoreDocumentContentType),
          encodingOpt               = encodingOpt,
          forceEncoding             = forceEncoding,
          ignoreDocumentEncoding    = selectBooleanValue           (configElement, "/config/ignore-document-encoding",      DefaultIgnoreDocumentEncoding),
          headers                   = headersIt.toList,
          headersToForward          = selectStringValueNormalizeOpt(configElement, "/config/forward-headers").map(_.splitTo[List]()).getOrElse(Nil),
          cacheUseLocalCache        = selectBooleanValue           (configElement, "/config/cache-control/use-local-cache", DefaultCacheUseLocalCache),
          empty                     = selectBooleanValue           (configElement, "/config/empty-content",                 DefaultEmpty),

          // XML / HTML / Text configuration
          methodOpt                 = selectStringValueNormalizeOpt(configElement, "/config/method"),
          versionOpt                = selectStringValueNormalizeOpt(configElement, "/config/version"),
          publicDoctypeOpt          = selectStringValueNormalizeOpt(configElement, "/config/public-doctype"),
          systemDoctypeOpt          = selectStringValueNormalizeOpt(configElement, "/config/system-doctype"),
          omitXMLDeclaration        = selectBooleanValue           (configElement, "/config/omit-xml-declaration",          DefaultOmitXmlDeclaration),
          standalone                = selectStringValueNormalizeOpt(configElement, "/config/standalone").map(_.toBoolean),
          indent                    = selectBooleanValue           (configElement, "/config/indent",                        DefaultIndent),
          indentAmount              = selectIntegerValue           (configElement, "/config/indent-amount",                 DefaultIndentAmount),
        )
      }
    )
}