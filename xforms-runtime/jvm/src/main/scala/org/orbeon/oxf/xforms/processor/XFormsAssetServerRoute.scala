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

import cats.syntax.option.*
import org.orbeon.errorified.Exceptions.{getRootThrowable, isConnectionInterruption}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.controller.NativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext.SessionScope
import org.orbeon.oxf.externalcontext.{ExternalContext, SafeRequestContext, UrlRewriteMode}
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.http.{Headers, HttpRanges, SessionExpiredException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.processor.ResourceServer
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.xforms.*
import org.orbeon.oxf.xforms.XFormsAssetPaths.*
import org.orbeon.oxf.xforms.XFormsContainingDocumentSupport.withDocumentAcquireLock
import org.orbeon.oxf.xforms.state.{RequestParameters, XFormsStateManager, XFormsStaticStateCache}
import org.orbeon.oxf.xforms.xbl.{BindingLoader, GlobalBindingIndex}
import org.orbeon.xforms.{Constants, XFormsCrossPlatformSupport}

import java.io.*
import java.net.URI
import scala.collection.immutable.ListSet
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Serve XForms engine JavaScript and CSS resources by combining them.
  */
object XFormsAssetServerRoute extends NativeRoute {

  // Unneeded for JVM platform
  private implicit val resourceResolver: Option[ResourceResolver] = None

  def process(
    matchResult: MatchResult
  )(implicit
    pc         : PipelineContext,
    ec         : ExternalContext
  ): Unit = {

    implicit val indentedLogger: IndentedLogger = Loggers.newIndentedLogger("resources")

    val requestPath = ec.getRequest.getRequestPath
    val response    = ec.getResponse

    val requestTime = System.currentTimeMillis

    requestPath match {
      case DynamicResourceRegex(_) =>
        serveDynamicResource(requestPath)
      case BaselineResourceRegex(ext) =>

        val isCSS = ext == "css"

        def redirect(path: String): Unit = {

          val pathWithContext =
            response.rewriteRenderURL(
              // https://github.com/orbeon/orbeon-forms/issues/6170
              if (ec.getRequest.getFirstParamAsString(Constants.UrlRewriteParameter).contains("noprefix"))
                PathUtils.recombineQuery(path, List(Constants.UrlRewriteParameter -> "noprefix"))
              else
                path
            )

          response.sendRedirect(pathWithContext, isServerSide = false, isExitPortal = false)
        }

        val updatesPropOpt =
          for {
            paramValue <- ec.getRequest.getFirstParamAsString(Constants.UpdatesParameter)
            propName   = (XFormsAssetsBuilder.AssetsBaselineProperty :: Constants.UpdatesParameter :: paramValue :: Nil).mkString(".")
            prop       <- CoreCrossPlatformSupport.properties.getPropertyOpt(propName)
          } yield
            prop

        val updatedAssets =
          XFormsAssetsBuilder.updateAssets(
            globalAssetsBaseline = XFormsAssetsBuilder.fromJsonProperty(CoreCrossPlatformSupport.properties),
            globalXblBaseline    = BindingLoader.getUpToDateLibraryAndBaseline(GlobalBindingIndex.currentIndex, checkUpToDate = true)._3.keySet,
            localExcludesProp    = None,
            localUpdatesProp     = updatesPropOpt
          )

        val isMinimal = XFormsGlobalProperties.isMinimalResources
        val assets    = (if (isCSS) updatedAssets.css else updatedAssets.js).map(_.assetPath(tryMin = isMinimal)).to(ListSet)

        val xblAssets =
          if (updatedAssets.xbl.nonEmpty) {
            val xblAssets = BindingLoader.findXblAssetsUnordered(updatedAssets.xbl)
            if (isCSS)
              xblAssets.flatMap(_._2.css.map(_.assetPath(tryMin = isMinimal)))
            else
              xblAssets.flatMap(_._2.js.map(_.assetPath(tryMin = isMinimal)))
          } else
            Nil

        AssetsAggregator.aggregate(assets ++ xblAssets, redirect, None, isCSS)

      case Constants.FormDynamicResourcesRegex(uuid) =>

        // This is the typical expected scenario: loading the dynamic data occurs just after loading the page and before there have been
        // any changes to the document, so the document should be in cache and have a sequence number of "1".
        val fromCurrentStateOptTry =
          withDocumentAcquireLock(
            uuid    = uuid,
            timeout = XFormsGlobalProperties.uploadXFormsAccessTimeout // same timeout as upload for now (throws if the timeout expires)
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
            // 2022-10-13: Returns `None` if the state is not found in the state store, so we can produce an appropriate
            // error without logging an exception.
            def fromInitialStateOpt: Option[Option[(Option[String], String)]] =
              XFormsStateManager.getStateFromParamsOrStore(
                RequestParameters(uuid, None, None, None, None),
                isInitialState = true
              ).map(_.dynamicState.flatMap(_.initializationData))

            response.setContentType(ContentTypes.JavaScriptContentTypeWithCharset)

            // The document cannot be cached "forever", but upon browser back it can be required again. So some small duration of caching
            // can make sense for the client.
            response.setResourceCaching(
              lastModified = requestTime,
              expires      = requestTime + ec.getRequest.getSession(true).getMaxInactiveInterval * 1000
            )

            IOUtils.useAndClose(new OutputStreamWriter(response.getOutputStream, CharsetNames.Utf8)) { writer =>
              fromCurrentStateOpt.map(Some.apply) orElse fromInitialStateOpt match {
                case Some(initializationDateOpt) =>
                  initializationDateOpt foreach { case (initializationScriptsOpt, jsonInitializationData) =>

                    val namespaceOpt   = ec.getRequest.getFirstParamAsString(Constants.EmbeddingNamespaceParameter)
                    val contextPathOpt = ec.getRequest.getFirstParamAsString(Constants.EmbeddingContextParameter)

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
                        contextPathOpt     = contextPathOpt,
                        namespaceOpt       = namespaceOpt
                      )
                    )
                  }
                case None =>
                  info(s"document not found in store while building dynamic form initialization")
                  response.setStatus(StatusCode.LoginTimeOut)
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

          // https://github.com/orbeon/orbeon-forms/issues/1730
          if (state.singleUseStaticState)
            XFormsStaticStateCache.removeDocument(staticStateDigest)
        }
      case ResourceRegex(hash, ext) =>
        serveCSSOrJavaScript(requestTime, hash, ext)
      case _ =>
        response.setStatus(StatusCode.NotFound)
    }
  }

  private def serveDynamicResource(
    requestPath    : String
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): Unit = {

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
              // Forward HTTP range headers to server
              headers     = resource.headers ++ HttpRanges.rangeHeadersFromRequest(externalContext.getRequest),
              loadState   = true,
              saveState   = true,
              logBody     = false
            )

          // Forward HTTP range headers, content length/type, and status to client
          HttpRanges.forwardResponseHeaders(cxr, response)
          response.setStatus(cxr.statusCode)

          IOUtils.copyStreamAndClose(cxr.content.stream, response.getOutputStream)
        } catch {
          case NonFatal(t) =>
            if (isConnectionInterruption(t)) {
              info(s"connection interrupted: ${getRootThrowable(t).getMessage}")
            } else {
              error("exception copying stream", Seq("throwable" -> OrbeonFormatter.format(t)))
            }
        }

      case None =>
        response.setStatus(StatusCode.NotFound)
    }
  }

  private def serveCSSOrJavaScript(
    requestTime    : Long,
    hash           : String,
    ext            : String
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): Unit = {

    val response = externalContext.getResponse

    val resources = {
      // New hash-based mechanism
      XFormsStores.resourcesStore.get(hash) match {
        case Some(value: Array[String]) =>
          // Mapping found
          value.toList map (r => AssetPath(r, hasMin = false))
        case _ =>
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
      def nsFromParameters = externalContext.getRequest.getFirstParamAsString(Constants.EmbeddingNamespaceParameter)
      def nsFromContainer  = Some(response.getNamespacePrefix)

      nsFromParameters orElse nsFromContainer filter (_.nonEmpty)
    }

    val isCssNoPrefix =
      externalContext.getRequest.getFirstParamAsString(Constants.UrlRewriteParameter).contains("noprefix")

    debug("caching not requested, serving directly", Seq("request path" -> externalContext.getRequest.getRequestPath))
    XFormsResourceRewriter.generateAndClose(resources, namespaceOpt, response.getOutputStream, isCSS, isMinimal, isCssNoPrefix)
  }

  import XFormsAssetPaths.*

  private val DynamicResourcesSessionKey = "orbeon.resources.dynamic."

  // Transform a URI accessible from the server into a URI accessible from the client.
  // The mapping expires with the session.
  def proxyURI(
    urlString       : String,
    filename        : Option[String],
    contentType     : Option[String],
    lastModified    : Long,
    customHeaders   : Map[String, List[String]],
    headersToForward: Set[String],
    getHeader       : String => Option[List[String]]
  )(implicit
    logger          : IndentedLogger
  ): Option[URI] = {

    // Get session
    val externalContext = XFormsCrossPlatformSupport.externalContext
    val session = externalContext.getRequest.getSession(true)

    require(session ne null, "proxyURI requires a session")

    // The resource URI may already be absolute, or may be relative to the server base. Make sure we work with
    // an absolute URI.
    val serviceAbsoluteUrl = URI.create(
      URLRewriterUtils.rewriteServiceURL(
        externalContext.getRequest,
        urlString,
        UrlRewriteMode.Absolute
      )
    )

    val outgoingHeaders =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = serviceAbsoluteUrl,
        hasCredentials   = false,
        customHeaders    = customHeaders,
        headersToForward = headersToForward,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = getHeader
      )(
        logger           = logger,
        safeRequestCtx   = SafeRequestContext(externalContext)
      )

    val resource =
      DynamicResource(serviceAbsoluteUrl, filename, contentType, lastModified, outgoingHeaders)

    // Store mapping into session
    session.setAttribute(DynamicResourcesSessionKey + resource.digest, resource, SessionScope.Application)

    URI.create(DynamicResourcesPath + resource.digest).some
  }

  // For Java callers
  // 2015-09-21: Only used by FileSerializer.
  def jProxyURI(urlString: String, contentType: String): String =
    proxyURI(urlString, None, Option(contentType), -1, Map(), Set(), _ => None)(null).map(_.toString).getOrElse("")

  // Try to remove a dynamic resource
  //
  // - do nothing if the session or resource are not found
  // - if `removeFile == true` and the resource maps to a file, try to remove the file
  // - remove the mapping from the session
  def tryToRemoveDynamicResource(
    requestPath     : String,
    removeFile      : Boolean
  ): Unit = {

    implicit val externalContext: ExternalContext = XFormsCrossPlatformSupport.externalContext

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
  def testGetResources(key: String): Array[String] =
    XFormsStores.resourcesStore.get(key) map (_.asInstanceOf[Array[String]]) orNull

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
      val digest = SecureUtils.digestStringToHexShort(key)

      val mediatypeOpt        = contentTypeOpt flatMap ContentTypes.getContentTypeMediaType
      val incompleteMediatype = mediatypeOpt exists (_.endsWith("/*"))

      val contentType =
        contentTypeOpt filterNot (_ => incompleteMediatype) getOrElse ContentTypes.OctetStreamContentType

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