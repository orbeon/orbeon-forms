package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.util.Base64
import org.orbeon.oxf.xforms.state.XFormsProtocols._
import org.orbeon.oxf.xml.SAXStore
import sbinary.Operations.{fromByteArray, toByteArray}

object AnnotatedTemplateBuilder {

  // Restore based on a Base64-encoded string
  def apply(base64: String): AnnotatedTemplate =
    AnnotatedTemplate(fromByteArray[SAXStore](Base64.decode(base64)))

  // Used to serialize into static state document
  def asBase64(annotatedTemplate: AnnotatedTemplate): String = {
    val asByteArray = toByteArray(annotatedTemplate.saxStore)
    Base64.encode(asByteArray, false)
  }
}
