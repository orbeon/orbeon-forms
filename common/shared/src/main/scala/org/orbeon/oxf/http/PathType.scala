package org.orbeon.oxf.http

sealed trait PathType
object PathType {
  case object Page    extends PathType
  case object Service extends PathType

  def fromString(s: String): PathType = s match {
    case "page"    => Page
    case "service" => Service
    case _         => throw new IllegalArgumentException(s"Unknown `PathType`: `$s`")
  }
}
