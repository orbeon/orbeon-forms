package org.orbeon.oxf.util

import org.orbeon.datatypes.{Mediatype, MediatypeRange}

sealed trait Reason
object Reason {
  case class SizeReason     (permitted: Long,                actual: Long)              extends Reason
  case class MediatypeReason(permitted: Set[MediatypeRange], actual: Option[Mediatype]) extends Reason
  case class FileScanReason (fieldName: String, message: Option[String])                extends Reason
}

sealed trait UploadState[+FileItemType] { def name: String }
object UploadState {
  case object Started                                         extends UploadState[Nothing]      { val name = "started" }
  case class  Completed[FileItemType](fileItem: FileItemType) extends UploadState[FileItemType] { val name = "completed" }
  case class  Interrupted(reason: Option[Reason])             extends UploadState[Nothing]      { val name = "interrupted" }
}

// NOTE: Fields don't need to be @volatile as they are accessed via the session, which provides synchronized access.
case class UploadProgress[FileItemType](
  fieldName        : String,
  expectedSize     : Option[Long],
  var receivedSize : Long        = 0L,
  var state        : UploadState[FileItemType] = UploadState.Started
)
