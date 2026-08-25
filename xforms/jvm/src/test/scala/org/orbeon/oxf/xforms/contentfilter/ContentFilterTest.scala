package org.orbeon.oxf.xforms.contentfilter

import org.orbeon.oxf.xforms.contentfilter.api.java.{ContentFilterProvider, ContentFilterResult}
import org.scalatest.funspec.AnyFunSpecLike

import java.util.Collections
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*


class ContentFilterTest extends AnyFunSpecLike {

  private class TestContentFilterProvider extends ContentFilterProvider {
    override def init(): Unit = ()
    override def destroy(): Unit = ()
    override def filterContent(text: String, language: String, extension: java.util.Map[String, AnyRef]): ContentFilterResult = {
      if (text != null && text.contains("profane")) {
        new ContentFilterResult.RejectResult(
          if (language == "fr") "Le texte contient des mots non autorisés." else "The text contains profane words.",
          List(new ContentFilterResult.Match(14, 21, "profane")).asJava
        )
      } else {
        new ContentFilterResult.AcceptResult()
      }
    }
  }

  describe("ContentFilter default behavior") {

    it("returns true for clean text when default behavior is invoked") {
      val isClean = ContentFilter.isContentClean("Hello world this is clean text")
      assert(isClean)
    }

    it("returns AcceptResult for clean text") {
      val res = ContentFilter.checkContent("Hello world")
      assert(res.isEmpty)
    }

    it("escapes XML characters in clean text highlight") {
      val html = ContentFilter.highlightContent("Hello & <world>")
      assert(html == "Hello &amp; &lt;world&gt;")
    }
  }

  describe("ContentFilter with TestContentFilterProvider") {

    val provider: ContentFilterProvider = (new TestContentFilterProvider).tap(_.init())

    it("accepts clean content") {
      val res = provider.filterContent("This is a clean sentence for testing.", "en", Collections.emptyMap())
      assert(res.isInstanceOf[ContentFilterResult.AcceptResult])
    }

    it("rejects profane content and provides match details") {
      val res = provider.filterContent("This contains profane text", "en", Collections.emptyMap())
      assert(res.isInstanceOf[ContentFilterResult.RejectResult])
      val reject = res.asInstanceOf[ContentFilterResult.RejectResult]
      assert(reject.message.contains("profane"))
      assert(reject.matches.size() == 1)
      val match1 = reject.matches.get(0)
      assert(match1.start == 14)
      assert(match1.end == 21)
      assert(match1.matchedWord == "profane")
    }

    it("rejects profane content in French and returns localized message") {
      val res = provider.filterContent("This contains profane text", "fr", Collections.emptyMap())
      assert(res.isInstanceOf[ContentFilterResult.RejectResult])
      assert(res.message.contains("mots non autorisés"))
    }
  }
}
