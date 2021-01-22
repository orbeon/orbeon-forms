package org.orbeon.saxon.function

import org.orbeon.io.CharsetNames
import org.orbeon.io.CharsetNames.{Iso88591, Utf8}


object GetRequestHeader {

  def getAndDecodeHeader(
    name     : String,
    encoding : Option[String],
    getter   : String => Option[List[String]]
  ): Option[List[String]] = {

    import CharsetNames._

    val decode: String => String =
      encoding map (_.toUpperCase) match {
        case None | Some(Iso88591) => identity
        case Some(Utf8)            => (s: String) => new String(s.getBytes(Iso88591), Utf8)
        case Some(other)           => throw new IllegalArgumentException(s"invalid `$$encoding` argument `$other`")
      }

    getter(name.toLowerCase) map (_ map decode)
  }
}