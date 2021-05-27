/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.xforms

import org.scalatest.funspec.AnyFunSpec


class XFormsIdTest extends AnyFunSpec {

  describe("extracting id parts") {

    val expected = List(
      "input-control" ->
        XFormsId("input-control", Nil, Nil),
      "fr-view-component≡selection-controls-section≡xf-1156≡radio-buttons-control" ->
        XFormsId("radio-buttons-control", List("fr-view-component", "selection-controls-section", "xf-1156"), Nil),
      "fr-view-component≡selection-controls-section≡xf-1156≡radio-buttons-control⊙42-7-2" ->
        XFormsId("radio-buttons-control", List("fr-view-component", "selection-controls-section", "xf-1156"), List(42, 7, 2))
    )

    for ((s, xformsId) <- expected)
      it(s"must pass for `$s`") {
        assert(xformsId === XFormsId.fromEffectiveId(s))
        assert(s === xformsId.toEffectiveId)
      }
  }
}
