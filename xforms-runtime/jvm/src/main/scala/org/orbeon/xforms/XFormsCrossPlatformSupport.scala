/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.xforms

import java.io.InputStream
import java.net.URI

import cats.syntax.option._
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.util.{Connection, IndentedLogger, NetUtils}
import org.orbeon.oxf.xforms.processor.XFormsAssetServer


object XFormsCrossPlatformSupport extends XFormsCrossPlatformSupportTrait {

  def properties: PropertySet = Properties.instance.getPropertySet

  def externalContext: ExternalContext = NetUtils.getExternalContext

  def proxyURI(
    uri              : String,
    filename         : Option[String],
    contentType      : Option[String],
    lastModified     : Long,
    customHeaders    : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String =
    XFormsAssetServer.proxyURI(
        uri              = uri,
        filename         = filename,
        contentType      = contentType,
        lastModified     = lastModified,
        customHeaders    = customHeaders,
        headersToForward = Connection.headersToForwardFromProperty,
        getHeader        = getHeader
    )

  def proxyBase64Binary(
    value            : String,
    filename         : Option[String],
    mediatype        : Option[String],
    evaluatedHeaders : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String =
    proxyURI(
      uri              = NetUtils.base64BinaryToAnyURI(value, NetUtils.SESSION_SCOPE, logger.logger.logger),
      filename         = filename,
      contentType      = mediatype,
      lastModified     = -1,
      customHeaders    = evaluatedHeaders,
      getHeader        = getHeader
    )

  def renameAndExpireWithSession(
    existingFileURI  : String)(implicit
    logger           : IndentedLogger
  ): URI =
    NetUtils.renameAndExpireWithSession(existingFileURI, logger.logger.logger).toURI

  def inputStreamToRequestUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    useAndClose(inputStream) { is =>
      NetUtils.inputStreamToAnyURI(is, NetUtils.REQUEST_SCOPE, logger.logger.logger).some
    }

  def inputStreamToSessionUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    useAndClose(inputStream) { is =>
      NetUtils.inputStreamToAnyURI(is, NetUtils.SESSION_SCOPE, logger.logger.logger).some
    }
}
