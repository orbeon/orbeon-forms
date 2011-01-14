/**
 *  Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.oxf.xforms.XFormsContainingDocument
import java.util.{Map => JMap}
import org.orbeon.oxf.xforms.event._

class XXFormsUploadDoneEvent(containingDocument: XFormsContainingDocument, targetObject: XFormsEventTarget, parameters: JMap[String, String])
    extends XFormsEvent(containingDocument, XFormsEvents.XXFORMS_UPLOAD_DONE, targetObject, true, false) {

    val file = parameters.get("file")
    val filename = parameters.get("filename")
    val mediatype = parameters.get("content-type")
    val size = parameters.get("content-length")
}
