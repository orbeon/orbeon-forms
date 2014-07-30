/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import org.orbeon.oxf.properties.Properties

object PropertiesHttpClientImpl extends HttpClientImpl(PropertiesConnectionSettings.apply)

object PropertiesConnectionSettings {

    def apply: HttpClientSettings = {

        val props = Properties.instance.getPropertySet

        import HttpClientSettings._

        HttpClientSettings(
            staleCheckingEnabled = props.getBoolean(StaleCheckingEnabledProperty, StaleCheckingEnabledDefault),
            soTimeout            = props.getInteger(SOTimeoutProperty, SOTimeoutPropertyDefault).toInt,

            proxyHost            = Option(props.getString(ProxyHostProperty)),
            proxyPort            = Option(props.getInteger(ProxyPortProperty)) map (_.toInt),
            proxyExclude         = Option(props.getString(ProxyExcludeProperty)),

            sslHostnameVerifier  = props.getString(SSLHostnameVerifierProperty, SSLHostnameVerifierDefault),
            sslKeystoreURI       = Option(props.getStringOrURIAsString(SSLKeystoreURIProperty, allowEmpty = false)),
            sslKeystorePassword  = Option(props.getString(SSLKeystorePasswordProperty)),

            proxySSL             = props.getBoolean(ProxySSLProperty, ProxySSLPropertyDefault),
            proxyUsername        = Option(props.getString(ProxyUsernameProperty)),
            proxyPassword        = Option(props.getString(ProxyPasswordProperty)),
            proxyNTLMHost        = Option(props.getString(ProxyNTLMHostProperty)),
            proxyNTLMDomain      = Option(props.getString(ProxyNTLMDomainProperty))
        )
    }
}