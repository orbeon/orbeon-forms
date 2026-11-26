package org.orbeon.oxf.xforms.upload

import org.orbeon.datatypes.MaximumFiles.{LimitedFiles, UnlimitedFiles}
import org.orbeon.datatypes.{MaximumFiles, MaximumSize}
import org.orbeon.datatypes.MaximumSize.LimitedSize
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunctionNames


// Separate checking logic for sanity and testing
trait UploadCheckerLogic {

  def attachmentMaxSizeValidationMipFor(controlEffectiveId: String, validationFunctionName: String): Option[String]
  def currentUploadSizeAggregateForControl(controlEffectiveId: String)                             : Option[Long]
  def currentUploadFilesForControl(controlEffectiveId: String)                                     : Option[Int]
  def currentUploadSizeAggregateForForm                                                            : Option[Long]
  def uploadMaxSizePerFileProperty                                                                 : MaximumSize
  def uploadMaxSizeAggregatePerControlProperty                                                     : MaximumSize
  def uploadMaxSizeAggregatePerFormProperty                                                        : MaximumSize
  def uploadMaxFilesPerControlProperty                                                             : MaximumFiles = MaximumFiles.UnlimitedFiles

  def uploadMaxSizePerFileForControl(controlEffectiveId: String): MaximumSize =
    attachmentMaxSizeValidationMipFor(controlEffectiveId, ValidationFunctionNames.UploadMaxSizePerFile)
      .flatMap(MaximumSize.unapply)
      .getOrElse(uploadMaxSizePerFileProperty)

  def uploadMaxSizeAggregateForControl(controlEffectiveId: String): MaximumSize =
    attachmentMaxSizeValidationMipFor(controlEffectiveId, ValidationFunctionNames.UploadMaxSizeAggregatePerControl)
      .flatMap(MaximumSize.unapply)
      .getOrElse(uploadMaxSizeAggregatePerControlProperty)

  def uploadRemainingSizeForControl(controlEffectiveId: String): MaximumSize =
    uploadMaxSizeAggregateForControl(controlEffectiveId).minus(
      currentUploadSizeAggregateForControl(controlEffectiveId).getOrElse {
        throw new IllegalArgumentException(s"Could not determine current aggregate upload size for control $controlEffectiveId")
      }
    )

  def uploadRemainingSizeForForm: MaximumSize =
    uploadMaxSizeAggregatePerFormProperty.minus(
      currentUploadSizeAggregateForForm.getOrElse {
        throw new IllegalArgumentException("Could not determine current aggregate upload size for form")
      }
    )

  def uploadMaxSizeForControl(controlEffectiveId: String): MaximumSize = {
    val maximumSizePerFile              = uploadMaxSizePerFileForControl(controlEffectiveId)
    lazy val maximumSizeAggregatePerControl = uploadRemainingSizeForControl(controlEffectiveId)
    lazy val maximumSizeAggregatePerForm    = uploadRemainingSizeForForm

    // Do not evaluate aggregate sizes unless needed
    MaximumSize.min(maximumSizePerFile #:: maximumSizeAggregatePerControl #:: maximumSizeAggregatePerForm #:: LazyList.empty)
  }

  def uploadMaxFilesForControl(controlEffectiveId: String): MaximumFiles =
    attachmentMaxSizeValidationMipFor(controlEffectiveId, ValidationFunctionNames.UploadMaxFilesPerControl)
      .flatMap(MaximumFiles.unapply)
      .getOrElse(uploadMaxFilesPerControlProperty)

  def uploadRemainingFilesForControl(controlEffectiveId: String): Option[Int] =
    uploadMaxFilesForControl(controlEffectiveId) match {
      case LimitedFiles(max) => Some(math.max(0, max - currentUploadFilesForControl(controlEffectiveId).getOrElse(0)))
      case UnlimitedFiles    => None
    }
}

object UploadCheckerLogic {
  def checkSizeLimitExceeded(maxSize: MaximumSize, currentSize: Long): Option[Long] =
    maxSize match {
      case LimitedSize(maxSize) if currentSize > maxSize => Some(maxSize)
      case _                                             => None
    }
}