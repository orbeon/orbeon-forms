/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.scalatest.funspec.AnyFunSpec


class FormRunnerImportTest extends AnyFunSpec {

  describe("Excel storage format") {

    val Expected = List(
      ("1",         "date",     "1900-01-01"),
      ("44089",     "date",     "2020-09-15"),
      ("44089.625", "datetime", "2020-09-15T15:00:00"),
      ("0",         "time",     "00:00:00"),
      ("0.5",       "time",     "12:00:00"),
      ("0.625",     "time",     "15:00:00"),
    )

    for ((storage, formatTypeString, iso) <- Expected)
      it(s"must convert from `$storage` to `$iso`") {
        assert(iso == FormRunnerImport.convertDateTime(storage, formatTypeString, use1904windowing = false))
      }
  }
}
