/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.client

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

trait ClientFormRunnerSummaryTest extends AssertionsForJUnit with FormRunnerOps {

  @Test def navigateSummary(): Unit = {
    Summary.navigate("orbeon", "bookshelf")

    assert("1 to 10 of 12" === Summary.paging)
    Summary.nextPage()
    assert("11 to 12 of 12" === Summary.paging)
    Summary.firstPage()
    assert("1 to 10 of 12" === Summary.paging)
  }
}
