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
package org.orbeon.oxf.http

import org.apache.http.{HttpHost, HttpRequest, HttpRequestInterceptor}
import org.apache.http.auth.{AuthScope, AuthState, NTCredentials}
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.protocol.ClientContext
import org.apache.http.impl.auth.{BasicScheme, NTLMScheme}
import org.apache.http.protocol.{ExecutionContext, HttpContext}

/**
 * The Apache folks are afraid we misuse preemptive authentication, and so force us to copy paste some code rather
 * than providing a simple configuration flag. See:
 *
 * http://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html#d4e950
 */
class PreemptiveAuthHttpRequestInterceptor extends HttpRequestInterceptor {
    def process(request: HttpRequest, context: HttpContext) {
        val authState = context.getAttribute(ClientContext.TARGET_AUTH_STATE).asInstanceOf[AuthState]
        val credentialsProvider = context.getAttribute(ClientContext.CREDS_PROVIDER).asInstanceOf[CredentialsProvider]
        val targetHost = context.getAttribute(ExecutionContext.HTTP_TARGET_HOST).asInstanceOf[HttpHost]

        // If not auth scheme has been initialized yet
        if (authState.getAuthScheme == null) {
            val authScope = new AuthScope(targetHost.getHostName, targetHost.getPort)
            // Obtain credentials matching the target host
            val credentials = credentialsProvider.getCredentials(authScope)
            // If found, generate BasicScheme preemptively
            if (credentials != null) {
                authState.setAuthScheme(if (credentials.isInstanceOf[NTCredentials]) new NTLMScheme(new JCIFSEngine) else new BasicScheme)
                authState.setCredentials(credentials)
            }
        }
    }
}