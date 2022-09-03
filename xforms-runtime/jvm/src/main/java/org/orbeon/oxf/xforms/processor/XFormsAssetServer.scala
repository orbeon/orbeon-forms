/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor

import org.orbeon.exception.OrbeonFormatter
import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.externalcontext.ExternalContext.SessionScope
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.http.{Headers, SessionExpiredException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ResourceServer}
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsAssetPaths._
import org.orbeon.oxf.xforms.XFormsContainingDocumentSupport.withDocumentAcquireLock
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.state.{RequestParameters, XFormsStateManager, XFormsStaticStateCache}
import org.orbeon.oxf.xforms.xbl.BindingLoader
import org.orbeon.xforms.{Constants, XFormsCrossPlatformSupport}

import java.io._
import java.net.URI
import scala.collection.compat._
import scala.collection.immutable.ListSet
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


/**
  * Serve XForms engine JavaScript and CSS resources by combining them.
  */
class XFormsAssetServer extends ProcessorImpl with Logging {

  import org.orbeon.oxf.xforms.processor.XFormsAssetServer._

  override def start(pipelineContext: PipelineContext): Unit = {

    implicit val externalContext = XFormsCrossPlatformSupport.externalContext

    val requestPath = externalContext.getRequest.getRequestPath
    val response    = externalContext.getResponse

    val requestTime = System.currentTimeMillis

    requestPath match {
      case DynamicResourceRegex(_) =>
        serveDynamicResource(requestPath)

      case BaselineResourceRegex(ext) =>

        val isCSS = ext == "css"

        def redirect(path: String): Unit = {
          val pathWithContext = response.rewriteRenderURL(path)
          response.sendRedirect(pathWithContext, isServerSide = false, isExitPortal = false)
        }

        val updatesPropOpt =
          for {
            paramValue <- externalContext.getRequest.getFirstParamAsString(Constants.UpdatesParameter)
            propName   = List(XFormsAssetsBuilder.AssetsBaselineProperty, Constants.UpdatesParameter, paramValue).mkString(".")
            prop       <- CoreCrossPlatformSupport.properties.getPropertyOpt(propName)
          } yield
            prop

        val assets    = XFormsAssetsBuilder.updateAssets(XFormsAssetsBuilder.fromJsonProperty(CoreCrossPlatformSupport.properties), excludesProp = None, updatesPropOpt)
        val isMinimal = XFormsGlobalProperties.isMinimalResources
        val resources = (if (isCSS) assets.css else assets.js).map(_.assetPath(tryMin = isMinimal)).to(ListSet)

        val xblResources =
          if (assets.xbl.nonEmpty) {
            val (scripts, styles) = BindingLoader.findXblAssets(assets.xbl)
            if (isCSS) styles else scripts
          } else
            Nil

        AssetsAggregator.aggregate(resources ++ xblResources, redirect, None, isCSS)

      case Constants.FormDynamicResourcesRegex(uuid) =>

        // This is the typical expected scenario: loading the dynamic data occurs just after loading the page and before there have been
        // any changes to the document, so the document should be in cache and have a sequence number of "1".
        val fromCurrentStateOptTry =
          withDocumentAcquireLock(
            uuid,
            XFormsGlobalProperties.uploadXFormsAccessTimeout // same timeout as upload for now (throws if the timeout expires)
          )(d => d.getInitializationData)

        fromCurrentStateOptTry match {
          case Failure(e: SessionExpiredException) => // from downstream `acquireDocumentLock`
            // For `serveDynamicResource` we return "not found" and here we return "forbidden" as that's the
            // code associated with `SessionExpiredException`. Can we justify the difference?
            info(s"session not found while retrieving form dynamic resources")
            response.setStatus(e.code)
          case Failure(e) => // from downstream `acquireDocumentLock`
            info(s"error while retrieving form dynamic resources: ${e.getMessage}")
            response.setStatus(StatusCode.InternalServerError)
          case Success(fromCurrentStateOpt) =>

            // This is the case where the above doesn't hold, for example upon browser back. It should be a much rarer case, and we bear
            // the cost of getting the state from cache.
            def fromInitialStateOpt =
              XFormsStateManager.getStateFromParamsOrStore(
                RequestParameters(uuid, None, None, None),
                isInitialState = true
              ).dynamicState flatMap (_.initializationData)

            response.setContentType(ContentTypes.JavaScriptContentTypeWithCharset)

            // The document cannot be cached "forever", but upon browser back it can be required again. So some small duration of caching
            // can make sense for the client.
            response.setResourceCaching(
              lastModified = requestTime,
              expires      = requestTime + externalContext.getRequest.getSession(true).getMaxInactiveInterval * 1000
            )

            IOUtils.useAndClose(new OutputStreamWriter(response.getOutputStream, CharsetNames.Utf8)) { writer =>

              val namespaceOpt = externalContext.getRequest.getFirstParamAsString(Constants.NamespaceParameter)

              fromCurrentStateOpt orElse fromInitialStateOpt foreach { case (initializationScriptsOpt, jsonInitializationData) =>
                initializationScriptsOpt foreach { initializationScripts =>
                  writer.write(
                    ScriptBuilder.buildXFormsPageLoadedServer(
                      body         = initializationScripts,
                      namespaceOpt = namespaceOpt
                    )
                  )
                }
                writer.write(
                  ScriptBuilder.buildInitializationCall(
                    jsonInitialization = jsonInitializationData,
                    contextPathOpt     = externalContext.getRequest.getFirstParamAsString(Constants.ContextPathParameter),
                    namespaceOpt       = namespaceOpt
                  )
                )
              }
            }
        }

      case FormStaticResourcesRegex(staticStateDigest) =>

        // Security consideration: Unlike in the dynamic case, where knowledge of the transient UUID is required, here we don't require
        // particular access rights to request the static state functions. This shouldn't be a problem, as what is returned is, in effect,
        // just a list of customer scripts which are either part of Form Runner, or part of XBL components. There is no form-specific
        // data returned. So this should be no different than having access to the combined JavaScript resource, for example.

        XFormsStaticStateCache.findDocument(staticStateDigest) foreach { case (state, validity) =>

          // NOTE: The validity is the time the static state was put in cache. We could do better by finding the last modified time
          // of the form definition, but that information is harder to obtain right now.
          response.setContentType(ContentTypes.JavaScriptContentTypeWithCharset)
          response.setResourceCaching(validity, requestTime + ResourceServer.ONE_YEAR_IN_MILLISECONDS)

          IOUtils.useAndClose(new OutputStreamWriter(response.getOutputStream, CharsetNames.Utf8)) { writer =>
            ScriptBuilder.writeScripts(
              state.topLevelPart.uniqueJsScripts,
              writer.write
            )
          }
        }
      case ResourceRegex(hash, ext) =>
        serveCSSOrJavaScript(requestTime, hash, ext)
      case _ =>
        response.setStatus(StatusCode.NotFound)
    }
  }

