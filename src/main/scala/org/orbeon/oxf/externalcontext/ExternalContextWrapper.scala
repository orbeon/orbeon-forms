/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.orbeon.oxf.pipeline.api.ExternalContext

class ExternalContextWrapper(val externalContext: ExternalContext) extends ExternalContext {

    def getWebAppContext = externalContext.getWebAppContext
    def getSession(create: Boolean) = externalContext.getSession(create)

    def getRequest: ExternalContext.Request = externalContext.getRequest
    def getResponse: ExternalContext.Response = externalContext.getResponse

    def getStartLoggerString = externalContext.getStartLoggerString
    def getEndLoggerString = externalContext.getEndLoggerString

    def getRequestDispatcher(path: String, isContextRelative: Boolean): ExternalContext.RequestDispatcher = externalContext.getRequestDispatcher(path, isContextRelative)
}