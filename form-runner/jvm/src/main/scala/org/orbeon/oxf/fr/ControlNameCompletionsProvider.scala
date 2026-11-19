package org.orbeon.oxf.fr


trait ControlNameCompletionsProvider {
  def controlNameCompletionsJson(text: String, limit: Int, updates: String): String
}
