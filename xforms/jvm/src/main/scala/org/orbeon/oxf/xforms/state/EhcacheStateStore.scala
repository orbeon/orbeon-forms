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

import net.sf.ehcache.{Element => EhElement}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.util.{IndentedLogger, SecureUtils}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.util.Logging._

object EhcacheStateStore {

  import Private._

  def storeDocumentState(
    document       : XFormsContainingDocument,
    session        : ExternalContext.Session,
    isInitialState : Boolean
  ): Unit = {

    assert(document.getStaticState.isServerStateHandling)

    if (! isInitialState)
      LifecycleLogger.eventAssumingRequest("xforms", "save state", List("uuid" -> document.getUUID))

    val documentUUID = document.getUUID

    withDebug("storing document state", List(
      "document UUID"             -> documentUUID,
      "store size before storing" -> getCurrentSize.toString,
      "replication"               -> XFormsProperties.isReplication.toString
    )) {
      val staticStateDigest = document.getStaticState.digest
      val dynamicStateKey   = createDynamicStateKey(documentUUID, isInitialState)
      val sequence          = document.getSequence

      def addOrReplaceOne(key: String, value: java.io.Serializable): Unit =
        Caches.stateCache.put(new EhElement(key, value, sequence))

      // Mapping (UUID -> static state key : dynamic state key
      addOrReplaceOne(documentUUID, staticStateDigest + ":" + dynamicStateKey)

      // Static and dynamic states
      addOrReplaceOne(staticStateDigest, document.getStaticState.encodedState) // XXX Q: is there a cost to replacing static state? value will be the same!
      addOrReplaceOne(dynamicStateKey, DynamicState(document))
    }
  }

  def findSequence(documentUUID: String): Option[Long] =
    Option(Caches.stateCache.get(documentUUID)) map (_.getVersion)

  def findState(
    session        : ExternalContext.Session,
    documentUUID   : String,
    isInitialState : Boolean
  ): Option[XFormsState] =
    LifecycleLogger.withEventAssumingRequest(
      "xforms",
      "restore state",
      List(
        "uuid" -> documentUUID,
        "backOrReload" -> isInitialState.toString
      )
    ) {

      debug(s"store size before finding: $getCurrentSize entries.")

      def findOne(key: String) = Option(Caches.stateCache.get(key)) map (_.getObjectValue)

      findOne(documentUUID) match {
        case Some(keyString: String) =>

          // Found the keys, split into parts
          val parts = keyString split ':'

          assert(parts.size == 2)
          assert(parts(0).length == SecureUtils.HexIdLength)   // static state key is an hex hash

          // If isInitialState == true, force finding the initial state. Otherwise, use current state stored in mapping.
          val dynamicStateKey = if (isInitialState) createDynamicStateKey(documentUUID, isInitialState = true) else parts(1)

          // Gather values from cache for both keys and return state only if both are non-null
          Stream(parts(0), dynamicStateKey) flatMap findOne filter (_ ne null) match {
            case Stream(staticState: String, dynamicState: DynamicState) =>
              Some(XFormsState(Some(parts(0)), Some(staticState), Some(dynamicState)))
            case _ =>
              None
          }

        case _ =>
          None
      }
    }

  // NOTE: Don't remove the static state as it might be in use by other form sessions.
  def removeDynamicState(documentUUID: String): Unit = {
    Caches.stateCache.remove(documentUUID)
    Caches.stateCache.remove(createDynamicStateKey(documentUUID, isInitialState = true))
    Caches.stateCache.remove(createDynamicStateKey(documentUUID, isInitialState = false))
  }

  def getMaxSize     : Long = Caches.stateCache.getCacheConfiguration.getMaxEntriesLocalHeap
  def getCurrentSize : Long = Caches.stateCache.getMemoryStoreSize

  private object Private {

    implicit val logger: IndentedLogger = XFormsStateManager.Logger

    def createDynamicStateKey(documentUUID: String, isInitialState: Boolean) =
      documentUUID + (if (isInitialState) "-I" else "-C") // key is different for initial vs. subsequent state
  }
}