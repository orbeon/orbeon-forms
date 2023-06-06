/**
 *  Copyright (C) 2011 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, SecureUtils}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.util.Logging._

import java.io


object XFormsStateStore {

  import Private._

  type CacheValueMappingType = (String, Long)

  def storeDocumentState(
    document       : XFormsContainingDocument,
    isInitialState : Boolean
  ): Unit = {

    assert(document.staticState.isServerStateHandling)

    implicit val ec: ExternalContext = CoreCrossPlatformSupport.externalContext

    if (! isInitialState)
      LifecycleLogger.eventAssumingRequest("xforms", "save state", List("uuid" -> document.uuid))

    val documentUUID = document.uuid

    withDebug("storing document state", List(
      "document UUID"             -> documentUUID,
      "store size before storing" -> getCurrentSize.map(_.toString).getOrElse("unknown"),
      "isInitialState"            -> isInitialState.toString,
      "replication"               -> XFormsGlobalProperties.isReplication.toString
    )) {
      val staticStateDigest = document.staticState.digest
      val dynamicStateKey   = createDynamicStateKey(documentUUID, isInitialState)
      val sequence          = document.sequence

      // Mapping (UUID -> static state key : dynamic state key
      Caches.stateCache.put(documentUUID, (staticStateDigest + ":" + dynamicStateKey, sequence): CacheValueMappingType)

      // Static and dynamic states
      Caches.stateCache.putIfAbsent(staticStateDigest, document.staticState.encodedState)
      Caches.stateCache.put(dynamicStateKey,   DynamicState(document))
    }
  }

  def findSequence(documentUUID: String): Option[Long] =
    Caches.stateCache.get(documentUUID).map(_.asInstanceOf[CacheValueMappingType]).map(_._2)

  def findState(
    documentUUID   : String,
    isInitialState : Boolean
  ): Option[XFormsState] = {

    implicit val ec: ExternalContext = CoreCrossPlatformSupport.externalContext

    LifecycleLogger.withEventAssumingRequest(
      "xforms",
      "restore state",
      List(
        "uuid" -> documentUUID,
        "backOrReload" -> isInitialState.toString
      )
    ) {

      debug(s"store size before finding: ${getCurrentSize.map(_.toString).getOrElse("unknown")} entries.")

      Caches.stateCache.get(documentUUID).map(_.asInstanceOf[CacheValueMappingType]) match {
        case Some((keyString: String, _: Long)) =>

          // Found the keys, split into parts
          val parts = keyString split ':'

          assert(parts.size == 2)
          assert(parts(0).length >= SecureUtils.HexShortLength) // static state key is an hex hash

          // If isInitialState == true, force finding the initial state. Otherwise, use current state stored in mapping.
          val dynamicStateKey = if (isInitialState) createDynamicStateKey(documentUUID, isInitialState = true) else parts(1)

          // Gather values from cache for both keys and return state only if both are non-null
          Stream(parts(0), dynamicStateKey) flatMap Caches.stateCache.get filter (_ != null) match {
            case Stream(staticState: String, dynamicState: DynamicState) =>
              Some(XFormsState(Some(parts(0)), Some(staticState), Some(dynamicState)))
            case _ =>
              None
          }

        case _ =>
          None
      }
    }
  }

  // NOTE: Don't remove the static state as it might be in use by other form sessions.
  def removeDynamicState(documentUUID: String): Unit = {
    Caches.stateCache.remove(documentUUID)
    Caches.stateCache.remove(createDynamicStateKey(documentUUID, isInitialState = true))
    Caches.stateCache.remove(createDynamicStateKey(documentUUID, isInitialState = false))
  }

  def getMaxSize     : Option[Long] = Caches.stateCache.getMaxEntriesLocalHeap
  def getCurrentSize : Option[Long] = Caches.stateCache.getLocalHeapSize

  private object Private {

    implicit val logger: IndentedLogger = XFormsStateManager.Logger

    def createDynamicStateKey(documentUUID: String, isInitialState: Boolean) =
      documentUUID + (if (isInitialState) "-I" else "-C") // key is different for initial vs. subsequent state
  }
}