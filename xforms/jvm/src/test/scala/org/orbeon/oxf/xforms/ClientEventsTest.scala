/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms

import org.junit.Test
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xforms.event.ClientEvents
import org.orbeon.oxf.xml.dom.Converter._
import org.scalatestplus.junit.AssertionsForJUnit

class ClientEventsTest extends DocumentTestBase with AssertionsForJUnit {

  @Test def adjustIdForRepeatIteration(): Unit = {

    this setupDocument
      <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
           xmlns:xh="http://www.w3.org/1999/xhtml"
           xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
        <xh:head>
          <xf:model>
            <xf:instance id="instance">
              <instance>
                <outer>
                  <inner/>
                  <inner/>
                </outer>
                <outer>
                  <inner/>
                  <inner/>
                  <inner/>
                </outer>
                <outer/>
              </instance>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:repeat id="my-outer-repeat" ref="outer">
            <xf:repeat id="my-inner-repeat" ref="inner">
              <xf:input id="my-input" ref="."/>
            </xf:repeat>
          </xf:repeat>
        </xh:body>
      </xh:html>.toDocument

    assert("my-outer-repeat"               === ClientEvents.adjustIdForRepeatIteration(document, "my-outer-repeat"))
    assert("my-outer-repeat~iteration⊙2"   === ClientEvents.adjustIdForRepeatIteration(document, "my-outer-repeat⊙2"))
    assert("my-inner-repeat⊙2"             === ClientEvents.adjustIdForRepeatIteration(document, "my-inner-repeat⊙2"))
    assert("my-inner-repeat~iteration⊙2-3" === ClientEvents.adjustIdForRepeatIteration(document, "my-inner-repeat⊙2-3"))
    assert("my-input⊙2-3"                  === ClientEvents.adjustIdForRepeatIteration(document, "my-input⊙2-3"))
  }
}