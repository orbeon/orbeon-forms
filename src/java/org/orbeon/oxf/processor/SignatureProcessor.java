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
package org.orbeon.oxf.processor;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;

public class SignatureProcessor extends ProcessorImpl {

    public static final String INPUT_PRIVATE_KEY = "private-key";

    private static final String SIGNED_DATA_ELEMENT = "signed-data";
    private static final String DATA_ELEMENT = "data";
    private static final String SIGNATURE_ELEMENT = "signature";

    public SignatureProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_PRIVATE_KEY));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(SignatureProcessor.this, name) {
            public void readImpl(PipelineContext context, final XMLReceiver xmlReceiver) {
                try {
                    final Document privDoc = readCacheInputAsDOM4J(context, INPUT_PRIVATE_KEY);
                    final String privString = XPathUtils.selectStringValueNormalize(privDoc, "/private-key");
                    final byte[] privBytes = Base64.decode(privString);
                    final PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privBytes);
                    final KeyFactory keyFactory = KeyFactory.getInstance("DSA");
                    final PrivateKey privKey = keyFactory.generatePrivate(privKeySpec);

                    final Signature dsa = Signature.getInstance("SHA1withDSA");
                    dsa.initSign(privKey);

                    xmlReceiver.startDocument();
                    xmlReceiver.startElement("", SIGNED_DATA_ELEMENT, SIGNED_DATA_ELEMENT, XMLUtils.EMPTY_ATTRIBUTES);
                    xmlReceiver.startElement("", DATA_ELEMENT, DATA_ELEMENT, XMLUtils.EMPTY_ATTRIBUTES);

                    final Document data = readCacheInputAsDOM4J(context, INPUT_DATA);
                    final String dataStr = Dom4jUtils.domToString(data);
                    dsa.update(dataStr.getBytes("utf-8"));
                    final String sig = new sun.misc.BASE64Encoder().encode(dsa.sign());

                    final LocationSAXWriter saxw = new LocationSAXWriter();
                    saxw.setContentHandler(xmlReceiver);
                    saxw.write(data.getRootElement());

                    xmlReceiver.endElement("", DATA_ELEMENT, DATA_ELEMENT);

                    xmlReceiver.startElement("", SIGNATURE_ELEMENT, SIGNATURE_ELEMENT, XMLUtils.EMPTY_ATTRIBUTES);
                    char[] sigChars = new char[sig.length()];
                    sig.getChars(0, sig.length(), sigChars, 0);
                    xmlReceiver.characters(sigChars, 0, sigChars.length);
                    xmlReceiver.endElement("", SIGNATURE_ELEMENT, SIGNATURE_ELEMENT);

                    xmlReceiver.endElement("", SIGNED_DATA_ELEMENT, SIGNED_DATA_ELEMENT);
                    xmlReceiver.endDocument();

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
