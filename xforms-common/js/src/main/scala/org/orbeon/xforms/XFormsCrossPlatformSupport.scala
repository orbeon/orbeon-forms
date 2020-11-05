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

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.IndentedLogger


object XFormsCrossPlatformSupport extends XFormsCrossPlatformSupportTrait {

  def externalContext: ExternalContext = ???

  def proxyURI(
    uri              : String,
    filename         : Option[String],
    contentType      : Option[String],
    lastModified     : Long,
    customHeaders    : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String =
    ???

  def proxyBase64Binary(
    value            : String,
    filename         : Option[String],
    mediatype        : Option[String],
    evaluatedHeaders : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String =
    ???

  def renameAndExpireWithSession(
    existingFileURI  : String)(implicit
    logger           : IndentedLogger
  ): URI =
    ???

  def inputStreamToRequestUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    ???

  def inputStreamToSessionUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    ???
}
