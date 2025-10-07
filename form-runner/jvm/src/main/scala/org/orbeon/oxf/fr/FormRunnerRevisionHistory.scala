package org.orbeon.oxf.fr

import org.orbeon.oxf.util.{DateUtils, DateUtilsUsingSaxon}


trait FormRunnerRevisionHistory {

  //@XPathFunction
  def compareRfc1123AndIsoDates(rfc1123: String, iso: String): Boolean =
    DateUtilsUsingSaxon.tryParseISODateOrDateTime(iso, DateUtilsUsingSaxon.TimeZone.UTC).contains(DateUtils.parseRFC1123(rfc1123))
}
