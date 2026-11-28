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

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.SignatureVerifierProcessor.SignatureAlgorithm
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{Base64, SecureUtils}
import org.orbeon.oxf.xml.*
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.oxf.xml.dom.{IOSupport, LocationSAXWriter}

import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, Signature}
import scala.util.chaining.*


private object SignatureProcessor {
  private val INPUT_PRIVATE_KEY   = "private-key"
  private val SIGNED_DATA_ELEMENT = "signed-data"
  private val DATA_ELEMENT        = "data"
  private val SIGNATURE_ELEMENT   = "signature"
  private val ALGORITHM_ATTRIBUTE = "algorithm"
}

class SignatureProcessor extends ProcessorImpl {

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
  addInputInfo(new ProcessorInputOutputInfo(SignatureProcessor.INPUT_PRIVATE_KEY))
  addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

  override def createOutput(name: String): ProcessorOutput =
    addOutput(
      name,
      new ProcessorOutputImpl(this, name) {
        override def readImpl(context: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          val (keySpec, explicitAlgOpt, sigAlgorithm) = {
            val privDoc        = readCacheInputAsOrbeonDom(context, SignatureProcessor.INPUT_PRIVATE_KEY)
            val explicitAlgOpt = privDoc.getRootElement.attributeValueOpt(SignatureProcessor.ALGORITHM_ATTRIBUTE).flatMap(_.trimAllToOpt)
            val sigAlgorithm   = SignatureAlgorithm.fromOptString(explicitAlgOpt)
            val privString     = XPathUtils.selectStringValueNormalize(privDoc, "/private-key")
            val privBytes      = Base64.decode(privString)
            (new PKCS8EncodedKeySpec(privBytes), explicitAlgOpt, sigAlgorithm)
          }

          val signature = {
            val keyFactory =
              SecureUtils.preferredProviderOpt("KeyFactory", sigAlgorithm.keyAlgorithm) match {
                case Some(provider) => KeyFactory.getInstance(sigAlgorithm.keyAlgorithm, provider)
                case None           => KeyFactory.getInstance(sigAlgorithm.keyAlgorithm)
              }
            val privKey = keyFactory.generatePrivate(keySpec)

            (SecureUtils.preferredProviderOpt("Signature", sigAlgorithm.signatureAlgorithm) match {
              case Some(provider) => Signature.getInstance(sigAlgorithm.signatureAlgorithm, provider)
              case None           => Signature.getInstance(sigAlgorithm.signatureAlgorithm)
            }).tap(_.initSign(privKey))
          }

          val dataDoc      = readCacheInputAsOrbeonDom(context, ProcessorImpl.INPUT_DATA)
          val dataAsString = IOSupport.domToStringJava(dataDoc)

          signature.update(dataAsString.getBytes(CharsetNames.Utf8))
          val sig = Base64.encode(signature.sign, useLineBreaks = true)

          val sigAtts =
            if (explicitAlgOpt.isDefined)
              List(SignatureProcessor.ALGORITHM_ATTRIBUTE -> sigAlgorithm.signatureAlgorithm)
            else
              Nil

          implicit val rx: XMLReceiver = xmlReceiver
          withDocument {
            withElement(SignatureProcessor.SIGNED_DATA_ELEMENT) {
              withElement(SignatureProcessor.DATA_ELEMENT) {
                (new LocationSAXWriter)
                  .tap(_.setContentHandler(xmlReceiver))
                  .tap(_.write(dataDoc.getRootElement))
              }
              element(SignatureProcessor.SIGNATURE_ELEMENT, atts = sigAtts, text = sig)
            }
          }
        }
      }
    )
}
