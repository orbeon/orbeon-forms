/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.portlet.liferay

import java.{util => ju}
import javax.portlet._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.wsrp.WSRPSupport


// Liferay-specific portlet support
object LiferayURL {

  // Liferay resource id parameter
  private val ResourceIdParameter = "p_p_resource_id"

  // Magic number to indicate we want to process the resource URL below
  private val URLBaseMagic = "1b713b2e6d7fd45753f4b8a6270b776e"

  // Liferay-specific code: attempt to move p_p_resource_id with magic number to the end so that client can attempt to
  // find a base URL. This is specifically written to handle https://github.com/orbeon/orbeon-forms/issues/258
  def moveMagicResourceId(encodedURL: String): String =
    if (encodedURL.contains(URLBaseMagic) && encodedURL.contains(ResourceIdParameter)) {
      splitQuery(encodedURL) match {
        case (path, Some(query)) =>
          val parameters = decodeSimpleQuery(query)
          parameters collectFirst {
            case (ResourceIdParameter, resourceId) if resourceId.contains(URLBaseMagic) => resourceId
          } match {
            case Some(resourceId) =>
              val updated =
                (parameters filterNot (_._1 == ResourceIdParameter)) :+ (ResourceIdParameter -> resourceId)
              appendQueryString(path, encodeSimpleQuery(updated))
            case None =>
              encodedURL
          }
        case _ => encodedURL
      }
    } else
      encodedURL


  // Rewrite a WSRP-encoded URL to a portlet URL
  def wsrpToPortletURL(encodedURL: String, response: MimeResponse): String = {

    def createResourceURL(resourceId: String) = {
      val url = response.createResourceURL
      url.setResourceID(resourceId)
      url.setCacheability(ResourceURL.PAGE)
      moveMagicResourceId(url.toString)
    }

    def createPortletURL[T <: PortletURL](
      url                  : T,
      portletMode          : Option[String],
      windowState          : Option[String],
      navigationParameters : ju.Map[String, Array[String]]
    ) = {
      portletMode foreach (v => url.setPortletMode(new PortletMode(v)))
      windowState foreach (v => url.setWindowState(new WindowState(v)))
      url.setParameters(navigationParameters)
      url.toString
    }

    WSRPSupport.decodeURL(
      encodedURL,
      createResourceURL,
      createPortletURL(response.createActionURL, _, _, _),
      createPortletURL(response.createRenderURL, _, _, _)
    )
  }
}
