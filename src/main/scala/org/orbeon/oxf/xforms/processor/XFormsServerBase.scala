/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor

import java.{util ⇒ ju}

import org.orbeon.oxf.xforms.XFormsConstants.XXFORMS_NAMESPACE_URI
import org.orbeon.oxf.xforms.{ScriptInvocation, XFormsContainingDocument, XFormsUtils}
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport._

import scala.collection.JavaConverters._

object XFormsServerBase {

  def outputScriptInvocations(
    doc               : XFormsContainingDocument,
    scriptInvocations : ju.List[ScriptInvocation])(implicit
    receiver          : XMLReceiver
  ): Unit = {

    for (script ← scriptInvocations.asScala) {

      withElement(
        "script",
        prefix = "xxf",
        uri    = XXFORMS_NAMESPACE_URI,
        atts   = List(
          "name"        → script.script.shared.clientName,
          "target-id"   → XFormsUtils.namespaceId(doc, script.targetEffectiveId),
          "observer-id" → XFormsUtils.namespaceId(doc, script.observerEffectiveId)
        )
      ) {

        for (value ← script.paramValues) {
          element(
            "param",
            prefix = "xxf",
            uri    = XXFORMS_NAMESPACE_URI,
            atts   = Nil,
            text   = value
          )
        }
      }
    }
  }
}
