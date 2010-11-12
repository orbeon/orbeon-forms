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

import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis

/**
 * Handle aspects of an element that are specific to the view.
 */
trait ViewTrait extends SimpleElementAnalysis {

    // Index of the element in the view
    val index: Int = staticStateContext.index

    // In the view, in-scope model variables are always first in scope
    override protected def getRootVariables =
        scopeModel.containingModel match { case Some(model) => model.variablesMap; case None => Map.empty }
        // NOTE: we could maybe optimize this to avoid prepending model variables every time, in case the previous element is in the same model
}