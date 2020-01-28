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
package org.orbeon.oxf.client.fr

import org.junit.Test
import org.orbeon.oxf.client.FormRunnerOps
import org.scalatestplus.junit.AssertionsForJUnit

trait WPaint extends AssertionsForJUnit with FormRunnerOps {

  @Test def loadEditSaveWorkflow(): Unit = {

    for {
      _ <- loadOrbeonPage("/fr/orbeon/controls/new")
      // switch to attachments tab
      // select image
      // => image is shown (annotation == image)
      // draw on image
      // => annotation shows (annotation changes, save it)
      // save form
      // => check URL is now edit/id
      // reload page
      // => check the annotation is the same as before
      // clear
      // check the annotation is the same as the original image
    }()
  }
}
