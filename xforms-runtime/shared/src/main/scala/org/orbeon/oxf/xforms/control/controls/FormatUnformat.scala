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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.xforms.control.*
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.scaxon.Implicits.stringToStringValue


trait WithFormatTrait extends XFormsValueControl {

  private def format: Option[String] = staticControlOpt.flatMap(_.format)

  // Tricky: mark the external value as dirty if there is a format, as the client will expect an up to date
  // formatted value
  final protected def markExternalValueDirtyIfHasFormat(): Unit =
    format.foreach { _ =>
      markExternalValueDirty()
      containingDocument.controls.markDirtySinceLastRequest(bindingsAffected = false)
    }

  // TODO: format must take place between instance and internal value instead
  final protected def maybeEvaluateWithFormat(collector: ErrorEventCollector): Option[String] =
    format.flatMap(valueWithSpecifiedFormat(_, collector))

  final protected def maybeEvaluateWithFormatOrDefaultFormat(collector: ErrorEventCollector): Option[String] =
    maybeEvaluateWithFormat(collector)
      .orElse(valueWithDefaultFormat(collector))
}

trait WithUnformatTrait extends XFormsValueControl {

  private def unformat: Option[String] = staticControlOpt.flatMap(_.unformat)

  final protected def unformatTransform(v: String, collector: ErrorEventCollector): String = unformat match {
    case Some(expr) => evaluateAsString(expr, List(stringToStringValue(v)), 1, collector, "translating external value") getOrElse ""
    case None       => v
  }
}