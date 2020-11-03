package org.orbeon.oxf.util

import java.util.regex.Pattern


case class PathMatcher(regexp: String, mimeType: String, versioned: Boolean) {
  val pattern: Pattern = Pattern.compile(regexp)
}