  private def serveDynamicResource(requestPath: String)(implicit externalContext: ExternalContext): Unit = {

    val response = externalContext.getResponse

    findDynamicResource(requestPath) match {
      case Some(resource) =>

        val digestFromPath = filename(requestPath)

        // Found URL, stream it out

        // Set caching headers

        // NOTE: Algorithm is that XFOutputControl currently passes either -1 or the last modified of the
        // resource if "fast" to obtain last modified ("oxf:" or "file:"). Would be nice to do better: pass
        // whether resource is cacheable or not; here, when dereferencing the resource, we get the last
        // modified (Last-Modified header from HTTP even) and store it. Then we can handle conditional get.
        // This is some work though. Might have to proxy conditional GET as well. So for now we don't
        // handle conditional GET and produce a non-now last modified only in a few cases.

        response.setResourceCaching(resource.lastModified, 0)

        if (resource.size >= 0)
          response.setContentLength(resource.size.asInstanceOf[Int]) // Q: Why does this API (and Servlet counterpart) take an int?

        // TODO: for Safari, try forcing application/octet-stream
        response.setContentType(resource.contentType)

        // File name visible by the user
        val rawFilename = resource.filenameOpt getOrElse digestFromPath

        def addExtensionIfNeeded(filename: String) =
          findExtension(filename) match {
            case Some(_) =>
              filename
            case None    =>
              Mediatypes.findExtensionForMediatype(resource.mediaType) map
              (filename + "." +)                                       getOrElse
              filename
          }

        val contentFilename = addExtensionIfNeeded(rawFilename)

        // Handle as attachment
        (response.setHeader _).tupled(Headers.buildContentDispositionHeader(contentFilename))

        // Copy stream out
        try {
          val cxr =
            Connection.connectNow(
              method      = GET,
              url         = resource.uri,
              credentials = None,
              content     = None,
              headers     = resource.headers,
              loadState   = true,
              saveState   = true,
              logBody     = false
            )

          // TODO: handle 404, etc. and set response parameters *after* we know that we have a successful response code.

          IOUtils.copyStreamAndClose(cxr.content.inputStream, response.getOutputStream)
        } catch {
          case NonFatal(t) => warn("exception copying stream", Seq("throwable" -> OrbeonFormatter.format(t)))
        }

      case None =>
        response.setStatus(StatusCode.NotFound)
    }
  }

