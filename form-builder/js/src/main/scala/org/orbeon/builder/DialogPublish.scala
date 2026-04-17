/**
  * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.builder

import org.orbeon.web.DomSupport.*
import org.scalajs.dom


object DialogPublish {

  locally {
    dom.document.addEventListener("click", (event: dom.Event) =>
      event.targetT.closestOpt(".fb-publish-open-new").foreach { button =>
        button.attValueOpt("data-href").foreach { href =>
          dom.window.open(href, "_blank")
        }
      }
    )
  }
}
