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
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.common.OXFException
import java.util.{Collections => JCollections}
import ControlLocalSupport._
import org.orbeon.oxf.xforms.state.ControlState
import scala.jdk.CollectionConverters._

trait ControlLocalSupport {

  self: XFormsControl =>

  // Scala 2.11: Simply `private` worked with 2.10. Unclear whether this is a feature or a bug.
  private[control] var initialLocal: XFormsControlLocal = null
  private[control] var currentLocal: XFormsControlLocal = null

  /**
   * Serialize this control's information which cannot be reconstructed from instances. The result is empty if no
   * serialization is needed, or a map of name/value pairs otherwise.
   */
  def serializeLocal = JCollections.emptyMap[String, String]

  final def controlState = {
    val keyValues = serializeLocal
    val isVisited = visited

    if (keyValues.isEmpty && ! isVisited)
      None
    else if (keyValues.isEmpty && isVisited)
      Some(ControlState(effectiveId, isVisited, Map.empty))
    else
      Some(ControlState(effectiveId, isVisited, keyValues.asScala.toMap))
  }

  final def updateLocalCopy(copy: XFormsControl): Unit = {
    if (this.currentLocal != null) {
      // There is some local data
      if (this.currentLocal ne this.initialLocal) {
        // The trees don't keep wasteful references
        copy.currentLocal = copy.initialLocal
        this.initialLocal = this.currentLocal
      } else {
        // The new tree must have its own copy
        // NOTE: We could implement a copy-on-write flag here
        copy.initialLocal = this.currentLocal.clone.asInstanceOf[XFormsControlLocal]
        copy.currentLocal = copy.initialLocal
      }
    }
  }

  final def setLocal(local: XFormsControlLocal): Unit = {
    this.initialLocal = local
    this.currentLocal = local
  }

  final def getLocalForUpdate = {
    if (containingDocument.isHandleDifferences) {
      // Happening during a client request where we need to handle diffs
      val controls = containingDocument.controls
      if (controls.getInitialControlTree ne controls.getCurrentControlTree) {
        if (currentLocal ne initialLocal)
          throw new OXFException("currentLocal != initialLocal")
      } else if (initialLocal eq currentLocal)
        currentLocal = initialLocal.clone.asInstanceOf[XFormsControlLocal]
    } else {
      // Happening during initialization
      // NOP: Don't modify currentLocal
    }

    currentLocal
  }

  final def getInitialLocal = initialLocal
  final def getCurrentLocal = currentLocal

  final def resetLocal() = initialLocal = currentLocal
}

object ControlLocalSupport {
  class XFormsControlLocal extends Cloneable {
    override def clone: AnyRef = super.clone
  }
}