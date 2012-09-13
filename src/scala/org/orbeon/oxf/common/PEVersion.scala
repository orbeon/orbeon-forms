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
import java.util.{Date ⇒ JDate}
import org.dom4j.Document
import org.orbeon.oxf.pipeline.InitUtils.withPipelineContext
import org.orbeon.oxf.processor.DOMSerializer
import org.orbeon.oxf.processor.ProcessorImpl.{INPUT_DATA, OUTPUT_DATA}
import org.orbeon.oxf.processor.SignatureVerifierProcessor
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.resources.ResourceNotFoundException
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.util.PipelineUtils
import org.orbeon.oxf.util.ScalaUtils.nonEmptyOrNone
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.DumbXPathDependencies
import org.orbeon.oxf.xforms.analysis.PathMapXPathDependencies
import org.orbeon.oxf.xforms.analysis.XPathDependencies
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.processor.SignatureVerifierProcessor.SignatureException
import org.orbeon.errorified.Exceptions

class PEVersion extends Version {

    import PEVersion._
    import Version._

    // Check license file
    // If the license doesn't pass, throw an exception so that processing is interrupted
    checkLicense()

    private def checkLicense(): Unit = {

        def licenseError(message: String, throwable: Option[Throwable] = None) = {
            logger.error(message + ". " + LicenseMessage)
            throw throwable getOrElse new OXFException(message)
        }

        val licenseInfo =
            try {
                val key = PipelineUtils.createURLGenerator(OrbeonPublicKeyURL)

                // Remove blank spaces in license file as that's the way it was signed
                val rawLicenceDocument = ResourceManagerWrapper.instance.getContentAsDOM4J(LicensePath)
                val compactString = Dom4jUtils.domToCompactString(rawLicenceDocument)
                val licenseDocument = Dom4jUtils.readDom4j(compactString)
                val licence = new DOMGenerator(licenseDocument, "license", DOMGenerator.ZeroValidity, LicenseURL)

                // Connect pipeline
                val verifierProcessor = new SignatureVerifierProcessor
                PipelineUtils.connect(licence, OUTPUT_DATA, verifierProcessor, INPUT_DATA)
                PipelineUtils.connect(key, OUTPUT_DATA, verifierProcessor, SignatureVerifierProcessor.INPUT_PUBLIC_KEY)
                val serializer = new DOMSerializer
                PipelineUtils.connect(verifierProcessor, OUTPUT_DATA, serializer, INPUT_DATA)

                // Execute pipeline to obtain license document
                val licenceDocument =
                    withPipelineContext { pipelineContext ⇒
                        serializer.reset(pipelineContext)
                        serializer.start(pipelineContext)
                        serializer.getDocument(pipelineContext)
                    }

                LicenseInfo(licenceDocument)
            } catch {
                case t: Throwable ⇒
                    Exceptions.getRootThrowable(t) match {
                        case e: ResourceNotFoundException ⇒
                            licenseError("License file not found for " + VersionString)
                        case e: SignatureException ⇒
                            licenseError("Invalid license file signature for " + VersionString)
                        case e: Exception ⇒
                            licenseError("Error loading license file for " + VersionString, Option(e))
                    }
            }

        // Check version
        licenseInfo.version foreach { version ⇒
            if (isVersionExpired(VersionNumber, version))
                licenseError("License version doesn't match. License version is: " + version + ", Orbeon Forms version is: " + VersionNumber)
        }

        // Check expiration
        licenseInfo.expiration foreach { expiration ⇒
            if (new JDate().after(expiration))
                licenseError("License has expired on " + DateFormat.getDateInstance.format(expiration))
        }

        logger.info("This installation of " + VersionString + " is licensed to: " + licenseInfo.toString)
    }

    def checkPEFeature(featureName: String) = ()

    def isPEFeatureEnabled(featureRequested: Boolean, featureName: String) = featureRequested

    def createUIDependencies(containingDocument: XFormsContainingDocument): XPathDependencies = {
        val requested = containingDocument.getStaticState.isXPathAnalysis
        if (requested) new PathMapXPathDependencies(containingDocument) else new DumbXPathDependencies
    }
}

private object PEVersion {

    val LicensePath        = "/config/license.xml"
    val LicenseURL         = "oxf:" + LicensePath
    val OrbeonPublicKeyURL = "oxf:/config/orbeon-public.xml"
    val LicenseMessage     = "Please make sure a proper license file is placed under WEB-INF/resources/config/license.xml."

    def isVersionExpired(currentVersion: String, licenseVersion: String): Boolean = {
        (majorMinor(currentVersion), majorMinor(licenseVersion)) match {
            case (Some((currentMajor, currentMinor)), Some((licenseMajor, licenseMinor))) ⇒
                currentMajor > licenseMajor || (currentMajor == licenseMajor && currentMinor > licenseMinor)
            case _ ⇒
                true
        }
    }

    def majorMinor(versionString: String): Option[(Int, Int)] = {
        val ints = versionString.split('.') take 2 map (_.toInt)
        if (ints.size == 2) Some(ints(0), ints(1)) else None
    }

    case class LicenseInfo(licensor: String, licensee: String, organization: String, email: String, issued: String, version: Option[String], expiration: Option[JDate]) {
        override def toString = {
            val expiresString = expiration map (" and expires on " + DateFormat.getDateInstance.format(_)) getOrElse ""
            val versionString = version map (" for version " + _) getOrElse ""

            licensee + " / " + organization + " / " + email + versionString + expiresString
        }
    }

    object LicenseInfo {
        def apply(licenceDocument: Document): LicenseInfo = {

            import org.orbeon.oxf.xml.XPathUtils.selectStringValueNormalize
            def select(s: String) = selectStringValueNormalize(licenceDocument, "/license/" + s)

            val licensor      = select("licensor")
            val licensee      = select("licensee")
            val organization  = select("organization")
            val email         = select("email")
            val issued        = select("issued")

            val versionOpt    = nonEmptyOrNone(select("version"))
            val expirationOpt = nonEmptyOrNone(select("expiration")) map (s ⇒ new JDate(DateUtils.parse(s)))

            LicenseInfo(licensor, licensee, organization, email, issued, versionOpt, expirationOpt)
        }
    }
}