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

object PropertiesApacheHttpClient extends ApacheHttpClient(PropertiesConnectionSettings.apply)

object PropertiesConnectionSettings {

  def apply: HttpClientSettings = {

    import HttpClientSettings._
    import org.orbeon.oxf.properties.Properties

    import scala.concurrent.duration._

    val props = Properties.instance.getPropertySet

    HttpClientSettings(
      staleCheckingEnabled           = props.getBoolean(StaleCheckingEnabledProperty, StaleCheckingEnabledDefault),
      soTimeout                      = props.getInteger(SOTimeoutProperty, SOTimeoutPropertyDefault),
      chunkRequests                  = props.getBoolean(ChunkRequestsProperty, ChunkRequestsDefault),

      proxyHost                      = props.getNonBlankString(ProxyHostProperty),
      proxyPort                      = props.getIntOpt(ProxyPortProperty),
      proxyExclude                   = props.getNonBlankString(ProxyExcludeProperty),

      sslHostnameVerifier            = props.getString(SSLHostnameVerifierProperty, SSLHostnameVerifierDefault),
      sslKeystoreURI                 = props.getStringOrURIAsStringOpt(SSLKeystoreURIProperty, allowEmpty = false),
      sslKeystorePassword            = props.getNonBlankString(SSLKeystorePasswordProperty),
      sslKeystoreType                = props.getNonBlankString(SSLKeystoreTypeProperty),

      proxySSL                       = props.getBoolean(ProxySSLProperty, ProxySSLPropertyDefault),
      proxyUsername                  = props.getNonBlankString(ProxyUsernameProperty),
      proxyPassword                  = props.getNonBlankString(ProxyPasswordProperty),
      proxyNTLMHost                  = props.getNonBlankString(ProxyNTLMHostProperty),
      proxyNTLMDomain                = props.getNonBlankString(ProxyNTLMDomainProperty),

      expiredConnectionsPollingDelay = props.getIntOpt(ExpiredConnectionsPollingDelayProperty) filter (_ > 0) map (_.intValue.milliseconds),
      idleConnectionsDelay           = props.getIntOpt(IdleConnectionsDelayProperty)           filter (_ > 0) map (_.intValue.milliseconds)
    )
  }
}