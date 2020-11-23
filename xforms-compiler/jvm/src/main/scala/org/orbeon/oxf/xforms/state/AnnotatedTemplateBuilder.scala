package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.util.Base64
import org.orbeon.oxf.xforms.state.XFormsCommonBinaryFormats._
import org.orbeon.oxf.xml.SAXStore
import sbinary.Operations.{fromByteArray, toByteArray}

object AnnotatedTemplateBuilder {

  // Restore based on a Base64-encoded string
  // Used by `XFormsContainingDocumentBuilder`
  def apply(base64: String): AnnotatedTemplate =
    AnnotatedTemplate(fromByteArray[SAXStore](Base64.decode(base64)))

  // Used to serialize into static state document
  // used by `XFormsExtractor`
  def asBase64(annotatedTemplate: AnnotatedTemplate): String = {
    val asByteArray = toByteArray(annotatedTemplate.saxStore)
    Base64.encode(asByteArray, useLineBreaks = false)
  }
}
