package org.orbeon.oxf.fr

import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.{SecureUtils, XPath}
import org.orbeon.oxf.xml.EncodeDecode.{decodeBytes, encodeBytes}
import org.orbeon.oxf.xml.{SAXStoreBinaryFormat, TransformerUtils}
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.util.Base64
import scala.util.Try


trait FormRunnerEncodeDecode {

  // The data can be encoded and encrypted, or just encoded in Base64 format.
  // The encrypted version is only used by the "Test PDF" feature as of 2021-10-13.
  // 2023-12-09: Adding support for the console.
  //@XPathFunction
  def decodeSubmittedFormData(data: String): DocumentNodeInfoType =
    Try(decodeXmlNodeInfo(data, forceEncryption = true)) getOrElse {
      XFormsCrossPlatformSupport.stringToTinyTree(
        XPath.GlobalConfiguration,
        new String(Base64.getDecoder.decode(data), "UTF-8"),
        handleXInclude = false,
        handleLexical  = true
      )
    }

  // Only used by the "Test PDF" feature as of 2021-10-13.
  def encodeFormDataToSubmit(document: om.NodeInfo): String =
    encodeXmlNodeInfo(document, compress = true, encrypt = true)

  private def encodeXmlNodeInfo(document: om.NodeInfo, compress: Boolean, encrypt: Boolean): String = {
    val saxStore = TransformerUtils.tinyTreeToSAXStore(document)
    val bytes = SAXStoreBinaryFormat.serialize(saxStore)
    encodeBytes(bytes, compress, encrypt, SecureUtils.KeyUsage.GeneralNoCheck)
  }

  private def decodeXmlNodeInfo(encodedXML: String, forceEncryption: Boolean): om.DocumentInfo = {
    val bytes = decodeBytes(encodedXML, forceEncryption, SecureUtils.KeyUsage.GeneralNoCheck)
    val saxStore = SAXStoreBinaryFormat.deserialize(bytes)
    TransformerUtils.saxStoreToTinyTree(XPath.GlobalConfiguration, saxStore)
  }
}
