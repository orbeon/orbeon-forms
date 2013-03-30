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
package org.orbeon.oxf.common

import java.text.DateFormat
import org.dom4j.Document
import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.pipeline.InitUtils.withPipelineContext
import org.orbeon.oxf.processor.DOMSerializer
import org.orbeon.oxf.processor.ProcessorImpl.{INPUT_DATA, OUTPUT_DATA}
import org.orbeon.oxf.processor.SignatureVerifierProcessor
import org.orbeon.oxf.processor.SignatureVerifierProcessor.SignatureException
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.resources.ResourceNotFoundException
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.util.PipelineUtils._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.DumbXPathDependencies
import org.orbeon.oxf.xforms.analysis.PathMapXPathDependencies
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import java.io.{FileInputStream, File}
import org.orbeon.oxf.xml.XMLUtils
import scala.util.Try

class PEVersion extends Version {

    import PEVersion._
    import Version._

    // Check license file during construction
    // If the license doesn't pass, throw an exception so that processing is interrupted
    locally {

        def licenseError(message: String, throwable: Option[Throwable] = None) = {
            val fullMessage = VersionString + ": " + message + ". " + LicenseMessage
            logger.error(fullMessage)
            throw throwable getOrElse new OXFException(fullMessage)
        }

        val licenseInfo =
            try {
                val key = createURLGenerator(OrbeonPublicKeyURL)

                // Read license file and remove blank spaces as that's the way it was signed
                val licenseDocument = {

                    def fromResourceManager =
                        Try(ResourceManagerWrapper.instance.getContentAsDOM4J(LicensePath))

                    def fromHomeDirectory =
                        Try {
                            val path = dropTrailingSlash(System.getProperty("user.home")) + "/.orbeon/license.xml"

                            useAndClose(new FileInputStream(new File(path))) { is ⇒
                                Dom4jUtils.readDom4j(is, path, XMLUtils.ParserConfiguration.PLAIN)
                            }
                        }

                    def document = fromResourceManager orElse fromHomeDirectory get

                    Dom4jUtils.readDom4j(Dom4jUtils.domToCompactString(document))
                }

                // Connect pipeline
                val serializer = {
                    val licence = new DOMGenerator(licenseDocument, "license", DOMGenerator.ZeroValidity, LicenseURL)
                    val verifierProcessor = new SignatureVerifierProcessor
                    connect(licence, OUTPUT_DATA, verifierProcessor, INPUT_DATA)
                    connect(key, OUTPUT_DATA, verifierProcessor, SignatureVerifierProcessor.INPUT_PUBLIC_KEY)
                    val result = new DOMSerializer
                    connect(verifierProcessor, OUTPUT_DATA, result, INPUT_DATA)
                    result
                }

                // Execute pipeline to obtain license document
                val licenceDocument =
                    withPipelineContext { pipelineContext ⇒
                        serializer.reset(pipelineContext)
                        serializer.runGetDocument(pipelineContext)
                    }

                LicenseInfo(licenceDocument)
            } catch {
                case t: Throwable ⇒
                    Exceptions.getRootThrowable(t) match {
                        case e: ResourceNotFoundException ⇒
                            licenseError("License file not found")
                        case e: SignatureException ⇒
                            licenseError("Invalid license file signature")
                        case e: Exception ⇒
                            licenseError("Error loading license file", Some(e))
                    }
            }

        licenseInfo.formattedSubscriptionEnd match {
            case Some(end) ⇒
                // There is a subscription end date so we check that the build date is prior to that
                // NOTE: Don't check against the current date as we don't want to depend on that for production licenses
                if (licenseInfo.isBuildAfterSubscriptionEnd)
                    licenseError(s"Subscription ended on: $end, Orbeon Forms build dates from: ${licenseInfo.formattedBuildDate.get}")
            case None ⇒
                // There is no subscription end date so we check against the version
                if (licenseInfo.isBadVersion)
                    licenseError(s"License version doesn't match. License version is: ${licenseInfo.version.get}, Orbeon Forms version is: $VersionNumber")
        }

        // Check expiration against the current date (for non-production licenses)
        if (licenseInfo.isExpired)
            licenseError(s"License has expired on ${licenseInfo.formattedExpiration.get}")

        logger.info("This installation of " + VersionString + " is licensed to: " + licenseInfo.toString)
    }

