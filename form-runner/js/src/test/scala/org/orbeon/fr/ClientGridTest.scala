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
import org.orbeon.fr.HtmlElementCell._
import org.orbeon.oxf.fr.Cell
import org.orbeon.xforms._
import org.scalajs.dom
import org.scalatest.funspec.AnyFunSpec

class ClientGridTest extends AnyFunSpec {

  describe("The `spaceToExtendCell()` function") {

    $(dom.document).find("body").append(
      """
        |<div id="fb≡section-1-control≡grid-1-control" class="xbl-component xbl-fr-grid">
        |    <div class="fr-grid fr-grid-12 fr-grid-css fr-norepeat fr-editable">
        |        <div class="fr-grid-body">
        |            <div id="cell-1-1"  class="fr-grid-td xforms-activable" data-fr-y="1" data-fr-x="1"               data-fr-w="4"><span/></div>
        |            <div id="cell-1-8"  class="fr-grid-td xforms-activable" data-fr-y="1" data-fr-x="8" data-fr-h="3"              ><span/></div>
        |            <div id="cell-1-9"  class="fr-grid-td xforms-activable" data-fr-y="1" data-fr-x="9"               data-fr-w="4"><span/></div>
        |            <div id="cell-2-3"  class="fr-grid-td xforms-activable" data-fr-y="2" data-fr-x="3" data-fr-h="2" data-fr-w="2"><span/></div>
        |            <div id="cell-2-5"  class="fr-grid-td xforms-activable" data-fr-y="2" data-fr-x="5"               data-fr-w="3"><span/></div>
        |            <div id="cell-2-9"  class="fr-grid-td xforms-activable" data-fr-y="2" data-fr-x="9"               data-fr-w="3"><span/></div>
        |            <div id="cell-3-1"  class="fr-grid-td xforms-activable" data-fr-y="3" data-fr-x="1"               data-fr-w="2"><span/></div>
        |            <div id="cell-3-5"  class="fr-grid-td xforms-activable" data-fr-y="3" data-fr-x="5" data-fr-h="2" data-fr-w="2"><span/></div>
        |            <div id="cell-3-9"  class="fr-grid-td xforms-activable" data-fr-y="3" data-fr-x="9"               data-fr-w="4"><span/></div>
        |            <div id="cell-4-8"  class="fr-grid-td xforms-activable" data-fr-y="4" data-fr-x="8"                            ><span/></div>
        |            <div id="cell-4-10" class="fr-grid-td xforms-activable" data-fr-y="4" data-fr-x="10"              data-fr-w="3"><span/></div>
        |        </div>
        |    </div>
        |</div>
        |""".stripMargin)

    val expected = List(
      "cell-1-1"  -> List(
        Direction.Right -> 3,
        Direction.Down  -> 0
      ),
      "cell-1-8"  -> List(
        Direction.Right -> 0,
        Direction.Down  -> 0
      ),
      "cell-1-9"  -> List(
        Direction.Right -> 0,
        Direction.Down  -> 0
      ),
      "cell-2-3"  -> List(
        Direction.Right -> 0,
        Direction.Down  -> 0
      ),
      "cell-2-5"  -> List(
        Direction.Right -> 0,
        Direction.Down  -> 0
      ),
      "cell-2-9"  -> List(
        Direction.Right -> 1,
        Direction.Down  -> 0
      ),
      "cell-3-1"  -> List(
        Direction.Right -> 0,
        Direction.Down  -> 0
      ),
      "cell-3-5"  -> List(
        Direction.Right -> 1,
        Direction.Down  -> 0
      ),
      "cell-3-9"  -> List(
        Direction.Right -> 0,
        Direction.Down  -> 0
      ),
      "cell-4-8"  -> List(
        Direction.Right -> 1,
        Direction.Down  -> 0
      ),
      "cell-4-10" -> List(
        Direction.Right -> 0,
        Direction.Down  -> 0
      )
    )

    val GridSelector = "#fb≡section-1-control≡grid-1-control"

    val expectedASCII =
      """
        |Aaaa   BCccc|
        |  DdEeebFff |
        |GgddHh bIiii|
        |    hh J Kkk|
      """.stripMargin.trim.replaceAllLiterally("|", "") // use `|` on the right as editors remove spaces

    it(s"must must analyze as expected") {
      assert(
        expectedASCII ===
          Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles($(GridSelector)(0), simplify = false))._1)
    }

    // NOTE: The logic which allows expanding cells can be improved. Right now (2017-10-03), it won't
    // allow at all expanding into cells which have controls, or expand into cells spanning in some
    // ways.
    for {
      (id, dirValue)     <- expected
      (direction, space) <- dirValue
    } locally {
      it(s"must pass moving `$id` ${direction.entryName.toLowerCase}") {
        // TODO
        //assert(space == Grid.spaceToExtendCell($(s"#$id")(0), direction))
      }
    }
  }

}