package org.orbeon.oxf.http

sealed trait PathType
object PathType {
  case object Page    extends PathType
  case object Service extends PathType
}
