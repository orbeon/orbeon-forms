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
package org.orbeon.oxf.processor.generator

import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.SignatureVerifierProcessor.SignatureAlgorithm
import org.orbeon.oxf.processor._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{Base64, SecureUtils}
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport._

import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}


private object KeyPairGenerator {
  private val KEY_PAIR_ELEMENT    = "key-pair"
  private val PRIVATE_KEY_ELEMENT = "private-key"
  private val PUBLIC_KEY_ELEMENT  = "public-key"
  private val ALGORITHM_ELEMENT   = "algorithm"
}

class KeyPairGenerator extends ProcessorImpl {

  addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

  override def createOutput(name: String): ProcessorOutput =
    addOutput(
      name,
      new ProcessorOutputImpl(this, name) {
        override def readImpl(context: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          val explicitAlgOpt =
            Option(getConnectedInputs.get(ProcessorImpl.INPUT_CONFIG)).flatMap { _ =>
              val configRoot = readCacheInputAsOrbeonDom(context, ProcessorImpl.INPUT_CONFIG).getRootElement
              configRoot.elementOpt(KeyPairGenerator.ALGORITHM_ELEMENT)
                .flatMap(_.getStringValue.trimAllToOpt)
                .orElse(configRoot.getStringValue.trimAllToOpt)
            }

          val sigAlgorithm = SignatureAlgorithm.fromOptString(explicitAlgOpt)

          val keyGen =
            SecureUtils.preferredProviderOpt("KeyPairGenerator", sigAlgorithm.keyAlgorithm) match {
              case Some(provider) => java.security.KeyPairGenerator.getInstance(sigAlgorithm.keyAlgorithm, provider)
              case None           => java.security.KeyPairGenerator.getInstance(sigAlgorithm.keyAlgorithm)
            }
          keyGen.initialize(sigAlgorithm.defaultKeySize)

          val pair    = keyGen.generateKeyPair()
          val privKey = Base64.encode(new PKCS8EncodedKeySpec(pair.getPrivate.getEncoded).getEncoded, useLineBreaks = true)
          val pubKey  = Base64.encode(new X509EncodedKeySpec(pair.getPublic.getEncoded).getEncoded, useLineBreaks = true)

          val keyAtts =
            if (explicitAlgOpt.isDefined)
              List(KeyPairGenerator.ALGORITHM_ELEMENT -> sigAlgorithm.signatureAlgorithm)
            else
              Nil

          implicit val rx: XMLReceiver = xmlReceiver
          withDocument {
            withElement(KeyPairGenerator.KEY_PAIR_ELEMENT) {
              element(KeyPairGenerator.PRIVATE_KEY_ELEMENT, atts = keyAtts, text = privKey)
              element(KeyPairGenerator.PUBLIC_KEY_ELEMENT,  atts = keyAtts, text = pubKey)
            }
          }
        }
      }
    )
}
