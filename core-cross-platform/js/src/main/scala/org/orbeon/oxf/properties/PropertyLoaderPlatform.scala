package org.orbeon.oxf.properties

import cats.data.NonEmptyList
import org.orbeon.oxf.externalcontext.ExternalContext.Request


// This is unused on the JS platform for now
trait PropertyLoaderPlatform extends PropertyLoaderTrait {
  def initialize(): Unit = ()
  def getPropertyStoreImpl(requestOpt: Option[Request]): PropertyStore = _combinedPropertyStore

  private var _serverPropertyStore  : PropertyStore = _
  private var _combinedPropertyStore: PropertyStore = _

  def setServerPropertyStore(propertyStore: PropertyStore): Unit =
    _serverPropertyStore = propertyStore

  def setClientPropertyStore(propertyStore: PropertyStore): Unit =
    _combinedPropertyStore =
      CombinedPropertyStore.combine(NonEmptyList(_serverPropertyStore, List(propertyStore)).map(Option.apply))
        .getOrElse(_serverPropertyStore)

}
