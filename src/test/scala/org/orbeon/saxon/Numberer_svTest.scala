package org.orbeon.saxon

import org.orbeon.saxon.number.{AbstractNumberer, Numberer_sv}
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Tests for numberer class for the Swedish language.
 *
 * @see <a href="http://en.wikipedia.org/wiki/Swedish_grammar">http://en.wikipedia.org/wiki/Swedish_grammar</a>
 * @see <a href="http://www2.hhs.se/isa/swedish/chap4.htm">http://www2.hhs.se/isa/swedish/chap4.htm</a>
 */
class Numberer_svTest extends AssertionsForJUnit {

  val N = new Numberer_sv
  val ONE_MILLION = 1000000L
  val ONE_BILLION = 1000000000L // Swedish definition 10^9

  private def check(number: Long, words: String, ordinalWords: String): Unit = {
    assert(words === N.toWords(number))
    assert(ordinalWords === N.toOrdinalWords("", number, AbstractNumberer.LOWER_CASE))
  }

  @Test def testLowNumbers(): Unit = {
    check(0L, "noll", "nollte")
    check(1L, "ett", "första")
    check(2L, "två", "andra")
    check(3L, "tre", "tredje")
    check(4L, "fyra", "fjärde")
    check(5L, "fem", "femte")
    check(6L, "sex", "sjätte")
    check(7L, "sju", "sjunde")
    check(8L, "åtta", "åttonde")
    check(9L, "nio", "nionde")
    check(10L, "tio", "tionde")
    check(11L, "elva", "elfte")
    check(12L, "tolv", "tolfte")
    check(13L, "tretton", "trettonde")
    check(14L, "fjorton", "fjortonde")
    check(15L, "femton", "femtonde")
    check(16L, "sexton", "sextonde")
    check(17L, "sjutton", "sjuttonde")
    check(18L, "arton", "artonde")
    check(19L, "nitton", "nittonde")
    check(20L, "tjugo", "tjugonde")
  }

  @Test def testEvenTens(): Unit = {
    check(10L, "tio", "tionde")
    check(20L, "tjugo", "tjugonde")
    check(30L, "trettio", "trettionde")
    check(40L, "fyrtio", "fyrtionde")
    check(50L, "femtio", "femtionde")
    check(60L, "sextio", "sextionde")
    check(70L, "sjuttio", "sjuttionde")
    check(80L, "åttio", "åttionde")
    check(90L, "nittio", "nittionde")
    check(100L, "etthundra", "etthundrade")
  }

  @Test def testEvenPowersOfTen(): Unit = {
    check(1000L, "ettusen", "tusende")
    check(10000L, "tiotusen", "tiotusende")
    check(100000L, "etthundratusen", "etthundratusende")
    check(ONE_MILLION, "en miljon", "miljonte")
    check(10 * ONE_MILLION, "tio miljoner", "tio miljonte")
    check(100 * ONE_MILLION, "etthundra miljoner", "etthundra miljonte")
    check(ONE_BILLION, "en miljard", "miljardte")
    check(10 * ONE_BILLION, "tio miljarder", "tio miljardte")
    check(100 * ONE_BILLION, "etthundra miljarder", "etthundra miljardte")
  }

