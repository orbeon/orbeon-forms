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

object PropertiesHTTPClient extends HTTPClient(PropertiesConnectionSettings.apply)

object PropertiesConnectionSettings {

    def apply: ConnectionSettings = {

        val propertySet = Properties.instance.getPropertySet

        import ConnectionSettings._

        ConnectionSettings(
            staleCheckingEnabled = propertySet.getBoolean(StaleCheckingEnabledProperty, StaleCheckingEnabledDefault),
            soTimeout            = propertySet.getInteger(SOTimeoutProperty, SOTimeoutPropertyDefault).toInt,

            proxyHost            = Option(propertySet.getString(ProxyHostProperty)),
            proxyPort            = Option(propertySet.getInteger(ProxyPortProperty)) map (_.toInt),
            proxyExclude         = Option(propertySet.getString(ProxyExcludeProperty)),

            sslHostnameVerifier  = propertySet.getString(SSLHostnameVerifierProperty, SSLHostnameVerifierDefault),
            sslKeystoreURI       = Option(propertySet.getStringOrURIAsString(SSLKeystoreURIProperty, allowEmpty = false)),
            sslKeystorePassword  = Option(propertySet.getString(SSLKeystorePasswordProperty)),

            proxySSL             = propertySet.getBoolean(ProxySSLProperty, ProxySSLPropertyDefault),
            proxyUsername        = Option(propertySet.getString(ProxyUsernameProperty)),
            proxyPassword        = Option(propertySet.getString(ProxyPasswordProperty)),
            proxyNTLMHost        = Option(propertySet.getString(ProxyNTLMHostProperty)),
            proxyNTLMDomain      = Option(propertySet.getString(ProxyNTLMDomainProperty))
        )
    }
}