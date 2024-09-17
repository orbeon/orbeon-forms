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
package org.orbeon.oxf.xml

import org.orbeon.dom.Document
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.{Compressor, SecureUtils}


object EncodeDecode {

  // 2016-09-14: `encrypt = false` only when encoding XForms static state when using server-side state handling.
  def encodeXML(document: Document, compress: Boolean, encrypt: Boolean, location: Boolean): String = {

    // Get SAXStore
    // TODO: This is not optimal since we create a second in-memory representation. Should stream instead.
    // NOTE: We don't encode XML comments and use only the `ContentHandler` interface
    val saxStore = TransformerUtils.orbeonDomToSAXStore(document, location)

    // Serialize SAXStore to bytes
    // TODO: This is not optimal since we create a third in-memory representation. Should stream instead.
    val bytes = SAXStoreBinaryFormat.serialize(saxStore)

    // Encode bytes
    encodeBytes(bytes, compress, encrypt, SecureUtils.KeyUsage.GeneralNoCheck)
  }

  def decodeXML(encodedXML: String, forceEncryption: Boolean): Document = {

    val bytes = decodeBytes(encodedXML, forceEncryption, SecureUtils.KeyUsage.GeneralNoCheck)

    // Deserialize bytes to SAXStore
    // TODO: This is not optimal
    val saxStore = SAXStoreBinaryFormat.deserialize(bytes)

    // Deserialize `SAXStore` to `Document`
    TransformerUtils.saxStoreToOrbeonDomDocument(saxStore)
  }

  // 2016-09-14: `encrypt = false` only when encoding static state when using server-side state handling, and
  // for some unit tests.
  def encodeBytes(
    bytesToEncode: Array[Byte],
    compress     : Boolean,
    encrypt      : Boolean,
    keyUsage     : SecureUtils.KeyUsage
  ): String = {

    // Compress if needed
    val gzipByteArrayOpt =
      compress option
        Compressor.compressBytes(bytesToEncode)

    // Encrypt/encode
    (encrypt, gzipByteArrayOpt) match {
      case (true,  None)                => "X1" + SecureUtils.encrypt(keyUsage, bytesToEncode)
      case (true,  Some(gzipByteArray)) => "X2" + SecureUtils.encrypt(keyUsage, gzipByteArray)
      case (false, None)                => "X3" + org.orbeon.oxf.util.Base64.encode(bytesToEncode, useLineBreaks = false)
      case (false, Some(gzipByteArray)) => "X4" + org.orbeon.oxf.util.Base64.encode(gzipByteArray, useLineBreaks = false)
    }
  }

  def decodeBytes(
    encoded        : String,
    forceEncryption: Boolean,
    keyUsage       : SecureUtils.KeyUsage
  ): Array[Byte] = {

    // Get raw text
    val prefix        = encoded.substring(0, 2)
    val encodedString = encoded.substring(2)

    // Decrypt/decode
    val rawOrCompressedBytes =
      prefix match {
        case "X1"                      => Left(SecureUtils.decrypt(keyUsage, encodedString))  // encryption    + uncompressed
        case "X2"                      => Right(SecureUtils.decrypt(keyUsage, encodedString)) // encryption    + compressed
        case "X3" if ! forceEncryption => Left(org.orbeon.oxf.util.Base64.decode(encodedString))                  // no encryption + uncompressed
        case "X4" if ! forceEncryption => Right(org.orbeon.oxf.util.Base64.decode(encodedString))                 // no encryption + compressed
        case _                         => throw new OXFException(s"Invalid prefix for encoded string: `$prefix`")
      }

    // Decompress if needed
    rawOrCompressedBytes.fold(identity, Compressor.uncompressBytes)
  }
}