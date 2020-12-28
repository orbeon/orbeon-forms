package org.orbeon.oxf.xml


object XMLUtils {

  def maybeAVT(attributeValue: String): Boolean =
    attributeValue.indexOf('{') != -1

  def prefixFromQName(qName: String): String = {
    val colonIndex = qName.indexOf(':')
    if (colonIndex == -1)
      ""
    else
      qName.substring(0, colonIndex)
  }

  def localNameFromQName(qName: String): String = {
    val colonIndex = qName.indexOf(':')
    if (colonIndex == -1)
      qName
    else
      qName.substring(colonIndex + 1)
  }

  def buildQName(prefix: String, localname: String): String =
    if (prefix == null || prefix == "")
      localname
    else
      prefix + ":" + localname

  /**
   * Encode a URI and local name to an exploded QName (also known as a "Clark name") String.
   */
  def buildExplodedQName(uri: String, localname: String): String =
    if ("" == uri)
      localname
    else
      "{" + uri + '}' + localname
}