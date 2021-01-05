package org.orbeon.oxf.util

// TODO: Mostly duplicated from `org.orbeon.oxf.resources.handler.DataURLDecoder`
// Decode as per https://tools.ietf.org/html/rfc2397
object DataURLDecoder {

  private val DefaultMediatype = "text/plain"
  private val DefaultCharset = "US-ASCII"

  def decode(url: String): DecodedDataURL = {

    require(url.startsWith("data"))

    val comma      = url.indexOf(',')
    val beforeData = url.substring("data:".length, comma)
    val data       = url.substring(comma + 1)

    val mediatype = ContentTypes.getContentTypeMediaType(beforeData) getOrElse DefaultMediatype
    val params    = parseContentTypeParameters(beforeData)

    val isBase64 = params.contains("base64")

    val charset =
      if (mediatype.startsWith("text/"))
        params.get("charset").flatten.orElse(Some(DefaultCharset))
      else
        None

    // See: https://github.com/orbeon/orbeon-forms/issues/1065
    val decodedData =
      if (isBase64)
        Base64.decode(data)
      else
        throw new IllegalArgumentException

    DecodedDataURL(decodedData, mediatype, charset)
  }

  // Support missing attribute values so we can collect ";base64" as well
  private def parseContentTypeParameters(s: String) = {

    def parseParameter(p: String) = {
      val parts = p.split('=')
      (parts(0), parts.lift(1))
    }

    s.split(';') drop 1 map parseParameter toMap
  }
}

case class DecodedDataURL(bytes: Array[Byte], mediatype: String, charset: Option[String]) {
  def contentType = mediatype + (charset map (";" + _) getOrElse "")
  def asString    = charset map (new String(bytes, _))
}