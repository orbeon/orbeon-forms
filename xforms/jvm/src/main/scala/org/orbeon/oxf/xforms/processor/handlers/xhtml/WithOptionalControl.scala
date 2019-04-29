/**
  * Copyright (C) 2019 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl


trait WithOptionalControl extends XFormsBaseHandlerXHTML {

  final def staticControlOpt: Option[ElementAnalysis] =
    getXFormsHandlerContext.getPartAnalysis.findControlAnalysis(getPrefixedId)

  final val currentControlOpt: Option[XFormsControl] =
    Option(getContainingDocument.getControlByEffectiveId(getEffectiveId)) ensuring (! _.contains(null))
}
