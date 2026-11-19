package org.orbeon.oxf.fb

import org.orbeon.oxf.fr.ControlNameCompletionsProvider


class FormBuilderControlNameCompletionsProvider extends ControlNameCompletionsProvider {
  override def controlNameCompletionsJson(text: String, limit: Int, updates: String): String =
    FormBuilderXPathApi.controlNameCompletionsJson(text, limit, updates)
}
