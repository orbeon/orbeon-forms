// MIT License

// Copyright (c) 2026 Bram Luyten

// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:

// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.

// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

// ORBEON: The code in this file was adapted from the original source to fit into
// the Orbeon Forms project. The result is redistributed under the same MIT License
// as the original code, and any modifications made by Orbeon Forms are also
// covered under this license.
package org.orbeon.oxf.xforms.contentfilter

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.NormalizerPlatform

import java.util.regex.Pattern
import scala.collection.mutable


case class ProfanityMatch(
  word : String,
  start: Int,
  end  : Int
) extends Ordered[ProfanityMatch] {
  override def compare(that: ProfanityMatch): Int = {
    val cmp = Integer.compare(this.start, that.start)
    if (cmp != 0) cmp else Integer.compare(this.end, that.end)
  }
}

/**
 * Fast, evasion-resistant profanity detector with content position tracking.
 */
final class ProfanityFilter private (
  private val substringTrie: AhoCorasickTrie,
  private val shortWords: Set[String]
) {

  /**
   * Returns `true` if the input contains any blocked term after normalization.
   */
  def containsProfanity(input: String): Boolean =
    findFirstProfanity(input).isDefined

  /**
   * Finds the first profanity match in the input text with position tracking.
   */
  def findFirstProfanity(input: String): Option[ProfanityMatch] =
    findAllMatches(input).headOption

  /**
   * Finds all profanity matches in the input text.
   */
  def findAllMatches(input: String): List[ProfanityMatch] = {

    if (input.isAllBlank)
      return Nil

    val normInput = ProfanityFilter.normalizeWithMapping(input)
    val normalized = normInput.normalized

    val results = mutable.ListBuffer.empty[ProfanityMatch]

    // 1. Check stripped input for longer words (substring match with Aho-Corasick)
    val strippedSb = new java.lang.StringBuilder(normalized.length)
    val strippedToNormIndex = new Array[Int](normalized.length + 1)
    var strippedLen = 0

    for (i <- 0 until normalized.length) {
      val c = normalized.charAt(i)
      if (Character.isLetterOrDigit(c)) {
        strippedToNormIndex(strippedLen) = i
        strippedSb.append(c)
        strippedLen += 1
      }
    }
    strippedToNormIndex(strippedLen) = normalized.length

    val stripped = strippedSb.toString
    if (stripped.nonEmpty) {
      val emits = substringTrie.parseText(stripped)
      for (emit <- emits) {
        val normStart = strippedToNormIndex(emit.start)
        val normEnd   = strippedToNormIndex(emit.end) + 1
        val origStart = normInput.toOriginalIndex(normStart)
        val origEnd   = normInput.toOriginalIndex(normEnd)
        results += ProfanityMatch(emit.keyword, origStart, origEnd)
      }
    }

    // 2. Check individual tokens for short words (whole-word match only)
    val matcher = ProfanityFilter.SeparatorsPattern.matcher(normalized)
    var lastEnd = 0
    while (matcher.find()) {
      val tokenStart = lastEnd
      val tokenEnd = matcher.start()
      if (tokenEnd > tokenStart) {
        val token = normalized.substring(tokenStart, tokenEnd)
        if (shortWords.contains(token)) {
          val origStart = normInput.toOriginalIndex(tokenStart)
          val origEnd = normInput.toOriginalIndex(tokenEnd)
          results += ProfanityMatch(token, origStart, origEnd)
        }
      }
      lastEnd = matcher.end()
    }

    if (lastEnd < normalized.length) {
      val tokenStart = lastEnd
      val tokenEnd = normalized.length
      val token = normalized.substring(tokenStart, tokenEnd)
      if (shortWords.contains(token)) {
        val origStart = normInput.toOriginalIndex(tokenStart)
        val origEnd = normInput.toOriginalIndex(tokenEnd)
        results += ProfanityMatch(token, origStart, origEnd)
      }
    }

    results.toList.sorted
  }
}

object ProfanityFilter {

  /** Short words (<= this length) are only matched as whole tokens to avoid false positives. */
  private val ShortWordThreshold = 3

  private val ZeroWidthPattern      = Pattern.compile("[\\u200B-\\u200D\\u200E\\u200F\\u00AD\\uFEFF\\u2060\\u2062-\\u2064]")
  private val CombiningMarksPattern = Pattern.compile("\\p{M}")
  private val SeparatorsPattern     = Pattern.compile("[^\\p{L}\\p{N}]+")

