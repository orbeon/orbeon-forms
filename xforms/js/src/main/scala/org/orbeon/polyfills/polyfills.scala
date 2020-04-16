package org.orbeon.polyfills

import org.scalajs.dom.html

import scala.annotation.tailrec

object HTMLPolyfills {

  implicit class HTMLElementOps(private val element: html.Element) extends AnyVal {

    @tailrec final def closest(selector: String): Option[html.Element] = {
      if (element.matches(selector))
        Some(element)
      else if (element.parentElement == null)
        None
      else
        element.parentElement.closest(selector)
    }
  }
}

