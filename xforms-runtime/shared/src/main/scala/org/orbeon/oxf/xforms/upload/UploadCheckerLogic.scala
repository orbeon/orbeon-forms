package org.orbeon.oxf.xforms.upload

import org.orbeon.datatypes.MaximumSize
import org.orbeon.datatypes.MaximumSize.{LimitedSize, UnlimitedSize}
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunctionNames

import scala.collection.compat.immutable.LazyList


// Separate checking logic for sanity and testing
trait UploadCheckerLogic {

  def attachmentMaxSizeValidationMipFor(controlEffectiveId: String, validationFunctionName: String): Option[String]
  def currentUploadSizeControlAggregate(controlEffectiveId: String)                                : Option[Long]
  def currentUploadSizeFormAggregate                                                               : Option[Long]
  def uploadMaxSizeProperty                                                                        : MaximumSize
  def uploadMaxSizeFormAggregateProperty                                                           : MaximumSize

  def uploadMaxSizeForControl(controlEffectiveId: String): MaximumSize = {
    val maximumSizePerFile =
      attachmentMaxSizeValidationMipFor(controlEffectiveId, ValidationFunctionNames.UploadMaxSize)
        .flatMap(MaximumSize.unapply)
        .getOrElse(uploadMaxSizeProperty)

    lazy val maximumSizeAggregatePerControl: MaximumSize =
      attachmentMaxSizeValidationMipFor(controlEffectiveId, ValidationFunctionNames.UploadMaxSizeControlAggregate)
        .flatMap(MaximumSize.unapply)
        .getOrElse(UnlimitedSize)
        .minus(
          currentUploadSizeControlAggregate(controlEffectiveId).getOrElse {
            throw new IllegalArgumentException(s"Could not determine current control aggregate upload size")
          }
        )

    lazy val maximumSizeAggregatePerForm: MaximumSize =
      uploadMaxSizeFormAggregateProperty.minus(
        currentUploadSizeFormAggregate.getOrElse {
          throw new IllegalArgumentException(s"Could not determine current form aggregate upload size")
        }
      )

    // Do not evaluate aggregate sizes unless needed
    MaximumSize.min(maximumSizePerFile #:: maximumSizeAggregatePerControl #:: maximumSizeAggregatePerForm #:: LazyList.empty)
  }
}