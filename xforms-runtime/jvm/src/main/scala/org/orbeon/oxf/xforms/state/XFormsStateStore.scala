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
import org.orbeon.oxf.util.{Compressor, CoreCrossPlatformSupport, IndentedLogger, SecureUtils}
import org.orbeon.oxf.xforms.*
import org.orbeon.oxf.xml.EncodeDecode


object XFormsStateStore {

  import Private.*

  // `(staticStateDigest, dynamicState)` — uses pure Scala Tuple2 + String + DynamicState
  // so it satisfies existing Infinispan JavaSerializationMarshaller `<allow-list>` configurations.
  type StoredDynamicStateEntry = (String, DynamicState)

  def storeStaticState(staticState: XFormsStaticState, refresh: Boolean): Unit = {
    val bytes =
      staticState match {
        case impl: XFormsStaticStateImpl =>
          impl.serverEncodedStateBytes
        case other =>
          Compressor.compressBytes(EncodeDecode.decodeBytes(other.encodedState, forceEncryption = false, SecureUtils.KeyUsage.GeneralNoCheck))
      }

    if (refresh)
      XFormsStores.staticStateStore.put(staticState.digest, bytes)
    else
      XFormsStores.staticStateStore.putIfAbsent(staticState.digest, bytes)
  }

  def findStaticStateBytes(staticStateDigest: String): Option[Array[Byte]] =
    XFormsStores.staticStateStore.get(staticStateDigest).collect {
      case bytes: Array[Byte] => bytes
      case str: String        => Compressor.compressBytes(EncodeDecode.decodeBytes(str, forceEncryption = false, SecureUtils.KeyUsage.GeneralNoCheck))
    }

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
      val dynamicState      = DynamicState(document)

      // Persist raw compressed static state in `xforms.static-state` (refreshing timestamp on initial load)
      storeStaticState(document.staticState, refresh = isInitialState)

      // Store dynamic state in `xforms.state` without a separate mapping entry:
      // - `uuid-I` holds `(staticStateDigest, initialDynamicState)` (written once upon initial load)
      // - `uuid` holds `(staticStateDigest, currentDynamicState)` (written only upon eviction or update)
      val key = if (isInitialState) createInitialDynamicStateKey(documentUUID) else documentUUID
      XFormsStores.stateStore.put(key, (staticStateDigest, dynamicState): StoredDynamicStateEntry)
    }
  }

  def findSequence(documentUUID: String): Option[Long] =
    findStoredEntry(documentUUID, isInitialState = false).map(_._2.sequence)

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

      findStoredEntry(documentUUID, isInitialState) match {
        case Some((staticStateDigest, dynamicState)) =>

          assert(staticStateDigest.length >= SecureUtils.HexShortLength) // static state key is a hex hash

          // Avoid reading/deserializing serialized static state from `xforms.static-state` when the compiled
          // `XFormsStaticState` is already live in `XFormsStaticStateCache`.
          if (XFormsStaticStateCache.findDocument(staticStateDigest).isDefined)
            Some(XFormsState(Some(staticStateDigest), staticState = None, Some(dynamicState), staticStateBytes = None))
          else
            findStaticStateBytes(staticStateDigest).map { bytes =>
              XFormsState(Some(staticStateDigest), staticState = None, Some(dynamicState), staticStateBytes = Some(bytes))
            }

        case None =>
          None
      }
    }
  }

  // NOTE: Don't remove the static state as it might be in use by other form sessions.
  def removeDynamicState(documentUUID: String): Unit = {
    XFormsStores.stateStore.remove(documentUUID)
    XFormsStores.stateStore.remove(createInitialDynamicStateKey(documentUUID))
  }

  def getMaxSize     : Option[Long] = XFormsStores.stateStore.getMaxEntriesLocalHeap
  def getCurrentSize : Option[Long] = XFormsStores.stateStore.getCurrentEntriesLocalHeap

  private object Private {
    def createInitialDynamicStateKey(documentUUID: String): String =
      documentUUID + "-I"

    private def getEntry(key: String): Option[StoredDynamicStateEntry] =
      XFormsStores.stateStore.get(key).collect {
        case (digest: String, ds: DynamicState) => (digest, ds)
      }

    def findStoredEntry(documentUUID: String, isInitialState: Boolean): Option[StoredDynamicStateEntry] =
      if (isInitialState)
        getEntry(createInitialDynamicStateKey(documentUUID))
      else
        getEntry(documentUUID).orElse(getEntry(createInitialDynamicStateKey(documentUUID)))
  }
}