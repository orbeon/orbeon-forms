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
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml.{XMLReceiver, XMLReceiverHelper}

import scala.collection.JavaConverters._
import scala.collection.{mutable ⇒ m}

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

  def diffIndexState(
    ch                     : XMLReceiverHelper,
    ns                     : String,
    initialRepeatIdToIndex : m.Map[String, Int],
    currentRepeatIdToIndex : m.Map[String, Int]
  ): Unit =
    if (currentRepeatIdToIndex.nonEmpty) {
      var found = false
      for {
        (repeatId, newIndex) ← currentRepeatIdToIndex
        oldIndex             ← initialRepeatIdToIndex.get(repeatId) // may be None if there was no iteration
        if newIndex != oldIndex
      } locally {
          if (! found) {
            ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "repeat-indexes")
            found = true
          }
          // Make sure to namespace the id
          ch.element("xxf", XXFORMS_NAMESPACE_URI, "repeat-index", Array("id", ns + repeatId, "new-index", newIndex.toString))
      }

      if (found)
        ch.endElement()
    }
}
