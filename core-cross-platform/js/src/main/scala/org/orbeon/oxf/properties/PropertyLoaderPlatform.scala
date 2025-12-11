package org.orbeon.oxf.properties

import cats.data.NonEmptyList
import org.orbeon.oxf.externalcontext.ExternalContext.Request


// This is unused on the JS platform for now
trait PropertyLoaderPlatform extends PropertyLoaderTrait {
  def initialize(): Unit = ()
  def getPropertyStoreImpl(requestOpt: Option[Request]): PropertyStore =
    _combinedPropertyStore

  private var _serverPropertyStore  : PropertyStore = _
  private var _clientPropertyStore  : PropertyStore = _
  private var _combinedPropertyStore: PropertyStore = _

  def setServerPropertyStore(propertyStore: PropertyStore): Unit = {
    _serverPropertyStore = propertyStore
    updateCombinedPropertyStore()
  }

  def setClientPropertyStore(propertyStore: PropertyStore): Unit = {
    _clientPropertyStore = propertyStore
    updateCombinedPropertyStore()
  }

  private def updateCombinedPropertyStore(): Unit =
    (_clientPropertyStore, _serverPropertyStore) match {
      case (null, null) => _combinedPropertyStore = PropertyStore.empty
      case (null, ss)   => _combinedPropertyStore = ss
      case (cs, null)   => _combinedPropertyStore = cs
      case (cs,  ss)    =>
        _combinedPropertyStore =
          CombinedPropertyStore.combine(NonEmptyList(ss, List(cs)).map(Option.apply))
            .getOrElse(throw new IllegalStateException)
    }
}
