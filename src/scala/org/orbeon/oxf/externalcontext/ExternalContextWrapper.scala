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

class ExternalContextWrapper(externalContext: ExternalContext) extends ExternalContext {

    def getRequest: ExternalContext.Request = externalContext.getRequest
    def getResponse: ExternalContext.Response = externalContext.getResponse
    def getSession(create: Boolean): ExternalContext.Session = externalContext.getSession(create)

    def getRealPath(path: String) = externalContext.getRealPath(path)

    def getStartLoggerString = externalContext.getStartLoggerString
    def getEndLoggerString = externalContext.getEndLoggerString

    def rewriteServiceURL(urlString: String, rewriteMode: Int) = externalContext.rewriteServiceURL(urlString, rewriteMode)

    def log(msg: String) = externalContext.log(msg)
    def log(message: String, throwable: Throwable) = externalContext.log(message, throwable)

    def getInitAttributesMap = externalContext.getInitAttributesMap
    def getAttributesMap = externalContext.getAttributesMap

    def getApplication: ExternalContext.Application = externalContext.getApplication

    def getRequestDispatcher(path: String, isContextRelative: Boolean): ExternalContext.RequestDispatcher = externalContext.getRequestDispatcher(path, isContextRelative)
    def getNativeRequest = externalContext.getNativeRequest
    def getNativeResponse = externalContext.getNativeResponse
    def getNativeContext = externalContext.getNativeContext
}