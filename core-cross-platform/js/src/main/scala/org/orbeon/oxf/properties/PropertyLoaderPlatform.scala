package org.orbeon.oxf.properties

import org.orbeon.oxf.externalcontext.ExternalContext.Request


// This is unused on the JS platform for now
trait PropertyLoaderPlatform extends PropertyLoaderTrait {
  def initialize(): Unit = ()
  def getPropertyStoreImpl(requestOpt: Option[Request]): PropertyStore = ???
}
