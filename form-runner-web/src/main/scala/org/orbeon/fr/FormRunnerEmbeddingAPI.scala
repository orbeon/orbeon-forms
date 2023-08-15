package org.orbeon.fr

import org.orbeon.xforms

import scala.scalajs.js


abstract class FormRunnerEmbeddingAPI extends js.Object

// Form Runner-specific facade as we don't want to expose internal `xforms.Form` members
class FormRunnerForm(private val form: xforms.Form) extends js.Object {

  def addCallback(name: String, fn: js.Function): Unit =
    form.addCallback(name, fn)

  def removeCallback(name: String, fn: js.Function): Unit =
    form.removeCallback(name, fn)
}
