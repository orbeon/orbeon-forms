package org.orbeon.xforms.runtime

import org.orbeon.dom
import org.orbeon.oxf.xforms.XFormsContainingDocument

case class ErrorInfo(
  element : dom.Element,
  message : String
)

trait XFormsObject {
  def getEffectiveId     : String
  def containingDocument : XFormsContainingDocument
}
