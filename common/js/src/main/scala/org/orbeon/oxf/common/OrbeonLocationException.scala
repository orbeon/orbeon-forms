package org.orbeon.oxf.common

import org.orbeon.datatypes.LocationData


object OrbeonLocationException extends OrbeonLocationExceptionTrait {

  def getAllLocationData(throwable: Throwable): List[LocationData] = ???

  def getRootLocationData(throwable: Throwable): Option[LocationData] = ???
}
