/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.resources.handler

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit


class DataURLDecoderTest extends AssertionsForJUnit {

  val EncodedQuote = "SWYgeW91IGNhbid0IGV4cGxhaW4gaXQgc2ltcGx5LCB5b3UgZG9uJ3QgdW5kZXJzdGFuZCBpdCB3ZWxsIGVub3VnaC4="

  @Test def testDecodeImage(): Unit = {
    val decoded = DataURLDecoder.decode("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg==")

    assert("image/png" === decoded.mediatype)
    assert(None        === decoded.charset)
    assert("image/png" === decoded.contentType)
    assert(None        === decoded.asString)
  }

  @Test def testDecodeText(): Unit = {
    val decoded = DataURLDecoder.decode("data:text/plain;base64," + EncodedQuote)

    assert("text/plain"          === decoded.mediatype)
    assert(Some("US-ASCII")      === decoded.charset)
    assert("text/plain;US-ASCII" === decoded.contentType)
    assert(Some("If you can't explain it simply, you don't understand it well enough.") === decoded.asString)
  }

  @Test def testCharset(): Unit = {
    val decoded = DataURLDecoder.decode("data:text/plain;charset=UTF-8;base64," + EncodedQuote)

    assert("text/plain"          === decoded.mediatype)
    assert(Some("UTF-8")         === decoded.charset)
    assert("text/plain;UTF-8"    === decoded.contentType)
    assert(Some("If you can't explain it simply, you don't understand it well enough.") === decoded.asString)
  }

  @Test def testNoContentType(): Unit = {
    val decoded = DataURLDecoder.decode("data:;base64," + EncodedQuote)

    assert("text/plain"          === decoded.mediatype)
    assert(Some("US-ASCII")      === decoded.charset)
    assert("text/plain;US-ASCII" === decoded.contentType)
    assert(Some("If you can't explain it simply, you don't understand it well enough.") === decoded.asString)
  }

  @Test def testNoBase64(): Unit = {
    val decoded = DataURLDecoder.decode("data:text/plain;charset=UTF-8," + EncodedQuote)

    assert("text/plain"          === decoded.mediatype)
    assert(Some("UTF-8")         === decoded.charset)
    assert("text/plain;UTF-8"    === decoded.contentType)
    assert(Some(EncodedQuote)    === decoded.asString)
  }
}
