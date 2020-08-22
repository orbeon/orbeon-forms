/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis._
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis
import org.orbeon.oxf.xforms.event.events.KeyboardEvent
import org.orbeon.xforms.EventNames

/**
 * Handle aspects of an element that are specific to the view.
 */
trait ViewTrait extends SimpleElementAnalysis with AppearanceTrait {

  // By default, external events are keypress plus those specifically allowed by the form author
  protected def externalEventsDef = attSet(element, XXFORMS_EXTERNAL_EVENTS_QNAME) ++ EventNames.KeyboardEvents
  def externalEvents              = externalEventsDef

  // In the view, in-scope model variables are always first in scope
  override protected def getRootVariables =
    model match { case Some(model) => model.variablesMap; case None => Map.empty }
    // NOTE: we could maybe optimize this to avoid prepending model variables every time, in case the previous element is in the same model
}
