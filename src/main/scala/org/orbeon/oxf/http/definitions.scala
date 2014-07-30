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
package org.orbeon.oxf.http

import java.io.InputStream

import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.util.ScalaUtils._

import scala.collection.immutable.Seq

case class HttpClientSettings(
    staleCheckingEnabled: Boolean,
    soTimeout           : Int,

    proxyHost           : Option[String],
    proxyPort           : Option[Int],
    proxyExclude        : Option[String],

    sslHostnameVerifier : String,
    sslKeystoreURI      : Option[String],
    sslKeystorePassword : Option[String],

    proxySSL            : Boolean,
    proxyUsername       : Option[String],
    proxyPassword       : Option[String],
    proxyNTLMHost       : Option[String],
    proxyNTLMDomain     : Option[String]
)

object HttpClientSettings {
    
    def apply(param: String ⇒ String): HttpClientSettings = {

        def booleanParamWithDefault(name: String, default: Boolean) =
            nonEmptyOrNone(param(name)) map (_ == "true") getOrElse default

        def intParam(name: String) =
            nonEmptyOrNone(param(name)) map (_.toInt)

        def intParamWithDefault(name: String, default: Int) =
            intParam(name) getOrElse default

        def stringParam(name: String) =
            nonEmptyOrNone(param(name))

        def stringParamWithDefault(name: String, default: String) =
            stringParam(name) getOrElse default

        HttpClientSettings(
            staleCheckingEnabled = booleanParamWithDefault(StaleCheckingEnabledProperty, StaleCheckingEnabledDefault),
            soTimeout            = intParamWithDefault(SOTimeoutProperty, SOTimeoutPropertyDefault),

            proxyHost            = stringParam(ProxyHostProperty),
            proxyPort            = intParam(ProxyPortProperty),
            proxyExclude         = stringParam(ProxyExcludeProperty),

            sslHostnameVerifier  = stringParamWithDefault(SSLHostnameVerifierProperty, SSLHostnameVerifierDefault),
            sslKeystoreURI       = stringParam(SSLKeystoreURIProperty),
            sslKeystorePassword  = stringParam(SSLKeystorePasswordProperty),

            proxySSL             = booleanParamWithDefault(ProxySSLProperty, ProxySSLPropertyDefault),
            proxyUsername        = stringParam(ProxyUsernameProperty),
            proxyPassword        = stringParam(ProxyPasswordProperty),
            proxyNTLMHost        = stringParam(ProxyNTLMHostProperty),
            proxyNTLMDomain      = stringParam(ProxyNTLMDomainProperty)
        )
    }
    
    val StaleCheckingEnabledProperty = "oxf.http.stale-checking-enabled"
    val SOTimeoutProperty            = "oxf.http.so-timeout"
    val ProxyHostProperty            = "oxf.http.proxy.host"
    val ProxyPortProperty            = "oxf.http.proxy.port"
    val ProxyExcludeProperty         = "oxf.http.proxy.exclude"
    val SSLHostnameVerifierProperty  = "oxf.http.ssl.hostname-verifier"
    val SSLKeystoreURIProperty       = "oxf.http.ssl.keystore.uri"
    val SSLKeystorePasswordProperty  = "oxf.http.ssl.keystore.password"
    val ProxySSLProperty             = "oxf.http.proxy.use-ssl"
    val ProxyUsernameProperty        = "oxf.http.proxy.username"
    val ProxyPasswordProperty        = "oxf.http.proxy.password"
    val ProxyNTLMHostProperty        = "oxf.http.proxy.ntlm.host"
    val ProxyNTLMDomainProperty      = "oxf.http.proxy.ntlm.domain"
    
    val StaleCheckingEnabledDefault  = true
    val SOTimeoutPropertyDefault     = 0
    val ProxySSLPropertyDefault      = false
    val SSLHostnameVerifierDefault   = "strict"
}

case class Credentials(username: String, password: Option[String], preemptiveAuth: Boolean, domain: Option[String]) {
    require(StringUtils.isNotBlank(username))
    def getPrefix = Option(password) map (username + ":" + _ + "@") getOrElse username + "@"
}

object Credentials {
    def apply(username: String, password: String, preemptiveAuth: String, domain: String): Credentials =
        Credentials(
            username.trim,
            nonEmptyOrNone(password),
            ! (nonEmptyOrNone(preemptiveAuth) exists (_ == "false")),
            nonEmptyOrNone(domain)
        )
}

trait HttpResponse {
    def statusCode  : Int
    def inputStream : InputStream
    def headers     : Map[String, Seq[String]]
    def contentType : Option[String]
    def disconnect(): Unit
}

trait HttpClient {
    def connect(
        url        : String,
        credentials: Option[Credentials],
        cookieStore: org.apache.http.client.CookieStore,
        method     : String,
        headers    : Map[String, Seq[String]],
        content    : ⇒ Array[Byte]
    ): HttpResponse

    def shutdown(): Unit
}
