/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.analysis.{AVTLangRef, LiteralLangRef, ElementAnalysis}
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl
import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue
import org.orbeon.oxf.xforms.XFormsContainingDocument

class XXFormsLang extends XFormsFunction with FunctionSupport {

    import XXFormsLang._

    override def evaluateItem(xpathContext: XPathContext): StringValue = {

        implicit val ctx = xpathContext

        val elementAnalysis = stringArgumentOpt(0) match {
            case Some(staticId) ⇒ elementAnalysisForStaticId(staticId)
            case None           ⇒ elementAnalysisForSource
        }

        elementAnalysis flatMap (resolveXMLangHandleAVTs(getContainingDocument, _)) map StringValue.makeStringValue orNull
    }
}

object XXFormsLang {

    def resolveXMLangHandleAVTs(containingDocument: XFormsContainingDocument, element: ElementAnalysis): Option[String] =
        element.lang match {
            case Some(LiteralLangRef(value)) ⇒
                Some(value)
            case Some(AVTLangRef(att)) ⇒

                // TODO: resolve concrete ancestor XXFormsAttributeControl instead of just using static id

                val attributeControl = containingDocument.getControls.getObjectByEffectiveId(att.staticId).asInstanceOf[XXFormsAttributeControl]
                Some(attributeControl.getExternalValue())
            case None ⇒
                None
        }
}
