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
package org.orbeon.oxf.processor

import org.orbeon.dom.Document
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{Base64, SecureUtils}
import org.orbeon.oxf.xml.dom.{IOSupport, LocationSAXWriter}
import org.orbeon.oxf.xml.{XMLReceiver, XPathUtils}

import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, Signature, SignatureException}
import scala.util.chaining.*


object SignatureVerifierProcessor {

  val SIGNATURE_DATA_URI       = "http://www/orbeon.com/oxf/signature"
  val SIGNATURE_PUBLIC_KEY_URI = "http://www/orbeon.com/oxf/signature/public-key"
  val INPUT_PUBLIC_KEY         = "public-key"

  sealed abstract class SignatureAlgorithm(
    val keyAlgorithm       : String,
    val signatureAlgorithm : String,
    val defaultKeySize     : Int
  )

  object SignatureAlgorithm {
    case object Dsa extends SignatureAlgorithm("DSA", "SHA1withDSA",   1024)
    case object Rsa extends SignatureAlgorithm("RSA", "SHA256withRSA", 3072)

    val Default: SignatureAlgorithm = Dsa

    def fromOptString(algOpt: Option[String]): SignatureAlgorithm =
      algOpt.flatMap(_.trimAllToOpt) match {
        case None | Some("DSA") | Some("SHA1withDSA")   => Dsa
        case Some("RSA")        | Some("SHA256withRSA") => Rsa
        case Some(other)                                => throw new OXFException(s"Unsupported signature algorithm: `$other`")
      }
  }
}

class SignatureVerifierProcessor extends ProcessorImpl {

  import SignatureVerifierProcessor.*

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA, SIGNATURE_DATA_URI))
  addInputInfo(new ProcessorInputOutputInfo(INPUT_PUBLIC_KEY, SIGNATURE_PUBLIC_KEY_URI))
  addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

  override def createOutput(name: String): ProcessorOutput =
    addOutput(
      name,
      new ProcessorOutputImpl(this, name) {
        override def readImpl(context: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          val dataDoc = readInputAsOrbeonDom(context, ProcessorImpl.INPUT_DATA)

          val signatureElemOpt = dataDoc.getRootElement.elementOpt("signature")
          val sigAlgorithm     = SignatureAlgorithm.fromOptString(signatureElemOpt.flatMap(_.attributeValueOpt("algorithm")))
          val signatureString  = XPathUtils.selectStringValue(dataDoc, "/signed-data/signature").trimAllToEmpty

          val pubKeySpec = {
            val pubDoc  = readCacheInputAsOrbeonDom(context, INPUT_PUBLIC_KEY)
            val pubRoot = pubDoc.getRootElement
            val pubElem =
              (if (pubRoot.getName == "public-keys") pubRoot.elements("public-key") else List(pubRoot))
                .find(e => SignatureAlgorithm.fromOptString(e.attributeValueOpt("algorithm")) == sigAlgorithm)
                .getOrElse(throw new OXFException(s"No public key found for signature algorithm: `${sigAlgorithm.signatureAlgorithm}`"))
            val pubString = pubElem.getStringValue.trimAllToEmpty
            val pubBytes  = Base64.decode(pubString)
            new X509EncodedKeySpec(pubBytes)
          }

          val signature = {
            val keyFactory =
              SecureUtils.preferredProviderOpt("KeyFactory", sigAlgorithm.keyAlgorithm) match {
                case Some(provider) => KeyFactory.getInstance(sigAlgorithm.keyAlgorithm, provider)
                case None           => KeyFactory.getInstance(sigAlgorithm.keyAlgorithm)
              }
            val pubKey = keyFactory.generatePublic(pubKeySpec)

            (SecureUtils.preferredProviderOpt("Signature", sigAlgorithm.signatureAlgorithm) match {
              case Some(provider) => Signature.getInstance(sigAlgorithm.signatureAlgorithm, provider)
              case None           => Signature.getInstance(sigAlgorithm.signatureAlgorithm)
            }).tap(_.initVerify(pubKey))
          }

          val signedDataDoc =
            Document()
              .tap(_.add(dataDoc.getRootElement.elements("data").head.elements.head.detach()))

          signature.update(IOSupport.domToStringJava(signedDataDoc).getBytes(CharsetNames.Utf8))

          // Verify signature and throw in case of failure
          try
            if (! signature.verify(Base64.decode(signatureString)))
              throw new OXFException("Signature verification failed")
          catch {
            case e: SignatureException =>
              throw e
            case _: Exception =>
              // A number of things can fail above, including Base64 decoding
              // NOTE: We don't pass the cause so that we can match on SignatureException as root Exception
              throw new SignatureException("Signature verification failed")
          }
          // Signature verification passed
          (new LocationSAXWriter)
            .tap(_.setContentHandler(xmlReceiver))
            .tap(_.write(signedDataDoc))
        }
      }
    )
}
