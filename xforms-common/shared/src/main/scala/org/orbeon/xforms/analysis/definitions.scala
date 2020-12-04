package org.orbeon.xforms.analysis

import enumeratum.EnumEntry.Lowercase
import enumeratum._


sealed abstract class Phase(val name: String) extends EnumEntry with Lowercase
object Phase extends Enum[Phase] with CirceEnum[Phase] {

  def values = findValues

  object Capture  extends Phase("capture")
  object Target   extends Phase("target")
  object Bubbling extends Phase("bubbling")
}

sealed trait Propagate extends EnumEntry
object Propagate extends Enum[Propagate] with CirceEnum[Propagate] {
  def values = findValues

  case object Continue extends Propagate
  case object Stop     extends Propagate
}

sealed trait Perform extends EnumEntry
object Perform extends Enum[Perform] with CirceEnum[Perform] {

  def values = findValues

  case object Perform extends Perform
  case object Cancel  extends Perform
}