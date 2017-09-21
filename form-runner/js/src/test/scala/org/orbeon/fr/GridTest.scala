/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.fr

import org.orbeon.datatypes.Direction
import org.orbeon.xforms._
import org.scalajs.dom
import org.scalatest.FunSpec

class GridTest extends FunSpec {

  describe("The `spaceToExtendCell()` function") {

    $(dom.window.document).find("body").append(
      """
        |<div id="fb≡section-1-control≡grid-1-control" class="xbl-component xbl-fr-grid">
        |    <div class="fr-grid fr-grid-12 fr-grid-css fr-norepeat fr-editable">
        |        <div class="fr-grid-body">
        |            <div id="cell-1-1"  class="fr-grid-td  xforms-activable" data-y="1" data-x="1"            data-w="4"></div>
        |            <div id="cell-1-8"  class="fr-grid-td  xforms-activable" data-y="1" data-x="8" data-h="3"           ></div>
        |            <div id="cell-1-9"  class="fr-grid-td  xforms-activable" data-y="1" data-x="9"            data-w="4"></div>
        |            <div id="cell-2-3"  class="fr-grid-td  xforms-activable" data-y="2" data-x="3" data-h="2" data-w="2"></div>
        |            <div id="cell-2-5"  class="fr-grid-td  xforms-activable" data-y="2" data-x="5"            data-w="3"></div>
        |            <div id="cell-2-9"  class="fr-grid-td  xforms-activable" data-y="2" data-x="9"            data-w="3"></div>
        |            <div id="cell-3-1"  class="fr-grid-td  xforms-activable" data-y="3" data-x="1"            data-w="2"></div>
        |            <div id="cell-3-5"  class="fr-grid-td  xforms-activable" data-y="3" data-x="5" data-h="2" data-w="2"></div>
        |            <div id="cell-3-9"  class="fr-grid-td  xforms-activable" data-y="3" data-x="9"            data-w="4"></div>
        |            <div id="cell-4-8"  class="fr-grid-td  xforms-activable" data-y="4" data-x="8"                      ></div>
        |            <div id="cell-4-10" class="fr-grid-td  xforms-activable" data-y="4" data-x="10"           data-w="3"></div>
        |        </div>
        |    </div>
        |</div>
        |""".stripMargin)

    val expected = List(
      "cell-1-1"  → List(
        Direction.Right → 3,
        Direction.Down  → 0
      ),
      "cell-1-8"  → List(
        Direction.Right → 0,
        Direction.Down  → 0
      ),
      "cell-1-9"  → List(
        Direction.Right → 0,
        Direction.Down  → 0
      ),
      "cell-2-3"  → List(
        Direction.Right → 0,
        Direction.Down  → 1
      ),
      "cell-2-5"  → List(
        Direction.Right → 0,
        Direction.Down  → 0
      ),
      "cell-2-9"  → List(
        Direction.Right → 1,
        Direction.Down  → 0
      ),
      "cell-3-1"  → List(
        Direction.Right → 0,
        Direction.Down  → 1
      ),
      "cell-3-5"  → List(
        Direction.Right → 1,
        Direction.Down  → 0
      ),
      "cell-3-9"  → List(
        Direction.Right → 0,
        Direction.Down  → 0
      ),
      "cell-4-8"  → List(
        Direction.Right → 1,
        Direction.Down  → 0
      ),
      "cell-4-10" → List(
        Direction.Right → 0,
        Direction.Down  → 0
      )
    )

    for {
      (id, dirValue)     ← expected
      (direction, space) ← dirValue
    } locally {
      it(s"must pass moving `$id` ${direction.entryName.toLowerCase}") {
        assert(space == Grid.spaceToExtendCell($(s"#$id")(0), direction))
      }
    }
  }

}