    def requirePEFeature(featureName: String) = ()

    def isPEFeatureEnabled(featureRequested: Boolean, featureName: String) = featureRequested

    def createUIDependencies(containingDocument: XFormsContainingDocument) = {
        val requested = containingDocument.getStaticState.isXPathAnalysis
        if (requested) new PathMapXPathDependencies(containingDocument) else new DumbXPathDependencies
    }
}

private object PEVersion {

    import Version._

    val LicensePath        = "/config/license.xml"
    val LicenseURL         = "oxf:" + LicensePath
    val OrbeonPublicKeyURL = "oxf:/config/orbeon-public.xml"
    val LicenseMessage     = "Please make sure a proper license file is placed under WEB-INF/resources" + LicensePath + '.'

    def isVersionExpired(currentVersion: String, licenseVersion: String): Boolean =
        (majorMinor(currentVersion), majorMinor(licenseVersion)) match {
            case (Some((currentMajor, currentMinor)), Some((licenseMajor, licenseMinor))) ⇒
                currentMajor > licenseMajor || (currentMajor == licenseMajor && currentMinor > licenseMinor)
            case _ ⇒
                true
        }

    def majorMinor(versionString: String): Option[(Int, Int)] = {
        val ints = versionString.split('.') take 2 map (_.toInt)
        if (ints.size == 2) Some(ints(0), ints(1)) else None
    }

    private val MatchTimestamp = """(.*[^\d]|)(\d{4})(\d{2})(\d{2})\d{4}([^\d].*|)""".r

    def dateFromVersionNumber(currentVersion: String) = (
        Some(currentVersion)
        collect { case MatchTimestamp(_, year, month, day, _) ⇒ year + '-' + month + '-' + day }
        map DateUtils.parseISODateOrDateTime
    )

    case class LicenseInfo(
            versionNumber: String,
            licensor: String,
            licensee: String,
            organization: String,
            email: String,
            issued: String,
            version: Option[String],
            expiration: Option[Long],
            subscriptionEnd: Option[Long]) {

        def isBadVersion                = version         exists (isVersionExpired(versionNumber, _))
        def isExpired                   = expiration      exists (System.currentTimeMillis() > _)
        def isBuildAfterSubscriptionEnd = subscriptionEnd exists (end ⇒ dateFromVersionNumber(versionNumber) exists (_ > end))

        def formattedExpiration      = expiration                           map DateFormat.getDateInstance.format
        def formattedSubscriptionEnd = subscriptionEnd                      map DateFormat.getDateInstance.format
        def formattedBuildDate       = dateFromVersionNumber(versionNumber) map DateFormat.getDateInstance.format

        override def toString = {
            val versionString         = version                  map (" for version " + _) getOrElse ""
            val subscriptionEndString = formattedSubscriptionEnd map (" with subscription ending on " + _) getOrElse ""
            val expiresString         = formattedExpiration      map (" and expires on " + _) getOrElse ""

            licensee + " / " + organization + " / " + email + versionString + subscriptionEndString + expiresString
        }
    }

    object LicenseInfo {
        def apply(licenceDocument: Document): LicenseInfo = {

            import org.orbeon.oxf.xml.XPathUtils.selectStringValueNormalize
            def select(s: String) = selectStringValueNormalize(licenceDocument, "/license/" + s)

            val licensor           = select("licensor")
            val licensee           = select("licensee")
            val organization       = select("organization")
            val email              = select("email")
            val issued             = select("issued")

            val versionOpt         = nonEmptyOrNone(select("version"))
            val expirationOpt      = nonEmptyOrNone(select("expiration"))       map DateUtils.parseISODateOrDateTime
            val subscriptionEndOpt = nonEmptyOrNone(select("subscription-end")) map DateUtils.parseISODateOrDateTime

            LicenseInfo(VersionNumber, licensor, licensee, organization, email, issued, versionOpt, expirationOpt, subscriptionEndOpt)
        }
    }
}