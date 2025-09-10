package org.orbeon.fr

import org.orbeon.web.DomSupport.DomElemOps
import org.scalajs.dom.html

object FormRunnerUtils {

  def isViewMode(formElement: html.Element): Boolean =
    formElement.closestOpt(".fr-mode-view").nonEmpty

}
