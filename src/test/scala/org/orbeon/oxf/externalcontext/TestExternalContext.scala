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
package org.orbeon.oxf.externalcontext

import java.io._
import java.{util => ju}

import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext._
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.oxf.xml.{PartUtils, XPathUtils}

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * ExternalContext used by the TestScriptProcessor. It is configurable with an XML document representing
  * the request.
  */
object TestExternalContext {
  private val Logger = LoggerFactory.createLogger(classOf[TestExternalContext])
}

class TestExternalContext(
  var pipelineContext : PipelineContext,
  var requestDocument : Document,
  sessionCreated      : Session => Any = _ => (),
  sessionDestroyed    : Session => Any = _ => ()
) extends ExternalContext {

  // For Java callers
  def this(
    pipelineContext : PipelineContext,
    requestDocument : Document
  ) =
    this(pipelineContext, requestDocument, _ => (), _ => ())

  private val webAppContext: WebAppContext = new TestWebAppContext(TestExternalContext.Logger, mutable.LinkedHashMap[String, AnyRef]())

  def getWebAppContext: WebAppContext = webAppContext

  private class RequestImpl extends Request {

    case class BodyDetails(
      bodyInputStream   : InputStream,
      bodyContentType   : String,
      bodyEncoding      : String,
      bodyContentLength : Long
    )

    private var bodyReader: Reader           = null

    private var getInputStreamCalled         = false
    private var getReaderCalled              = false

    lazy val getAttributesMap: ju.Map[String, AnyRef] = {

      val result = new ju.LinkedHashMap[String, AnyRef]

      for {
        node  <- XPathUtils.selectNodeIterator(requestDocument, "/*/attributes/attribute").asScala
        elem  = node.asInstanceOf[Element]
        name  = XPathUtils.selectStringValueNormalize(elem, "name")
        value = XPathUtils.selectStringValueNormalize(elem, "value[1]")
      } locally {
        result.put(name, value)
      }

      result
    }

    def getAuthType: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/auth-type")

    def getCharacterEncoding: String = bodyDetails map (_.bodyEncoding) orNull
    def getContentLength: Int        = bodyDetails map (_.bodyContentLength.toInt) getOrElse -1
    def getContentType: String       = bodyDetails map (_.bodyContentType) orNull

    def getContainerType: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/container-type")

    def getContainerNamespace = ""

    def getInputStream: InputStream = {

      if (getReaderCalled)
        throw new IllegalStateException("Cannot call getInputStream() after getReader() has been called.")

      getInputStreamCalled = true
      bodyDetails map (_.bodyInputStream) orNull
    }

    lazy val bodyDetails: Option[BodyDetails] = {
      val bodyNode = XPathUtils.selectSingleNode(requestDocument, "/*/body").asInstanceOf[Element]
      bodyNode ne null option {
        val contentTypeAttribute = bodyNode.attributeValue(Headers.ContentTypeLower)
        val contentType          = ContentTypes.getContentTypeMediaType(contentTypeAttribute) getOrElse (throw new IllegalArgumentException)
        val charset              = ContentTypes.getContentTypeCharset(contentTypeAttribute).orNull
        val hrefAttribute        = bodyNode.attributeValue("href")

        // TODO: Support same scenarios as Email processor
        if (hrefAttribute ne null) {

          val locationData = bodyNode.getData.asInstanceOf[LocationData]
          val systemId =
            if (locationData eq null)
              null
            else
              locationData.file

          val saxSource = PartUtils.getSAXSource(null, pipelineContext, hrefAttribute, systemId, contentType)
          val fileItem  = PartUtils.handleStreamedPartContent(pipelineContext, saxSource)
          if (! (ContentTypes.isTextOrJSONContentType(contentType) || ContentTypes.isXMLContentType(contentType))) {
            // This is binary content
            if (fileItem ne null) {
              BodyDetails(
                bodyInputStream   = fileItem.getInputStream,
                bodyContentType   = contentType,
                bodyEncoding      = null,
                bodyContentLength = fileItem.getSize
              )
            } else {
              throw new OXFException("Not implemented yet.")
              //                            byte[] data = XMLUtils.base64StringToByteArray((String) content);
              //
              //                            bodyInputStream = new ByteArrayInputStream(data);
              //                            bodyContentType = contentType;
              //                            bodyContentLength = data.length;
            }
          } else {
            // This is text content
            if (fileItem ne null) {
              // The text content was encoded when written to the FileItem
              BodyDetails(
                bodyInputStream   = fileItem.getInputStream,
                bodyContentType   = contentType,
                bodyEncoding      = charset,
                bodyContentLength = fileItem.getSize
              )
            } else {
              throw new OXFException("Not implemented yet.")
              //                            final String s = (String) content
              //                            byte[] bytes = s.getBytes(charset);
              //
              //                            bodyInputStream = new ByteArrayInputStream(bytes);
              //                            bodyContentType = contentType;
              //                            bodyEncoding = charset;
              //                            bodyContentLength = bytes.length;
            }
          }
        } else {
          // Just treat the content as UTF-8 text
          // Should handle other scenarios better - this is just to support basic use cases
          val textContentAsBytes = bodyNode.getText.getBytes(StandardCharacterEncoding)

          BodyDetails(
            bodyInputStream   = new ByteArrayInputStream(textContentAsBytes),
            bodyContentType   = contentType,
            bodyEncoding      = charset,
            bodyContentLength = textContentAsBytes.length
          )
        }
      }
    }

    def getContextPath: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/context-path")

    lazy val getHeaderValuesMap: ju.Map[String, Array[String]] = {

      val result = new ju.LinkedHashMap[String, Array[String]]

      for {
        headerNode <- XPathUtils.selectNodeIterator(requestDocument, "/*/headers/header").asScala
        headerElem = headerNode.asInstanceOf[Element]
        name       = XPathUtils.selectStringValueNormalize(headerElem, "name")
        valueNode  <- XPathUtils.selectNodeIterator(headerElem, "value").asScala
        valueElem  = valueNode.asInstanceOf[Element]
        value      = XPathUtils.selectStringValueNormalize(valueElem, ".")
      } locally {
        StringConversions.addValueToStringArrayMap(result, name, value)
      }

      ju.Collections.unmodifiableMap(result)
    }

    def getLocale: ju.Locale          = null
    def getLocales: ju.Enumeration[_] = null

    def getMethod: HttpMethod =
      HttpMethod.withNameInsensitive(XPathUtils.selectStringValueNormalize(requestDocument, "/*/method"))

    lazy val getParameterMap: ju.Map[String, Array[AnyRef]] = {

      val result = new ju.LinkedHashMap[String, Array[AnyRef]]

      for {
        paramNode  <- XPathUtils.selectNodeIterator(requestDocument, "/*/parameters/parameter").asScala
        paramElem  = paramNode.asInstanceOf[Element]
        name       = XPathUtils.selectStringValueNormalize(paramElem, "name")
        valueNode  <- XPathUtils.selectNodeIterator(paramElem, "value").asScala
        valueElem  = valueNode.asInstanceOf[Element]
        value      = XPathUtils.selectStringValueNormalize(valueElem, ".")
      } locally {
        StringConversions.addValueToObjectArrayMap(result, name, value)
      }

      ju.Collections.unmodifiableMap(result)
    }

    def getPathInfo: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/path-info")

    def getPathTranslated: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/path-translated")

    def getProtocol: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/protocol")

    def getQueryString: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/query-string")

    def getRemoteAddr: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/remote-addr")

    def getRemoteHost: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/remote-host")

    // TODO
    def credentials: Option[Credentials] = None

    override def getUsername: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/username")

    //        public scala.Option<Organization> getUserOrganization() {
    //            final String organizationOrNull = XPathUtils.selectStringValueNormalize(requestDocument, "/*/user-organization");
    //            if (organizationOrNull == null)
    //                null;
    //            else
    //                null;// TODO organizationOrNull.split("\\s+");
    //        }
    //
    //        public UserRole[] getUserRoles() {
    //            final String rolesOrNull = XPathUtils.selectStringValueNormalize(requestDocument, "/*/user-roles");
    //            if (rolesOrNull == null)
    //                new UserRole[0];
    //            else {
    //                String[] serializedRoles = rolesOrNull.split("\\s+");
    //                UserRole[] parsedRoles = new UserRole[serializedRoles.length];
    //                for (int i = 0; i < serializedRoles.length; i++)
    //                    parsedRoles[i] = UserRole$.MODULE$.parse(serializedRoles[i]);
    //                parsedRoles;
    //            }
    //        }
    def getSession(create: Boolean): Session =
      TestExternalContext.this.getSession(create)

    def getRequestedSessionId: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/requested-session-id")

    def getRequestPath: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/request-path")

    def getRequestURI: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/request-uri")

    def getRequestURL: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/request-url")

    def getScheme: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/scheme")

    def getServerName: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/server-name")

    def getServerPort: Int =
      ProcessorUtils.selectIntValue(requestDocument, "/*/server-port", 80)

    def getServletPath: String =
      XPathUtils.selectStringValueNormalize(requestDocument, "/*/servlet-path")

    def getClientContextPath(urlString: String): String =
      URLRewriterUtils.getClientContextPath(this, URLRewriterUtils.isPlatformPath(urlString))

    def isRequestedSessionIdValid   = false

    def isSecure: Boolean = {
      ProcessorUtils.selectBooleanValue(requestDocument, "/*/is-secure", false)
    }

    def isUserInRole(role: String)  = false
    def sessionInvalidate()         = ()

    def getPortletMode: String      = null
    def getWindowState: String      = null

    def getNativeRequest: AnyRef    = null
  }

  private class ResponseImpl extends Response {

    def addHeader(name: String, value: String)  = ()

    def checkIfModifiedSince(request: Request, lastModified: Long) = true
    def getCharacterEncoding: String = null
    def getNamespacePrefix: String = null

    lazy val getOutputStream: OutputStream = new ByteArrayOutputStream

    def getWriter: PrintWriter = null
    def isCommitted: Boolean = false

    def rewriteActionURL(urlString: String) =
      rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

    def rewriteRenderURL(urlString: String) =
      rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

    def rewriteActionURL(urlString: String, portletMode: String, windowState: String) =
      rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

    def rewriteRenderURL(urlString: String, portletMode: String, windowState: String) =
      rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

    def rewriteResourceURL(urlString: String, rewriteMode: Int) =
      URLRewriterUtils.rewriteURL(getRequest, urlString, rewriteMode)

    def reset()                                                                      = ()
    def sendError(len: Int)                                                          = ()
    def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean) = ()
    def setPageCaching(lastModified: Long)                                           = ()
    def setResourceCaching(lastModified: Long, expires: Long)                        = ()
    def setContentLength(len: Int)                                                   = ()
    def setContentType(contentType: String)                                          = ()
    def setHeader(name: String, value: String)                                       = ()
    def setStatus(status: Int)                                                       = ()
    def setTitle(title: String)                                                      = ()

    def getNativeResponse: AnyRef                                                    = null
  }

  lazy val getRequest: Request   = new RequestImpl
  lazy val getResponse: Response = new ResponseImpl

  private var session: Session = null

  def getSession(create: Boolean): Session = {
    if ((session eq null) && create) {
      session = new SimpleSession(SecureUtils.randomHexId)
      sessionCreated(session)
    }
    session
  }

  def getStartLoggerString = "Running test processor"
  def getEndLoggerString   = "Done running test processor"
}