  @Test def testPowersOfTenMultiples(): Unit = {
    check(2000L, "tvåtusen", "tvåtusende")
    check(20000L, "tjugotusen", "tjugotusende")
    check(200000L, "tvåhundratusen", "tvåhundratusende")
    check(2 * ONE_MILLION, "två miljoner", "två miljonte")
    check(20 * ONE_MILLION, "tjugo miljoner", "tjugo miljonte")
    check(200 * ONE_MILLION, "tvåhundra miljoner", "tvåhundra miljonte")
    check(2 * ONE_BILLION, "två miljarder", "två miljardte")
    check(20 * ONE_BILLION, "tjugo miljarder", "tjugo miljardte")
    check(200 * ONE_BILLION, "tvåhundra miljarder", "tvåhundra miljardte")
    check(3000L, "tretusen", "tretusende")
    check(30000L, "trettiotusen", "trettiotusende")
    check(300000L, "trehundratusen", "trehundratusende")
    check(3 * ONE_MILLION, "tre miljoner", "tre miljonte")
    check(30 * ONE_MILLION, "trettio miljoner", "trettio miljonte")
    check(300 * ONE_MILLION, "trehundra miljoner", "trehundra miljonte")
    check(3 * ONE_BILLION, "tre miljarder", "tre miljardte")
    check(30 * ONE_BILLION, "trettio miljarder", "trettio miljardte")
    check(300 * ONE_BILLION, "trehundra miljarder", "trehundra miljardte")
    check(8000L, "åttatusen", "åttatusende")
    check(80000L, "åttiotusen", "åttiotusende")
    check(800000L, "åttahundratusen", "åttahundratusende")
    check(8 * ONE_MILLION, "åtta miljoner", "åtta miljonte")
    check(80 * ONE_MILLION, "åttio miljoner", "åttio miljonte")
    check(800 * ONE_MILLION, "åttahundra miljoner", "åttahundra miljonte")
    check(8 * ONE_BILLION, "åtta miljarder", "åtta miljardte")
    check(80 * ONE_BILLION, "åttio miljarder", "åttio miljardte")
    check(800 * ONE_BILLION, "åttahundra miljarder", "åttahundra miljardte")
  }

  @Test def testSmallComposites(): Unit = {
    check(21L, "tjugoett", "tjugoförsta")
    check(32L, "trettiotvå", "trettioandra")
    check(43L, "fyrtiotre", "fyrtiotredje")
    check(54L, "femtiofyra", "femtiofjärde")
    check(65L, "sextiofem", "sextiofemte")
    check(76L, "sjuttiosex", "sjuttiosjätte")
    check(87L, "åttiosju", "åttiosjunde")
    check(98L, "nittioåtta", "nittioåttonde")
    check(89L, "åttionio", "åttionionde")
    check(78L, "sjuttioåtta", "sjuttioåttonde")
    check(67L, "sextiosju", "sextiosjunde")
    check(56L, "femtiosex", "femtiosjätte")
    check(45L, "fyrtiofem", "fyrtiofemte")
    check(34L, "trettiofyra", "trettiofjärde")
    check(23L, "tjugotre", "tjugotredje")
    check(123L, "etthundratjugotre", "etthundratjugotredje")
    check(234L, "tvåhundratrettiofyra", "tvåhundratrettiofjärde")
    check(345L, "trehundrafyrtiofem", "trehundrafyrtiofemte")
    check(456L, "fyrahundrafemtiosex", "fyrahundrafemtiosjätte")
    check(567L, "femhundrasextiosju", "femhundrasextiosjunde")
    check(678L, "sexhundrasjuttioåtta", "sexhundrasjuttioåttonde")
    check(789L, "sjuhundraåttionio", "sjuhundraåttionionde")
    check(901L, "niohundraett", "niohundraförsta")
    check(197L, "etthundranittiosju", "etthundranittiosjunde")
    check(286L, "tvåhundraåttiosex", "tvåhundraåttiosjätte")
    check(375L, "trehundrasjuttiofem", "trehundrasjuttiofemte")
    check(464L, "fyrahundrasextiofyra", "fyrahundrasextiofjärde")
    check(553L, "femhundrafemtiotre", "femhundrafemtiotredje")
    check(642L, "sexhundrafyrtiotvå", "sexhundrafyrtioandra")
    check(731L, "sjuhundratrettioett", "sjuhundratrettioförsta")
  }

