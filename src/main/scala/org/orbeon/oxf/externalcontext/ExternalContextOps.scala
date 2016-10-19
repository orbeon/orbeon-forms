/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.orbeon.oxf.webapp.ExternalContext.Request
import collection.JavaConverters._

object ExternalContextOps {
  implicit class RequestOps(request: Request) {

    // NOTE: Ideally would return immutable.Map
    def parameters: collection.Map[String, Array[AnyRef]] =
      request.getParameterMap.asScala

    def getFirstParamAsString(name: String) =
      Option(request.getParameterMap.get(name)) flatMap (_ collectFirst { case s: String ⇒ s })

    def getFirstHeader(name: String) =
      Option(request.getHeaderValuesMap.get(name)) flatMap (_.lift(0))
  }
}
