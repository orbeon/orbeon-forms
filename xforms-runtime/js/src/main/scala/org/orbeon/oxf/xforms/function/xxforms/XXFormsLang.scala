package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, LangRef}
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl


object XXFormsLang {
  def resolveXMLangHandleAVTs(containingDocument: XFormsContainingDocument, element: ElementAnalysis): Option[String] =
    element.getLangUpdateIfUndefined match {
      case LangRef.Literal(value) =>
        Some(value)
      case LangRef.AVT(att) =>
        // TODO: resolve concrete ancestor XXFormsAttributeControl instead of just using static id
        val attributeControl = containingDocument.getControlByEffectiveId(att.staticId).asInstanceOf[XXFormsAttributeControl]
        Option(attributeControl.getExternalValue())
      case _ =>
        None
    }
}