  @Test def testMediumComposites(): Unit = {
    check(1234L, "ettusen tvåhundratrettiofyra", "ettusen tvåhundratrettiofjärde")
    check(2345L, "tvåtusen trehundrafyrtiofem", "tvåtusen trehundrafyrtiofemte")
    check(3456L, "tretusen fyrahundrafemtiosex", "tretusen fyrahundrafemtiosjätte")
    check(4567L, "fyratusen femhundrasextiosju", "fyratusen femhundrasextiosjunde")
    check(5678L, "femtusen sexhundrasjuttioåtta", "femtusen sexhundrasjuttioåttonde")
    check(6789L, "sextusen sjuhundraåttionio", "sextusen sjuhundraåttionionde")
    check(7890L, "sjutusen åttahundranittio", "sjutusen åttahundranittionde")
    check(9876L, "niotusen åttahundrasjuttiosex", "niotusen åttahundrasjuttiosjätte")
    check(8765L, "åttatusen sjuhundrasextiofem", "åttatusen sjuhundrasextiofemte")
    check(7654L, "sjutusen sexhundrafemtiofyra", "sjutusen sexhundrafemtiofjärde")
    check(6543L, "sextusen femhundrafyrtiotre", "sextusen femhundrafyrtiotredje")
    check(5432L, "femtusen fyrahundratrettiotvå", "femtusen fyrahundratrettioandra")
    check(4321L, "fyratusen trehundratjugoett", "fyratusen trehundratjugoförsta")
    check(1357L, "ettusen trehundrafemtiosju", "ettusen trehundrafemtiosjunde")
    check(2468L, "tvåtusen fyrahundrasextioåtta", "tvåtusen fyrahundrasextioåttonde")
    check(3579L, "tretusen femhundrasjuttionio", "tretusen femhundrasjuttionionde")
    check(4680L, "fyratusen sexhundraåttio", "fyratusen sexhundraåttionde")
    check(5791L, "femtusen sjuhundranittioett", "femtusen sjuhundranittioförsta")
    check(6801L, "sextusen åttahundraett", "sextusen åttahundraförsta")
    check(7913L, "sjutusen niohundratretton", "sjutusen niohundratrettonde")
    check(8024L, "åttatusen tjugofyra", "åttatusen tjugofjärde")
    check(9135L, "niotusen etthundratrettiofem", "niotusen etthundratrettiofemte")
  }

  @Test def testLargeComposites(): Unit = {
    check(111111L, "etthundraelvatusen etthundraelva", "etthundraelvatusen etthundraelfte")
    check(1111111L, "en miljon etthundraelvatusen etthundraelva",
      "en miljon etthundraelvatusen etthundraelfte")
    check(11111111L, "elva miljoner etthundraelvatusen etthundraelva",
      "elva miljoner etthundraelvatusen etthundraelfte")
    check(111111111L, "etthundraelva miljoner etthundraelvatusen etthundraelva",
      "etthundraelva miljoner etthundraelvatusen etthundraelfte")
    check(1111111111L, "en miljard etthundraelva miljoner etthundraelvatusen etthundraelva",
      "en miljard etthundraelva miljoner etthundraelvatusen etthundraelfte")
    check(11111111111L, "elva miljarder etthundraelva miljoner etthundraelvatusen etthundraelva",
      "elva miljarder etthundraelva miljoner etthundraelvatusen etthundraelfte")
    check(111111111111L,
      "etthundraelva miljarder etthundraelva miljoner etthundraelvatusen etthundraelva",
      "etthundraelva miljarder etthundraelva miljoner etthundraelvatusen etthundraelfte")
    check(222222L, "tvåhundratjugotvåtusen tvåhundratjugotvå", "tvåhundratjugotvåtusen tvåhundratjugoandra")
    check(2222222L, "två miljoner tvåhundratjugotvåtusen tvåhundratjugotvå",
      "två miljoner tvåhundratjugotvåtusen tvåhundratjugoandra")
    check(22222222L, "tjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugotvå",
      "tjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugoandra")
    check(222222222L, "tvåhundratjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugotvå",
      "tvåhundratjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugoandra")
    check(2222222222L, "två miljarder tvåhundratjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugotvå",
      "två miljarder tvåhundratjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugoandra")
    check(22222222222L,
      "tjugotvå miljarder tvåhundratjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugotvå",
      "tjugotvå miljarder tvåhundratjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugoandra")
    check(222222222222L,
      "tvåhundratjugotvå miljarder tvåhundratjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugotvå",
      "tvåhundratjugotvå miljarder tvåhundratjugotvå miljoner tvåhundratjugotvåtusen tvåhundratjugoandra")
  }

