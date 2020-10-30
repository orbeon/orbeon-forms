package org.orbeon.oxf.xforms.upload

import org.orbeon.datatypes.MaximumSize
import org.orbeon.datatypes.MaximumSize.{LimitedSize, UnlimitedSize}


// Separate checking logic for sanity and testing
trait UploadCheckerLogic {

  def findAttachmentMaxSizeValidationMipFor(controlEffectiveId: String): Option[String]

  def currentUploadSizeAggregate     : Option[Long]
  def uploadMaxSizeProperty          : MaximumSize
  def uploadMaxSizeAggregateProperty : MaximumSize

  def uploadMaxSizeForControl(controlEffectiveId: String): MaximumSize = {

    def maximumSizeForControlOrDefault =
      findAttachmentMaxSizeValidationMipFor(controlEffectiveId) flatMap
        MaximumSize.unapply                                     getOrElse
        uploadMaxSizeProperty

    uploadMaxSizeAggregateProperty match {
      case UnlimitedSize =>
        // Aggregate size is not a factor so just use what we got for the control
        maximumSizeForControlOrDefault
      case LimitedSize(maximumAggregateSize) =>
        // Aggregate size is a factor
        currentUploadSizeAggregate match {
          case Some(currentAggregateSize) =>

            val remainingByAggregation = (maximumAggregateSize - currentAggregateSize) max 0L

            if (remainingByAggregation == 0) {
              LimitedSize(0)
            } else {
              maximumSizeForControlOrDefault match {
                case UnlimitedSize                   => LimitedSize(remainingByAggregation)
                case LimitedSize(remainingByControl) => LimitedSize(remainingByControl min remainingByAggregation)
              }
            }
          case None =>
            throw new IllegalArgumentException(s"missing `upload.max-size-aggregate-expression` property")
        }
    }
  }
}