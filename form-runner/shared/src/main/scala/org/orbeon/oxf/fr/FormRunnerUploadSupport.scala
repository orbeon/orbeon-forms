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

import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.XFormsControls
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.upload.UploadSupport
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath
import org.orbeon.scaxon.SimplePath.NodeInfoOps


object FormRunnerUploadSupport extends UploadSupport {

  import Private.*

  def currentUploadSizeAggregateForControl(controls: XFormsControls, controlEffectiveId: String): Option[Long] =
    getUploadControlOpt(controls, controlEffectiveId).flatMap(currentUploadSize)

  // Return `None` if we can't find the associated attachment XBL control or bound node for any of the upload controls
  def currentUploadSizeAggregateForForm(controls: XFormsControls): Option[Long] = {

    val uploadControlsUploadSizes =
      controls.getCurrentControlTree
        .getRelevantUploadControls // https://github.com/orbeon/orbeon-forms/issues/6762
        .map(currentUploadSize)

    if (uploadControlsUploadSizes.exists(_.isEmpty))
      None
    else
      Some(uploadControlsUploadSizes.flatten.sum)
  }

  def currentUploadFilesForControl(controls: XFormsControls, controlEffectiveId: String): Option[Int] =
    getUploadControlOpt(controls, controlEffectiveId)
      .flatMap(getAttachmentBoundNodeOpt)
      .map(multipleAttachmentHolders(_).size)

  def attachmentSizeForBoundNode(boundNode: NodeInfo): Long =
    dataAttachmentHolders(boundNode)
      .flatMap(_.attValueOpt("size"))
      .flatMap(_.trimAllToOpt)
      .flatMap(_.toLongOption)
      .filter(_ >= 0L)
      .sum

  def attachmentCountForBoundNode(boundNode: NodeInfo, isMultiple: Boolean): Int =
    if (isMultiple)
      multipleAttachmentHolders(boundNode).size
    else if (isDataAttachmentHolder(boundNode))
      1
    else
      0

  def isFormDefinitionAttachmentUri(uri: String): Boolean = {
    val pathOnly = PathUtils.splitQuery(uri.trimAllToEmpty)._1
    FormRunnerPersistence.FormPath.matches(pathOnly)
  }

  private object Private {

    def getUploadControlOpt(
      controls           : XFormsControls,
      controlEffectiveId : String
    ): Option[XFormsUploadControl] =
      controls.getCurrentControlTree
        .findControl(controlEffectiveId)
        .collect { case c: XFormsUploadControl => c }

    def getAttachmentBoundNodeOpt(uploadControl: XFormsUploadControl): Option[NodeInfo] =
      uploadControl.container.associatedControlOpt
        .collect { case c: XFormsComponentControl => c }
        .filter(_.staticControl.localName == "attachment")
        .flatMap(_.boundNodeOpt)

    def isDataAttachmentHolder(holder: NodeInfo): Boolean = {
      val uri = holder.getStringValue.trimAllToEmpty
      uri.nonEmpty && ! isFormDefinitionAttachmentUri(uri)
    }

    def multipleAttachmentHolders(boundNode: NodeInfo): Seq[NodeInfo] =
      boundNode
        .child(SimplePath.*)
        .filter(n => n.localname == "_" && isDataAttachmentHolder(n))

    def dataAttachmentHolders(boundNode: NodeInfo): Seq[NodeInfo] = {
      val multiple = multipleAttachmentHolders(boundNode)
      if (multiple.nonEmpty)
        multiple
      else if (isDataAttachmentHolder(boundNode))
        List(boundNode)
      else
        Nil
    }

    // Return `None` if we can't find the associated attachment XBL control or bound node
    def currentUploadSize(uploadControl: XFormsUploadControl): Option[Long] =
      getAttachmentBoundNodeOpt(uploadControl).map(attachmentSizeForBoundNode)
  }
}
