package org.orbeon.oxf.util

import org.scalatest.funspec.AnyFunSpec


class HtmlParsingTest extends AnyFunSpec {

  describe("HTML sanitation") {

    val FilterNothing = Set.empty[String]
    val FilterA       = Set("a")

    val expected = List(
      ("""<a href="https://orbeon.com/">Link</a>"""                 , """<a href="https://orbeon.com/">Link</a>""", FilterNothing),
      ("""<a href="javascript:alert('')">Link</a>"""                , """<a>Link</a>"""                           , FilterNothing),
      ("""This is a <b>bold</b> thing to say!"""                    , """This is a <b>bold</b> thing to say!"""   , FilterNothing),
      ("""<script>alert("");</script>"""                            , """"""                                      , FilterNothing),
      ("""This is a <b onclick="alert('')">bold</b> thing to say!""", """This is a <b>bold</b> thing to say!"""   , FilterNothing),
      ("""This is a <b foo="javascript:">bold</b> thing to say!"""  , """This is a <b>bold</b> thing to say!"""   , FilterNothing),
      ("""This is a totally <custom>element</custom>"""             , """This is a totally element"""             , FilterNothing),
      ("""<a href="https://orbeon.com/">Link</a>"""                 , """Link"""                                  , FilterA),
    )

    for ((input, output,filter) <- expected) {
      it(s"should sanitize `$input` to `$output`") {
        assert(HtmlParsing.sanitizeHtmlString(input, filter) == output)
      }
    }
  }
}