  private def serveCSSOrJavaScript(requestTime: Long, hash: String, ext: String)(implicit externalContext: ExternalContext): Unit = {

    val response = externalContext.getResponse

    val resources = {
      // New hash-based mechanism
      val cacheElement = Caches.resourcesCache.get(hash)
      if (cacheElement ne null) {
        // Mapping found
        val resourcesStrings = cacheElement.getObjectValue.asInstanceOf[Array[String]].toList
        resourcesStrings map (r => AssetPath(r, hasMin = false))
      } else {
        // Not found, either because the hash is invalid, or because the cache lost the mapping
        response.setStatus(StatusCode.NotFound)
        return
      }
    }

    val isMinimal = false

    // Get last modified date
    val combinedLastModified = XFormsResourceRewriter.computeCombinedLastModified(resources, isMinimal)

    // Set Last-Modified, required for caching and conditional get
    if (URLRewriterUtils.isResourcesVersioned)
      // Use expiration far in the future
      response.setResourceCaching(combinedLastModified, requestTime + ResourceServer.ONE_YEAR_IN_MILLISECONDS)
    else
      // Use standard expiration policy
      response.setResourceCaching(combinedLastModified, 0)

    // Check If-Modified-Since and don't return content if condition is met
    if (! response.checkIfModifiedSince(externalContext.getRequest, combinedLastModified)) {
      response.setStatus(StatusCode.NotModified)
      return
    }

    val isCSS = ext == "css"

    // Two clients could request the same CSS URL with a different value of the `Orbeon-Client` header, causing
    // the production of different content based on the URL rewriter used as a consequence.
    // However, we can't see a use case where a cache would actually see that, as the `Orbeon-Client` header is not
    // sent by the browser!
    if (isCSS)
      response.addHeader("Vary", Headers.OrbeonClient)

    response.setContentType(if (isCSS) ContentTypes.CssContentTypeWithCharset else ContentTypes.JavaScriptContentTypeWithCharset)

    // Namespace to use, must be `None` if empty
    def namespaceOpt: Option[String] = {
      def nsFromParameters = externalContext.getRequest.getFirstParamAsString(Constants.NamespaceParameter)
      def nsFromContainer  = Some(response.getNamespacePrefix)

      nsFromParameters orElse nsFromContainer filter (_.nonEmpty)
    }

    debug("caching not requested, serving directly", Seq("request path" -> externalContext.getRequest.getRequestPath))
    XFormsResourceRewriter.generateAndClose(resources, namespaceOpt, response.getOutputStream, isCSS, isMinimal)
  }
}

object XFormsAssetServer {

  import XFormsAssetPaths._

  val DynamicResourcesSessionKey = "orbeon.resources.dynamic."

  implicit def indentedLogger: IndentedLogger = Loggers.getIndentedLogger("resources")

  // Transform an URI accessible from the server into a URI accessible from the client.
  // The mapping expires with the session.
  def proxyURI(
    uri              : String,
    filename         : Option[String],
    contentType      : Option[String],
    lastModified     : Long,
    customHeaders    : Map[String, List[String]],
    headersToForward : Set[String],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String = {

    // Get session
    val externalContext = XFormsCrossPlatformSupport.externalContext
    val session = externalContext.getRequest.getSession(true)

    require(session ne null, "proxyURI requires a session")

    // The resource URI may already be absolute, or may be relative to the server base. Make sure we work with
    // an absolute URI.
    val serviceAbsoluteUrl = URI.create(
      URLRewriterUtils.rewriteServiceURL(
        XFormsCrossPlatformSupport.externalContext.getRequest,
        uri,
        UrlRewriteMode.Absolute
      )
    )

    val outgoingHeaders =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url                      = serviceAbsoluteUrl,
        hasCredentials           = false,
        customHeaders            = customHeaders,
        headersToForward         = headersToForward,
        cookiesToForward         = Connection.cookiesToForwardFromProperty,
        getHeader                = getHeader)(
        logger                   = logger,
        externalContext          = externalContext,
        coreCrossPlatformSupport = CoreCrossPlatformSupport
      )

    val resource =
      DynamicResource(serviceAbsoluteUrl, filename, contentType, lastModified, outgoingHeaders)

    // Store mapping into session
    session.setAttribute(DynamicResourcesSessionKey + resource.digest, resource, SessionScope.Application)

    DynamicResourcesPath + resource.digest
  }

