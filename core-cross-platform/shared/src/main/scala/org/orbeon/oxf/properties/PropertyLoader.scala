package org.orbeon.oxf.properties

import org.log4s.Logger
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.externalcontext.SafeRequestContext
import org.orbeon.oxf.util.LoggerFactory

import scala.util.chaining.*


trait PropertyLoaderTrait {

  private val RequestPropertiesAttributeName = "orbeon.request.property-store"

  val logger: Logger = LoggerFactory.createLogger("org.orbeon.properties")

  def initialize(): Unit

  protected def getPropertyStoreImpl(requestOpt: Option[Request]): PropertyStore

  // This method caches the property store in the request attributes map, if a request is provided. This avoids
  // excessive accesses which could be expensive when multiple `PropertyStore`s must be combined.
  def getPropertyStore(requestOpt: Option[Request]): PropertyStore =
    requestOpt match {
      case Some(request) =>
        Option(request.getAttributesMap.get(RequestPropertiesAttributeName)).map(_.asInstanceOf[PropertyStore]) match {
          case Some(requestPropertyStore) =>
            requestPropertyStore
          case None =>
            getPropertyStoreImpl(requestOpt)
              .tap(request.getAttributesMap.put(RequestPropertiesAttributeName, _))
        }
      case None =>
        getPropertyStoreImpl(requestOpt)
    }

  def fromSafeRequestContext(safeRequestCtx: SafeRequestContext): PropertyStore =
    safeRequestCtx.attributes.get(RequestPropertiesAttributeName) match {
      case Some(requestPropertyStore) =>
        requestPropertyStore.asInstanceOf[PropertyStore]
      case None =>
        //xxx
        throw new IllegalStateException("`PropertyStore` not found in `SafeRequestContext` attributes.")
//        logger.warn("`PropertyStore` not found in `SafeRequestContext` attributes. Creating a new `PropertyStore` without request information.")
//        getPropertyStoreImpl(requestOpt = None)
    }
}

object PropertyLoader extends PropertyLoaderPlatform with PropertyLoaderTrait