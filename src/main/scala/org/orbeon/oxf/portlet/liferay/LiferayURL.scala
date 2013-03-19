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

import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.NetUtils

// Liferay-specific portlet support
object LiferayURL {

    // Liferay resource id parameter
    private val ResourceIdParameter = "p_p_resource_id"

    // Magic number to indicate we want to process the resource URL below
    private val URLBaseMagic = "1b713b2e6d7fd45753f4b8a6270b776e"

    // Liferay-specific code: attempt to move p_p_resource_id with magic number to the end so that client can attempt to
    // find a base URL. This is specifically written to handle https://github.com/orbeon/orbeon-forms/issues/258
    def moveMagicResourceId(encodedURL: String) =
        if (encodedURL.contains(URLBaseMagic) && encodedURL.contains(ResourceIdParameter)) {
            splitQuery(encodedURL) match {
                case (path, Some(query)) ⇒
                    val parameters = decodeSimpleQuery(query)
                    parameters collect { case (ResourceIdParameter, resourceId) if resourceId.contains(URLBaseMagic) ⇒ resourceId } headOption match {
                        case Some(resourceId) ⇒
                            val updated = (parameters filterNot (_._1 == ResourceIdParameter)) :+ (ResourceIdParameter → resourceId)
                            NetUtils.appendQueryString(path, encodeSimpleQuery(updated))
                        case None ⇒
                            encodedURL
                    }
                case _ ⇒ encodedURL
            }
        } else
            encodedURL
}
