/**
 * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.upload

import org.orbeon.oxf.xforms.XFormsControls


trait UploadSupport {
  def currentUploadSizeAggregateForControl(controls: XFormsControls, controlEffectiveId: String): Option[Long]
  def currentUploadSizeAggregateForForm   (controls: XFormsControls                            ): Option[Long]
  def currentUploadFilesForControl        (controls: XFormsControls, controlEffectiveId: String): Option[Int]
}

object UploadSupport {
  def noUploadSupport: UploadSupport = new UploadSupport {
    def currentUploadSizeAggregateForControl(controls: XFormsControls, controlEffectiveId: String): Option[Long] = None
    def currentUploadSizeAggregateForForm   (controls: XFormsControls                            ): Option[Long] = None
    def currentUploadFilesForControl        (controls: XFormsControls, controlEffectiveId: String): Option[Int]  = None
  }
}