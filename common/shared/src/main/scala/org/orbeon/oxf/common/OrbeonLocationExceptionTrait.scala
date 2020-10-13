package org.orbeon.oxf.common

import org.orbeon.datatypes.LocationData


trait OrbeonLocationExceptionTrait {

  def getAllLocationData (throwable: Throwable): List[LocationData]
  def getRootLocationData(throwable: Throwable): Option[LocationData]

  final def wrapException(throwable: Throwable, locationData: LocationData): ValidationException =
    throwable match {
      case t: ValidationException => Option(locationData) foreach t.addLocationData; t
      case t                      => new ValidationException(t, locationData)
    }
}
