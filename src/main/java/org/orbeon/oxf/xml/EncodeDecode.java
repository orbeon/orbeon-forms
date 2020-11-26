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
package org.orbeon.oxf.xml;

import org.orbeon.dom.Document;
import org.orbeon.dom.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.util.Compressor;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.oxf.xml.dom4j.LocationDocumentSource;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;


public class EncodeDecode {

    // 2016-09-14: `encrypt = false` only when encoding XForms static state when using server-side state handling.
    public static String encodeXML(Document document, boolean compress, boolean encrypt, boolean location) {

        // Get SAXStore
        // TODO: This is not optimal since we create a second in-memory representation. Should stream instead.
        final SAXStore saxStore = new SAXStore();
        // NOTE: We don't encode XML comments and use only the ContentHandler interface
        final Source source = location ? new LocationDocumentSource(document) : new DocumentSource(document);
        TransformerUtils.sourceToSAX(source, saxStore);

        // Serialize SAXStore to bytes
        // TODO: This is not optimal since we create a third in-memory representation. Should stream instead.
        final byte[] bytes = SAXStoreBinaryFormat.serialize(saxStore);

        // Encode bytes
        return encodeBytes(bytes, compress, encrypt);
    }

    // 2016-09-14: `encrypt = false` only when encoding static state when using server-side state handling, and
    // for some unit tests.
    public static String encodeBytes(byte[] bytesToEncode, boolean compress, boolean encrypt) {
        // Compress if needed
        final byte[] gzipByteArray = compress ? Compressor.compressBytes(bytesToEncode) : null;

        // Encrypt if needed
        if (encrypt) {
            // Perform encryption
            if (gzipByteArray == null) {
                // The data was not compressed above
                return "X1" + SecureUtils.encrypt(bytesToEncode);
            } else {
                // The data was compressed above
                return "X2" + SecureUtils.encrypt(gzipByteArray);
            }
        } else {
            // No encryption
            if (gzipByteArray == null) {
                // The data was not compressed above
                return "X3" + org.orbeon.oxf.util.Base64.encode(bytesToEncode, false);
            } else {
                // The data was compressed above
                return "X4" + org.orbeon.oxf.util.Base64.encode(gzipByteArray, false);
            }
        }
    }

    public static Document decodeXML(String encodedXML, boolean forceEncryption) {

        final byte[] bytes = decodeBytes(encodedXML, forceEncryption);

        // Deserialize bytes to SAXStore
        // TODO: This is not optimal
        final SAXStore saxStore = SAXStoreBinaryFormat.deserialize(bytes);

        // Deserialize SAXStore to dom4j document
        // TODO: This is not optimal
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        final LocationDocumentResult result = new LocationDocumentResult();
        identity.setResult(result);
        try {
            saxStore.replay(identity);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
        return result.getDocument();
    }

    public static byte[] decodeBytes(String encoded, boolean forceEncryption) {
        // Get raw text
        byte[] resultBytes;
        {
            final String prefix = encoded.substring(0, 2);
            final String encodedString = encoded.substring(2);

            final byte[] resultBytes1;
            final byte[] gzipByteArray;
            if (prefix.equals("X1")) {
                // Encryption + uncompressed
                resultBytes1 = SecureUtils.decrypt(encodedString);
                gzipByteArray = null;
            } else if (prefix.equals("X2")) {
                // Encryption + compressed
                resultBytes1 = null;
                gzipByteArray = SecureUtils.decrypt(encodedString);
            } else if (! forceEncryption && prefix.equals("X3")) {
                // No encryption + uncompressed
                resultBytes1 = org.orbeon.oxf.util.Base64.decode(encodedString);
                gzipByteArray = null;
            } else if (! forceEncryption && prefix.equals("X4")) {
                // No encryption + compressed
                resultBytes1 = null;
                gzipByteArray = org.orbeon.oxf.util.Base64.decode(encodedString);
            } else {
                throw new OXFException("Invalid prefix for encoded string: " + prefix);
            }

            // Decompress if needed
            if (gzipByteArray != null) {
                resultBytes = Compressor.uncompressBytes(gzipByteArray);
            } else {
                resultBytes = resultBytes1;
            }
        }
        return resultBytes;
    }
}
