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

object PropertiesApacheHttpClient extends ApacheHttpClient(PropertiesConnectionSettings.apply)

object PropertiesConnectionSettings {

  def apply: HttpClientSettings = {

    val props = Properties.instance.getPropertySet

    import HttpClientSettings._

    HttpClientSettings(
      staleCheckingEnabled = props.getBoolean(StaleCheckingEnabledProperty, StaleCheckingEnabledDefault),
      soTimeout            = props.getInteger(SOTimeoutProperty, SOTimeoutPropertyDefault).toInt,
      chunkRequests        = props.getBoolean(ChunkRequestsProperty, ChunkRequestsDefault),

      proxyHost            = props.getNonBlankString(ProxyHostProperty),
      proxyPort            = Option(props.getInteger(ProxyPortProperty)) map (_.toInt),
      proxyExclude         = props.getNonBlankString(ProxyExcludeProperty),

      sslHostnameVerifier  = props.getString(SSLHostnameVerifierProperty, SSLHostnameVerifierDefault),
      sslKeystoreURI       = Option(props.getStringOrURIAsString(SSLKeystoreURIProperty, allowEmpty = false)),
      sslKeystorePassword  = props.getNonBlankString(SSLKeystorePasswordProperty),
      sslKeystoreType      = props.getNonBlankString(SSLKeystoreTypeProperty),

      proxySSL             = props.getBoolean(ProxySSLProperty, ProxySSLPropertyDefault),
      proxyUsername        = props.getNonBlankString(ProxyUsernameProperty),
      proxyPassword        = props.getNonBlankString(ProxyPasswordProperty),
      proxyNTLMHost        = props.getNonBlankString(ProxyNTLMHostProperty),
      proxyNTLMDomain      = props.getNonBlankString(ProxyNTLMDomainProperty)
    )
  }
}