/**
  * Copyright (C) 2019 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.submission

import org.orbeon.dom.Document
import org.orbeon.scaxon.NodeConversions._
import org.scalatest.funspec.AnyFunSpec


class SubmissionUtilsTest extends AnyFunSpec {

  describe("`application/x-www-form-urlencoded` serialization") {

    it("must serialize the basic case") {

      val doc: Document =
        elemToOrbeonDom(
          <PersonName title="Mr">
            <GivenName>René</GivenName>
          </PersonName>
        )

      assert("GivenName=Ren%C3%A9" === SubmissionUtils.createWwwFormUrlEncoded(doc, "&"))
    }

    it("must serialize empty values") {

      val doc: Document =
        elemToOrbeonDom(
          <PersonName title="Mr">
            <GivenName>René</GivenName>
            <LastName/>
          </PersonName>
        )

      assert("GivenName=Ren%C3%A9&LastName=" === SubmissionUtils.createWwwFormUrlEncoded(doc, "&"))
    }

    it("must ignore non-leaf elements") {

      val doc: Document =
        elemToOrbeonDom(
          <PersonName title="Mr">
            <GivenName>René</GivenName>
            <LastName/>
            <Comment>Snow is <i>great!</i></Comment>
          </PersonName>
        )

      assert("GivenName=Ren%C3%A9&LastName=&i=great%21" === SubmissionUtils.createWwwFormUrlEncoded(doc, "&"))
    }

    it("must encode line breaks") {

      val doc: Document =
        elemToOrbeonDom(
          <Comment>This will be
a new line</Comment>
        )

      assert("Comment=This+will+be%0Aa+new+line" === SubmissionUtils.createWwwFormUrlEncoded(doc, "&"))
    }
  }
}
