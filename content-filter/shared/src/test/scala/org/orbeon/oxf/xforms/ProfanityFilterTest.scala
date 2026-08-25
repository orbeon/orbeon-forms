package org.orbeon.oxf.xforms

import org.scalatest.funspec.AnyFunSpec

class ProfanityFilterTest extends AnyFunSpec {

  describe("ProfanityFilter position tracking") {

    val filter = ProfanityFilter.defaultFilter()

    it("returns None for clean text") {
      val matchOpt = filter.findFirstProfanity("This is clean text with no bad words.")
      assert(matchOpt.isEmpty)
      assert(! filter.containsProfanity("This is clean text with no bad words."))
    }

    it("detects position of a profane word with punctuation evasion") {
      val input = "Hello f-u-c-k world!"
      val matchOpt = filter.findFirstProfanity(input)
      assert(matchOpt.isDefined)
      val m = matchOpt.get
      assert(m.word == "fuck")
      assert(m.start == 6)
      assert(m.end == 13)
      assert(input.substring(m.start, m.end) == "f-u-c-k")
    }

    it("detects position of short words with exact token boundary") {
      val input = "Look at that ass right there"
      val matchOpt = filter.findFirstProfanity(input)
      assert(matchOpt.isDefined)
      val m = matchOpt.get
      assert(m.word == "ass")
      assert(m.start == 13)
      assert(m.end == 16)
      assert(input.substring(m.start, m.end) == "ass")
    }

    it("returns all profanity matches with positions") {
      val input = "Some f-u-c-k and ass in text"
      val matches = filter.findAllMatches(input)
      assert(matches.size == 2)
      assert(matches(0).word == "fuck")
      assert(matches(0).start == 5)
      assert(matches(0).end == 12)
      assert(matches(1).word == "ass")
      assert(matches(1).start == 17)
      assert(matches(1).end == 20)
    }
  }
}