  /**
   * Creates a filter with all bundled word lists (EN, ES, FR, JA, PL + evasion variants).
   */
  def defaultFilter(): ProfanityFilter = {
    val b = builder()
    Language.values.foreach(b.addLanguage)
    b.build()
  }

  /**
   * Creates a filter for the specified languages only. Always includes EXTRA.
   */
  def forLanguages(languages: Language*): ProfanityFilter = {
    val b = builder()
    val requested = mutable.Set(languages*)
    requested += Language.EXTRA
    requested.foreach(b.addLanguage)
    b.build()
  }

  def builder(): Builder = new Builder()

  case class NormalizedInput(normalized: String, origIndices: Array[Int]) {
    def toOriginalIndex(normIndex: Int): Int =
      if (origIndices == null || normIndex >= origIndices.length)
        normIndex
      else
        origIndices(normIndex)
  }

  def normalize(input: String): String =
    normalizeWithMapping(input).normalized

  def normalizeWithMapping(input: String): NormalizedInput = {

    if (input.isAllBlank)
      return NormalizedInput("", Array(0))

    val sb = new java.lang.StringBuilder(input.length)
    var mapping = new Array[Int](input.length * 4 + 1)
    var normLen = 0

    for (i <- 0 until input.length) {
      val c = input.charAt(i)

      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        mapping(normLen) = i
        sb.append(leetFoldChar(c))
        normLen += 1
      } else if (c >= 'A' && c <= 'Z') {
        mapping(normLen) = i
        sb.append(leetFoldChar((c + 32).toChar))
        normLen += 1
      } else {
        var s = String.valueOf(c)
        s = NormalizerPlatform.normalizeNFKD(s)
        s = CombiningMarksPattern.matcher(s).replaceAll("")
        s = ZeroWidthPattern.matcher(s).replaceAll("")

        if (s.nonEmpty) {
          s = s.toLowerCase
          for (k <- 0 until s.length) {
            val folded = leetFoldChar(s.charAt(k))
            if (normLen >= mapping.length - 1) {
              mapping = java.util.Arrays.copyOf(mapping, mapping.length * 2)
            }
            mapping(normLen) = i
            sb.append(folded)
            normLen += 1
          }
        }
      }
    }

    mapping(normLen) = input.length
    val finalMapping = java.util.Arrays.copyOf(mapping, normLen + 1)
    NormalizedInput(sb.toString, finalMapping)
  }

  private def leetFoldChar(c: Char): Char = c match {
    case '0' => 'o'
    case '1' | '!' => 'i'
    case '3' => 'e'
    case '4' => 'a'
    case '5' => 's'
    case '6' => 'g'
    case '7' => 't'
    case '8' => 'b'
    case '9' => 'g'
    case '@' => 'a'
    case '$' => 's'
    case '#' => 'h'
    case '+' => 't'
    case _   => c
  }

  // ---- Language enum ----

  sealed abstract class Language(val dictionary: String)
  object Language {
    case object EN extends Language(ProfanityDictionaries.EN)
    case object ES extends Language(ProfanityDictionaries.ES)
    case object FR extends Language(ProfanityDictionaries.FR)
    case object JA extends Language(ProfanityDictionaries.JA)
    case object PL extends Language(ProfanityDictionaries.PL)
    case object EXTRA extends Language(ProfanityDictionaries.EXTRA)

    val values: Array[Language] = Array(EN, ES, FR, JA, PL, EXTRA)
  }

  // ---- Builder ----

  final class Builder {
    private val words = mutable.ListBuffer.empty[String]

    def addLanguage(language: Language): Builder =
      addWordList(language.dictionary)

    def addWordList(dictionary: String): Builder = {
      dictionary.linesIterator.foreach { line =>
        val trimmed = line.trim
        if (trimmed.nonEmpty && ! trimmed.startsWith("#"))
          words += trimmed
      }
      this
    }

    def addWords(extraWords: Iterable[String]): Builder = {
      words ++= extraWords
      this
    }

    def build(): ProfanityFilter = {
      val substringTb = AhoCorasickTrie.builder()
      val shortSet = mutable.Set.empty[String]

      for (word <- words) {
        val normalized = ProfanityFilter.normalize(word)
        val stripped = ProfanityFilter.SeparatorsPattern.matcher(normalized).replaceAll("")
        if (stripped.nonEmpty) {
          if (stripped.length <= ShortWordThreshold)
            shortSet += stripped
          else
            substringTb.addKeyword(stripped)
        }
      }

      new ProfanityFilter(substringTb.build(), shortSet.toSet)
    }
  }
}
