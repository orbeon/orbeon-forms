package org.orbeon.polyfills

import org.scalajs.dom.html


object HTMLPolyfills {

  implicit class HTMLElementOps(private val element: html.Element) extends AnyVal {

    // TODO: not needed anymore, called should just call `.closest()`
    final def closest(selector: String): Option[html.Element] = {
      if (element.matches(selector))
        Some(element)
      else if (element.parentElement == null)
        None
      else
        element.parentElement.closest(selector) match {
          case e: html.Element => Some(e)
          case _               => None
        }
    }
  }
}

