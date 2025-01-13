package org.orbeon.oxf.util

import org.orbeon.oxf.xml.ElemFilter
import org.scalatest.funspec.AnyFunSpec


class HtmlParsingTest extends AnyFunSpec {

  describe("HTML parsing and sanitation") {

    val KeepFilter: String => ElemFilter = _ => ElemFilter.Keep

    val expected = List[(String, String, String => ElemFilter)](
      ("""<a href="https://orbeon.com/">Link</a>"""                 , """<a href="https://orbeon.com/">Link</a>"""              , KeepFilter),
      ("""<A HREF="https://orbeon.com/">Link</A>"""                 , """<a href="https://orbeon.com/">Link</a>"""              , KeepFilter),
      ("""<a href="javascript:alert('')">Link</a>"""                , """<a>Link</a>"""                                         , KeepFilter),
      ("""This is a <b>bold</b> thing to say!"""                    , """This is a <b>bold</b> thing to say!"""                 , KeepFilter),
      ("""<script>alert("");</script>"""                            , """"""                                                    , KeepFilter),
      ("""This is a <b onclick="alert('')">bold</b> thing to say!""", """This is a <b>bold</b> thing to say!"""                 , KeepFilter),
      ("""This is a <b ONCLICK="alert('')">bold</b> thing to say!""", """This is a <b>bold</b> thing to say!"""                 , KeepFilter),
      ("""This is a <b foo="javascript:">bold</b> thing to say!"""  , """This is a <b>bold</b> thing to say!"""                 , KeepFilter),
      ("""<a href="https://orbeon.com/">Link</a>"""                 , """Link"""                                                , s => if (s == "a") ElemFilter.Remove else ElemFilter.Keep),
      ("""Before <div>this is <i>italics</i>, etc.</div> after"""   , """Before <div>this is <i>italics</i>, etc.</div> after""", KeepFilter),
      ("""Before <div>this is <i>italics</i>, etc.</div> after"""   , """Before this is <i>italics</i>, etc. after"""           , s => if (s == "div") ElemFilter.Remove            else ElemFilter.Keep),
      ("""Before <div>this is <i>italics</i>, etc.</div> after"""   , """Before  after"""                                       , s => if (s == "div") ElemFilter.RemoveWithContent else ElemFilter.Keep),
      // JVM: TagSoup removes element before we get to it, and keeps its content, so right now we can't make this work correctly cross-enviroment
//      ("""This is a totally <custom>element</custom>"""             , """This is a totally element"""                           , KeepFilter),
    )

    for ((input, output, filter) <- expected) {
      it(s"should sanitize `$input` to `$output`") {
        assert(HtmlParsing.sanitizeHtmlString(input, filter) == output)
      }
    }
  }
}
