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
package org.orbeon.oxf.fr

import org.orbeon.oxf.xforms.XFormsControls
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.upload.UploadSupport
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.NodeInfoOps


object FormRunnerUploadSupport extends UploadSupport {

  def currentUploadSizeAggregateForControl(controls: XFormsControls, controlEffectiveId: String): Option[Long] = {
    // Return None if we couldn't find an XFormsUploadControl for the given control effective ID...
    val uploadControlOpt = getUploadControlOpt(controls, controlEffectiveId)

    // ...or if we couldn't compute the size of the files currently attached to the corresponding bound node
    uploadControlOpt.flatMap(currentUploadSize)
  }

  def currentUploadSizeAggregateForForm(controls: XFormsControls): Option[Long] = {
    val uploadControlsUploadSizes = controls.getCurrentControlTree.getUploadControls.map(currentUploadSize)

    if (uploadControlsUploadSizes.exists(_.isEmpty)) {
      // Return None if we can't find bound nodes for any of the upload controls
      None
    } else {
      Some(uploadControlsUploadSizes.flatten.sum)
    }
  }

  def currentUploadFilesForControl(controls: XFormsControls, controlEffectiveId: String): Option[Int] = {
    val uploadControlOpt = getUploadControlOpt(controls, controlEffectiveId)
    uploadControlOpt.flatMap { uploadControl =>
      getAttachmentBoundNodeOpt(uploadControl).map { boundNode =>
        boundNode.child("_").size
      }
    }
  }

  private def getUploadControlOpt(
    controls           : XFormsControls,
    controlEffectiveId : String
  ): Option[XFormsUploadControl] =
    controls.getCurrentControlTree
      .findControl(controlEffectiveId)
      .collect { case c: XFormsUploadControl => c }

  private def getAttachmentBoundNodeOpt(uploadControl: XFormsUploadControl): Option[NodeInfo] =
    uploadControl.container.associatedControlOpt
      .collect { case c: XFormsComponentControl => c }
      .filter(_.staticControl.element.getName == "attachment")
      .flatMap(_.boundNodeOpt)

  private def currentUploadSize(uploadControl: XFormsUploadControl): Option[Long] = {
    getAttachmentBoundNodeOpt(uploadControl).map { boundNode =>
      val singleAttachmentSizeOpt  = boundNode.attValueOpt("size")
      val multipleAttachmentsSizes = boundNode.child("_").flatMap(_.attValueOpt("size"))
      val sizes = (singleAttachmentSizeOpt.toSeq ++ multipleAttachmentsSizes).filter(_.nonEmpty).map(_.toLong)
      sizes.sum
    }
  }
}
