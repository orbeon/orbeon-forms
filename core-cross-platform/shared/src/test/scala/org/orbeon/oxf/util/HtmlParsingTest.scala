package org.orbeon.oxf.util

import org.orbeon.oxf.xml.ElemFilter
import org.scalatest.funspec.AnyFunSpec


class HtmlParsingTest extends AnyFunSpec {

  describe("HTML parsing and sanitation") {

    val KeepFilter  : String => ElemFilter = _ => ElemFilter.Keep
    val RemoveFilter: String => ElemFilter = _ => ElemFilter.Remove

    val expected = List[(String, String, String => ElemFilter)](
      ("""<a href="https://orbeon.com/">Link</a>"""                 , """<a href="https://orbeon.com/">Link</a>"""              , KeepFilter),
      ("""<A HREF="https://orbeon.com/">Link</A>"""                 , """<a href="https://orbeon.com/">Link</a>"""              , KeepFilter),
      ("""<a href="javascript:alert('')">Link</a>"""                , """<a>Link</a>"""                                         , KeepFilter),
      ("""<a href="JavaScript:alert('')">Link</a>"""                , """<a>Link</a>"""                                         , KeepFilter),
      ("""<a href="jav&#x09;ascript:alert('')">Link</a>"""          , """<a>Link</a>"""                                         , KeepFilter),
      ("<a href=\"java\nscript:alert('')\">Link</a>"                , """<a>Link</a>"""                                         , KeepFilter),
      ("""<a href="&#106;avascript:alert('')">Link</a>"""           , """<a>Link</a>"""                                         , KeepFilter),
      ("""<img src="JAVA&#x0D;SCRIPT:alert('')">"""                 , """<img>"""                                               , KeepFilter),
      ("""This is a <b>bold</b> thing to say!"""                    , """This is a <b>bold</b> thing to say!"""                 , KeepFilter),
      ("""<script>alert("");</script>"""                            , """"""                                                    , KeepFilter),
      ("""This is a <b onclick="alert('')">bold</b> thing to say!""", """This is a <b>bold</b> thing to say!"""                 , KeepFilter),
      ("""This is a <b ONCLICK="alert('')">bold</b> thing to say!""", """This is a <b>bold</b> thing to say!"""                 , KeepFilter),
      ("""This is a <b foo="javascript:">bold</b> thing to say!"""  , """This is a <b>bold</b> thing to say!"""                 , KeepFilter),
      ("""<a href="https://orbeon.com/">Link</a>"""                 , """Link"""                                                , s => if (s == "a") ElemFilter.Remove else ElemFilter.Keep),
      ("""Before <div>this is <i>italics</i>, etc.</div> after"""   , """Before <div>this is <i>italics</i>, etc.</div> after""", KeepFilter),
      ("""Before <div>this is <i>italics</i>, etc.</div> after"""   , """Before this is <i>italics</i>, etc. after"""           , s => if (s == "div") ElemFilter.Remove            else ElemFilter.Keep),
      ("""Before <div>this is <i>italics</i>, etc.</div> after"""   , """Before  after"""                                       , s => if (s == "div") ElemFilter.RemoveWithContent else ElemFilter.Keep),
      ("""<i class="fa fa-fw fa-plus"></i> Create"""                , """ Create"""                                             , RemoveFilter),
      ("""Let's <b>remove</b> all the <i>markup</i>!"""             , """Let's remove all the markup!"""                        , RemoveFilter),

      // With jsoup, tagsoup, and the browser, the `<custom>` element is removed. So keep this test.
      ("""This is a totally <custom>element</custom>"""             , """This is a totally """                                  , KeepFilter),
      ("""This is a <mark>highlight</mark>!"""                      , """This is a <mark>highlight</mark>!"""                   , KeepFilter),

      // TinyMCE font color and background color
      ("""<span style="color: #e03e2d;">colored</span>"""           , """<span style="color: #e03e2d">colored</span>"""         , KeepFilter),
      ("""<span style="background-color: #2dc26b;">bg</span>"""     , """<span style="background-color: #2dc26b">bg</span>"""   , KeepFilter),
      ("""<span style="color: #e03e2d; background-color: #2dc26b;">both</span>""", """<span style="color: #e03e2d; background-color: #2dc26b">both</span>""", KeepFilter),
      ("""<span style="color: rgb(224, 62, 37); background-color: rgba(45, 194, 107, 0.5);">rgb</span>""", """<span style="color: rgb(224, 62, 37); background-color: rgba(45, 194, 107, 0.5)">rgb</span>""", KeepFilter),

      // TinyMCE text and font styling
      ("""<span style="font-size: 14px; font-family: Arial, sans-serif; text-align: center;">styled</span>""", """<span style="font-size: 14px; font-family: Arial, sans-serif; text-align: center">styled</span>""", KeepFilter),

      // Style sanitization: dangerous styles stripped, safe styles kept
      ("""<span style="color: red; position: fixed; top: 0; z-index: 9999;">overlay</span>""", """<span style="color: red">overlay</span>""", KeepFilter),
      ("""<span style="position: fixed; z-index: 9999;">overlay</span>""", """<span>overlay</span>""", KeepFilter),
      ("""<span style="background-image: url('http://evil.com');">test</span>""", """<span>test</span>""", KeepFilter),
      ("""<span style="color: expression(alert(1));">test</span>""" , """<span>test</span>"""                                   , KeepFilter),
      ("""<span style="color: /*comment*/ red;">test</span>"""       , """<span>test</span>"""                                   , KeepFilter),
      ("""<span style="color: \6a avascript;">test</span>"""         , """<span>test</span>"""                                   , KeepFilter),

      // URL scheme validation
      ("""<a href="data:text/html,<script>alert('')</script>">Link</a>""", """<a>Link</a>"""                                    , KeepFilter),
      ("""<a href="vbscript:alert('')">Link</a>"""                  , """<a>Link</a>"""                                         , KeepFilter),
      ("""<a href="mailto:test@example.com">Email</a>"""            , """<a href="mailto:test@example.com">Email</a>"""         , KeepFilter),
      ("""<a href="tel:+123456789">Call</a>"""                      , """<a href="tel:+123456789">Call</a>"""                   , KeepFilter),
      ("""<a href="/relative/path">Relative</a>"""                  , """<a href="/relative/path">Relative</a>"""               , KeepFilter),

      // Attribute allowlisting: disallowed attributes stripped
      ("""<a href="https://orbeon.com/" ping="https://evil.com">Link</a>""", """<a href="https://orbeon.com/">Link</a>"""      , KeepFilter),
      ("""<div id="clobber" name="test">text</div>"""               , """<div>text</div>"""                                     , KeepFilter),
      ("""<a href="https://orbeon.com/" target="_blank" rel="noopener noreferrer">Link</a>""", """<a href="https://orbeon.com/" target="_blank" rel="noopener noreferrer">Link</a>""", KeepFilter),

      // Images
      ("""<img src="/images/pic.png" alt="Picture" width="100" height="50">""", """<img src="/images/pic.png" alt="Picture" width="100" height="50">""", KeepFilter),
      ("""<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" alt="dot">""", """<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" alt="dot">""", KeepFilter),
      ("""<img src="data:image/svg+xml;base64,PHN2Zz4=">"""         , """<img>"""                                               , KeepFilter),

      // Tables
      ("""<table border="1" cellpadding="2" cellspacing="0"><tbody><tr align="center"><td colspan="2" style="background-color: #eee">Cell</td></tr></tbody></table>""", """<table border="1" cellpadding="2" cellspacing="0"><tbody><tr align="center"><td colspan="2" style="background-color: #eee">Cell</td></tr></tbody></table>""", KeepFilter),

      // Attribute value escaping against breakout
      ("""<a title="foo&quot; onclick=&quot;alert('1')">Link</a>""" , """<a title="foo&quot; onclick=&quot;alert('1')">Link</a>""", KeepFilter),
    )

    for ((input, output, filter) <- expected) {
      it(s"should sanitize `$input` to `$output`") {
        assert(HtmlParsing.sanitizeHtmlString(input, filter) == output)
      }
    }
  }
}