  // For Java callers
  // 2015-09-21: Only used by FileSerializer.
  def jProxyURI(uri: String, contentType: String) =
    proxyURI(uri, None, Option(contentType), -1, Map(), Set(), _ => None)(null)

  // Try to remove a dynamic resource
  //
  // - do nothing if the session or resource are not found
  // - if `removeFile == true` and the resource maps to a file, try to remove the file
  // - remove the mapping from the session
  def tryToRemoveDynamicResource(
    requestPath     : String,
    removeFile      : Boolean
  ): Unit = {

    implicit val externalContext = XFormsCrossPlatformSupport.externalContext

    findDynamicResource(requestPath) foreach { resource =>
      externalContext.getRequest.sessionOpt foreach { session =>

        if (removeFile)
          Try(new File(resource.uri)) foreach { file =>
            file.delete()
          }

        session.removeAttribute(DynamicResourcesSessionKey + resource.digest, SessionScope.Application)
      }
    }
  }

  private def findDynamicResource(
    requestPath     : String)(implicit
    externalContext : ExternalContext
  ): Option[DynamicResource] =
    externalContext.getRequest.sessionOpt flatMap { session =>
      val digestFromPath = filename(requestPath)
      val lookupKey      = DynamicResourcesSessionKey + digestFromPath

      session.getAttribute(lookupKey, SessionScope.Application) map (_.asInstanceOf[DynamicResource])
    }

  // For unit tests only (called from XSLT)
  def testGetResources(key: String)  =
    Option(Caches.resourcesCache.get(key)) map (_.getObjectValue.asInstanceOf[Array[String]]) orNull

  // Information about the resource, stored into the session
  case class DynamicResource(
    digest       : String,
    uri          : URI,
    filenameOpt  : Option[String],
    contentType  : String,
    mediaType    : String,
    size         : Long,
    lastModified : Long,
    headers      : Map[String, List[String]]
  )

  object DynamicResource {
    def apply(
      uri            : URI,
      filenameOpt    : Option[String],
      contentTypeOpt : Option[String],
      lastModified   : Long,
      headers        : Map[String, List[String]]
    ): DynamicResource = {

      // Create a digest, so that for a given URI we always get the same key
      //
      // 2015-09-02: Also digest header name/values, as they matter for example if a resource includes a
      // version number in a header. Headers will include headers explicitly set on `xf:output` with `xf:header`,
      // as well as `Accept`, `User-Agent`, and `Orbeon-Token`.
      // One question is what to do with `Orbeon-Token`. We could exclude it from the digest just in case, for
      // security reasons, but 1) `digest()` should be safe and 2) after a restart, if the session is restored,
      // the token will have changed anyway, so it's better if the digest does not include it as things won't
      // work anyway. On the other hand, unit tests fail if `Orbeon-Token` keeps changing. Not sure what's the best
      // here, but for now filtering out. In addition, that's what we used to do before.

      // Just digest a key produced with `toString`, since we know that tuples, `List` and `Map` produce
      // a reasonable output with `toString`.
      val key    = (uri, headers filterNot (_._1.equalsIgnoreCase("Orbeon-Token"))).toString
      val digest = SecureUtils.digestString(key, "hex")

      val mediatypeOpt        = contentTypeOpt flatMap ContentTypes.getContentTypeMediaType
      val incompleteMediatype = mediatypeOpt exists (_.endsWith("/*"))

      val contentType =
        contentTypeOpt filterNot (_ => incompleteMediatype) getOrElse "application/octet-stream"

      DynamicResource(
        digest       = digest,
        uri          = uri,
        filenameOpt  = filenameOpt,
        contentType  = contentType,
        mediaType    = ContentTypes.getContentTypeMediaType(contentType) getOrElse (throw new IllegalStateException),
        size         = -1,
        lastModified = lastModified,
        headers      = headers
      )
    }
  }

  private val ResourceRegex = """/(?:.+/)?orbeon-([0-9|a-f]+)\.(js|css)""".r

  private def filename(requestPath: String) =
    requestPath.substring(requestPath.lastIndexOf('/') + 1)
}