  @Test def testWithZeroes(): Unit = {
    check(101L, "etthundraett", "etthundraförsta")
    check(202L, "tvåhundratvå", "tvåhundraandra")
    check(707L, "sjuhundrasju", "sjuhundrasjunde")
    check(1001L, "ettusen ett", "ettusen första")
    check(1002L, "ettusen två", "ettusen andra")
    check(10001L, "tiotusen ett", "tiotusen första")
    check(10011L, "tiotusen elva", "tiotusen elfte")
    check(10002L, "tiotusen två", "tiotusen andra")
    check(10022L, "tiotusen tjugotvå", "tiotusen tjugoandra")
    check(1000001L, "en miljon ett", "en miljon första")
    check(1000011L, "en miljon elva", "en miljon elfte")
    check(1000004L, "en miljon fyra", "en miljon fjärde")
    check(1000044L, "en miljon fyrtiofyra", "en miljon fyrtiofjärde")
    check(100000001L, "etthundra miljoner ett", "etthundra miljoner första")
    check(200000002L, "tvåhundra miljoner två", "tvåhundra miljoner andra")
    check(100800001L, "etthundra miljoner åttahundratusen ett", "etthundra miljoner åttahundratusen första")
    check(200800002L, "tvåhundra miljoner åttahundratusen två", "tvåhundra miljoner åttahundratusen andra")
    check(1000800001L, "en miljard åttahundratusen ett", "en miljard åttahundratusen första")
    check(2000800002L, "två miljarder åttahundratusen två", "två miljarder åttahundratusen andra")
    check(1000807001L, "en miljard åttahundrasjutusen ett", "en miljard åttahundrasjutusen första")
    check(2000807002L, "två miljarder åttahundrasjutusen två", "två miljarder åttahundrasjutusen andra")
  }

  @Test def testVarious(): Unit = {
    // The largest int
    check(2147483647L,
      "två miljarder etthundrafyrtiosju miljoner fyrahundraåttiotretusen sexhundrafyrtiosju",
      "två miljarder etthundrafyrtiosju miljoner fyrahundraåttiotretusen sexhundrafyrtiosjunde")
  }

  @Test def testDateRelated(): Unit = {
    assert("jan" === N.monthName(1, 3, 3))
    assert("januari" === N.monthName(1, 3, 10))
    assert("dec" === N.monthName(12, 3, 3))
    assert("december" === N.monthName(12, 3, 10))
    assert("må" === N.dayName(1, 2, 2))
    assert("m" === N.dayName(1, 1, 2))
    assert("mån" === N.dayName(1, 3, 3))
    assert("månd" === N.dayName(1, 4, 4))
    assert("måndag" === N.dayName(1, 2, 10))
    assert("ti" === N.dayName(2, 2, 2))
    assert("ti" === N.dayName(2, 1, 2))
    assert("tis" === N.dayName(2, 3, 3))
    assert("tisd" === N.dayName(2, 4, 4))
    assert("tisdag" === N.dayName(2, 2, 10))
    assert("on" === N.dayName(3, 2, 2))
    assert("o" === N.dayName(3, 1, 2))
    assert("ons" === N.dayName(3, 3, 3))
    assert("onsd" === N.dayName(3, 4, 4))
    assert("onsdag" === N.dayName(3, 2, 10))
    assert("to" === N.dayName(4, 2, 2))
    assert("to" === N.dayName(4, 1, 2))
    assert("tor" === N.dayName(4, 3, 3))
    assert("tors" === N.dayName(4, 4, 4))
    assert("torsdag" === N.dayName(4, 2, 10))
    assert("fr" === N.dayName(5, 2, 2))
    assert("f" === N.dayName(5, 1, 2))
    assert("fre" === N.dayName(5, 3, 3))
    assert("fred" === N.dayName(5, 4, 4))
    assert("fredag" === N.dayName(5, 2, 10))
    assert("lö" === N.dayName(6, 2, 2))
    assert("l" === N.dayName(6, 1, 2))
    assert("lör" === N.dayName(6, 3, 3))
    assert("lörd" === N.dayName(6, 4, 4))
    assert("lördag" === N.dayName(6, 2, 10))
    assert("sö" === N.dayName(7, 2, 2))
    assert("s" === N.dayName(7, 1, 2))
    assert("sön" === N.dayName(7, 3, 3))
    assert("sönd" === N.dayName(7, 4, 4))
    assert("söndag" === N.dayName(7, 2, 10))
  }
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file
//
// The Initial Developer of the Original Code is Hakan Soderstrom.
//
// Contributor(s): Karel Goossens, Hakan Soderstrom, Erik Bruchez
// See also: http://discuss.orbeon.com/Swedish-localization-contribution-offered-td4656518.html
//