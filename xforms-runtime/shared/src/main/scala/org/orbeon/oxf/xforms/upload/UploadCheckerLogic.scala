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

  def uploadMaxSizeForControl(controlEffectiveId: String): MaximumSize = {

    val maximumSizePerFile =
      attachmentMaxSizeValidationMipFor(controlEffectiveId, ValidationFunctionNames.UploadMaxSizePerFile)
        .flatMap(MaximumSize.unapply)
        .getOrElse(uploadMaxSizePerFileProperty)

    lazy val maximumSizeAggregatePerControl: MaximumSize =
      attachmentMaxSizeValidationMipFor(controlEffectiveId, ValidationFunctionNames.UploadMaxSizeAggregatePerControl)
        .flatMap(MaximumSize.unapply)
        .getOrElse(uploadMaxSizeAggregatePerControlProperty)
        .minus(
          currentUploadSizeAggregateForControl(controlEffectiveId).getOrElse {
            throw new IllegalArgumentException(s"Could not determine current aggregate upload size for control $controlEffectiveId")
          }
        )

    lazy val maximumSizeAggregatePerForm: MaximumSize =
      uploadMaxSizeAggregatePerFormProperty.minus(
        currentUploadSizeAggregateForForm.getOrElse {
          throw new IllegalArgumentException("Could not determine current aggregate upload size for form")
        }
      )

    // Do not evaluate aggregate sizes unless needed
    MaximumSize.min(maximumSizePerFile #:: maximumSizeAggregatePerControl #:: maximumSizeAggregatePerForm #:: LazyList.empty)
  }
}

object UploadCheckerLogic {
  def checkSizeLimitExceeded(maxSize: MaximumSize, currentSize: Long): Option[Long] =
    maxSize match {
      case LimitedSize(maxSize) if currentSize > maxSize => Some(maxSize)
      case _                                             => None
    }
}