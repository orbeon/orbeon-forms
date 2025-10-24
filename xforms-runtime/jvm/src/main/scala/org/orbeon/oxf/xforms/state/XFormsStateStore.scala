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
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, SecureUtils}
import org.orbeon.oxf.xforms.*


object XFormsStateStore {

  import Private.*

  type CacheValueMappingType = (String, Long)

  def storeDocumentState(
    document       : XFormsContainingDocument,
    isInitialState : Boolean
  ): Unit = {

    assert(document.staticState.isServerStateHandling)

    implicit val ec: ExternalContext = CoreCrossPlatformSupport.externalContext
    implicit val logger: IndentedLogger = document.getIndentedLogger("state")

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
      XFormsStores.stateStore.put(documentUUID, (staticStateDigest + ":" + dynamicStateKey, sequence): CacheValueMappingType)

      // Static and dynamic states
      XFormsStores.stateStore.putIfAbsent(staticStateDigest, document.staticState.encodedState)
      XFormsStores.stateStore.put(dynamicStateKey,   DynamicState(document))
    }
  }

  def findSequence(documentUUID: String): Option[Long] =
    XFormsStores.stateStore.get(documentUUID).map(_.asInstanceOf[CacheValueMappingType]).map(_._2)

  def findState(
    documentUUID   : String,
    isInitialState : Boolean
  ): Option[XFormsState] = {

    implicit val ec: ExternalContext = CoreCrossPlatformSupport.externalContext
    implicit val indentedLogger: IndentedLogger = XFormsStateManager.newIndentedLogger

    LifecycleLogger.withEventAssumingRequest(
      "xforms",
      "restore state",
      List(
        "uuid" -> documentUUID,
        "backOrReload" -> isInitialState.toString
      )
    ) {
      debug(s"store size before finding: ${getCurrentSize.map(_.toString).getOrElse("unknown")} entries.")

      XFormsStores.stateStore.get(documentUUID).map(_.asInstanceOf[CacheValueMappingType]) match {
        case Some((keyString: String, _: Long)) =>

          // Found the keys, split into parts
          val parts = keyString split ':'

          assert(parts.size == 2)
          assert(parts(0).length >= SecureUtils.HexShortLength) // static state key is an hex hash

          // If isInitialState == true, force finding the initial state. Otherwise, use current state stored in mapping.
          val dynamicStateKey = if (isInitialState) createDynamicStateKey(documentUUID, isInitialState = true) else parts(1)

          // Gather values from cache for both keys and return state only if both are non-null
          LazyList(parts(0), dynamicStateKey) flatMap XFormsStores.stateStore.get filter (_ != null) match {
            case LazyList(staticState: String, dynamicState: DynamicState) =>
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
    XFormsStores.stateStore.remove(documentUUID)
    XFormsStores.stateStore.remove(createDynamicStateKey(documentUUID, isInitialState = true))
    XFormsStores.stateStore.remove(createDynamicStateKey(documentUUID, isInitialState = false))
  }

  def getMaxSize     : Option[Long] = XFormsStores.stateStore.getMaxEntriesLocalHeap
  def getCurrentSize : Option[Long] = XFormsStores.stateStore.getLocalHeapSize

  private object Private {
    def createDynamicStateKey(documentUUID: String, isInitialState: Boolean) =
      documentUUID + (if (isInitialState) "-I" else "-C") // key is different for initial vs. subsequent state
  }
}