/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.generator;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.ContentHandler;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class KeyPairGenerator extends ProcessorImpl {


    public KeyPairGenerator() {
        addOutputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(OUTPUT_DATA));
    }


    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {

                try {
                    java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("DSA", "SUN");
                    SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
                    keyGen.initialize(1024, random);

                    KeyPair pair = keyGen.generateKeyPair();
                    PrivateKey priv = pair.getPrivate();
                    PublicKey pub = pair.getPublic();

                    String pubKey = new sun.misc.BASE64Encoder().encode(new X509EncodedKeySpec(pub.getEncoded()).getEncoded());
                    String privKey = new sun.misc.BASE64Encoder().encode(new PKCS8EncodedKeySpec(priv.getEncoded()).getEncoded());

                    contentHandler.startDocument();

                    contentHandler.startElement("", "key-pair", "key-pair", XMLUtils.EMPTY_ATTRIBUTES);

                    contentHandler.startElement("", "private-key", "private-key", XMLUtils.EMPTY_ATTRIBUTES);
                    char[] privKeyChar = new char[privKey.length()];
                    privKey.getChars(0, privKey.length(), privKeyChar, 0);
                    contentHandler.characters(privKeyChar, 0, privKeyChar.length);
                    contentHandler.endElement("", "private-key", "private-key");

                    contentHandler.startElement("", "public-key", "public-key", XMLUtils.EMPTY_ATTRIBUTES);
                    char[] pubKeyChar = new char[pubKey.length()];
                    pubKey.getChars(0, pubKey.length(), pubKeyChar, 0);
                    contentHandler.characters(pubKeyChar, 0, pubKeyChar.length);
                    contentHandler.endElement("", "public-key", "public-key");

                    contentHandler.endElement("", "key-pair", "key-pair");

                    contentHandler.endDocument();
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };

        addOutput(name, output);
        return output;
    }
}
