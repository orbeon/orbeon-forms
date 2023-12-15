package org.orbeon.oxf.util

import org.orbeon.datatypes.{Mediatype, MediatypeRange}

sealed trait FileRejectionReason
object FileRejectionReason {
  case object EmptyFile                                                                                        extends FileRejectionReason
  case class  SizeTooLarge       (permitted: Long, actual: Long)                                               extends FileRejectionReason
  case class  DisallowedMediatype(filename: String, permitted: Set[MediatypeRange], actual: Option[Mediatype]) extends FileRejectionReason
  case class  FailedFileScan     (fieldName: String, message: Option[String])                                  extends FileRejectionReason
}

sealed trait UploadState[+FileItemType] { def name: String }
object UploadState {
  case object Started                                          extends UploadState[Nothing]      { val name = "started" }
  case class  Completed[FileItemType](fileItem: FileItemType)  extends UploadState[FileItemType] { val name = "completed" }
  case class  Interrupted(reason: Option[FileRejectionReason]) extends UploadState[Nothing]      { val name = "interrupted" }
}

// NOTE: Fields don't need to be @volatile as they are accessed via the session, which provides synchronized access.
case class UploadProgress[FileItemType](
  fieldName        : String,
  expectedSize     : Option[Long],
  var receivedSize : Long                      = 0L,
  var state        : UploadState[FileItemType] = UploadState.Started
)
