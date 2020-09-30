package org.orbeon.xforms.analysis


sealed abstract class Phase(val name: String)
object Phase {
  object Capture  extends Phase("capture")
  object Target   extends Phase("target")
  object Bubbling extends Phase("bubbling")
}

sealed trait Propagate
object Propagate {
  case object Continue extends Propagate
  case object Stop     extends Propagate
}

sealed trait Perform
object Perform {
  case object Perform extends Perform
  case object Cancel  extends Perform
}