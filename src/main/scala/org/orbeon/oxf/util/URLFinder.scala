/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.orbeon.oxf.xml.XMLUtils.escapeXMLMinimal
import scala.util.matching.Regex

object URLFinder {

    def replaceWithHyperlink  (s: String) = s"""<a href="$s">$s</a>"""
    def replaceWithPlaceholder(s: String) = s"""<a>$s</a>"""

    // Replace HTTP/HTTPS URLs in a plain string and return an HTML fragment
    def replaceURLs(s: String, replace: String ⇒ String) = {

        val sb = new StringBuilder("<span>")
        var afterPreviousMatch = 0

        // Don't just use replaceAllIn because we need to match on unescaped URLs, yet escape text around URLs
        for (m ← URLMatchRegex.findAllMatchIn(s)) {
            val before = m.before
            val precedingUnmatched = before.subSequence(afterPreviousMatch, before.length)

            sb append escapeXMLMinimal(precedingUnmatched.toString)
            sb append replace(escapeXMLMinimal(Regex.quoteReplacement(m.toString)))

            afterPreviousMatch = m.end
        }

        sb append escapeXMLMinimal(s.substring(afterPreviousMatch))
        sb append "</span>"

        sb.toString
    }

    def findURLs(s: String) =
        URLMatchRegex.findAllIn(s) map Regex.quoteReplacement

    // Useful resources:
    //
    // - http://data.iana.org/TLD/tlds-alpha-by-domain.txt
    // - https://gist.github.com/gruber/8891611
    // - https://gist.github.com/winzig/8894715
    // - https://gist.github.com/dperini/729294
    // - https://gist.github.com/HenkPoley/8899766
    // - http://mathiasbynens.be/demo/url-regex
    // - http://developer.android.com/reference/android/text/util/Linkify.html
    // - https://github.com/android/platform_frameworks_base/blob/master/core/java/android/util/Patterns.java
    // - http://blog.codinghorror.com/the-problem-with-urls/
    //
    // "xn--" domains:
    //
    // - we don't support them yet
    // - "XN--VERMGENSBERATUNG-PWB".size == 24
    // - can also contain numbers
    // - also unclear: they could also appear in text with actual Unicode characters

    private val RegexpDomains              = """[a-z]{2,25}"""
    private val BalancedParensOneLevelDeep = """\([^\s()]*?\([^\s()]+\)[^\s()]*?\)"""
    private val BalancedParens             = """\([^\s]+?\)"""

    // NOTE: All backslashes are escaped below because interpolation with triple quotes interprets backslash escapes.
    private val URLMatchRegex =
        s"""(?xi)
            \\b
            (
              (?:
                https?:
                (?:
                  /{1,3}
                  |
                  [a-z0-9%]
                )
                |
                [a-z0-9.\\-]+[.]
                (?:$RegexpDomains)
                /
              )
              (?:
                [^\\s()<>{}\\[\\]]+
                |
                $BalancedParensOneLevelDeep
                |
                $BalancedParens
              )+
              (?:
                $BalancedParensOneLevelDeep
                |
                $BalancedParens
                |
                [^\\s`!()\\[\\]{};:'".,<>?«»“”‘’]
              )
              |
              (?:                       # non-capturing group
                (?<![@.])               # not preceded by @ or .
                [a-z0-9]+               # lowercase ASCII and numbers for first part
                (?:[.\\-][a-z0-9]+)*    # followed by . or - groups (non-capturing group)
                [.]                     # followed by .
                (?:$RegexpDomains)      # match TLD which is letters only (non-capturing group)
                \\b                     # word boundary (0-length match)
                /?                      # optional trailing /
                (?!@)                   # not followed by @
              )
            )""".r
}
