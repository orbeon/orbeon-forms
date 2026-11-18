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

  // (staticStateDigest, sequence, currentDynamicStateOpt) — uses pure Scala tuple + Option + DynamicState
  // so it satisfies existing Infinispan JavaSerializationMarshaller `<allow-list>` configurations.
  type StoredDocumentMapping = (String, Long, Option[DynamicState])

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
      val sequence          = document.sequence
      val dynamicState      = DynamicState(document)

      // Persist raw compressed static state in `xforms.static-state` (refreshing timestamp on initial load)
      storeStaticState(document.staticState, refresh = isInitialState)

      // Consolidate dynamic state in `xforms.state`:
      // - `uuid-I` holds the initial dynamic state (written only once upon initial load)
      // - `uuid` holds `(staticStateDigest, sequence, currentDynamicStateOpt)`
      if (isInitialState) {
        XFormsStores.stateStore.put(createInitialDynamicStateKey(documentUUID), dynamicState)
        XFormsStores.stateStore.put(documentUUID, (staticStateDigest, sequence, None): StoredDocumentMapping)
      } else {
        XFormsStores.stateStore.put(documentUUID, (staticStateDigest, sequence, Some(dynamicState)): StoredDocumentMapping)
      }
    }
  }

  def findSequence(documentUUID: String): Option[Long] =
    XFormsStores.stateStore.get(documentUUID).collect {
      case (_: String, seq: Long, _: Option[?]) => seq
    }

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

      XFormsStores.stateStore.get(documentUUID) match {
        case Some((staticStateDigest: String, _: Long, currentStateOpt: Option[DynamicState @unchecked])) =>

          assert(staticStateDigest.length >= SecureUtils.HexShortLength) // static state key is a hex hash

          val dynamicStateOpt =
            if (isInitialState)
              findInitialDynamicState(documentUUID)
            else
              currentStateOpt.orElse(findInitialDynamicState(documentUUID))

          dynamicStateOpt.flatMap { dynamicState =>
            // Avoid reading/deserializing serialized static state from `xforms.static-state` when the compiled
            // `XFormsStaticState` is already live in `XFormsStaticStateCache`.
            if (XFormsStaticStateCache.findDocument(staticStateDigest).isDefined)
              Some(XFormsState(Some(staticStateDigest), staticState = None, Some(dynamicState), staticStateBytes = None))
            else
              findStaticStateBytes(staticStateDigest).map { bytes =>
                XFormsState(Some(staticStateDigest), staticState = None, Some(dynamicState), staticStateBytes = Some(bytes))
              }
          }

        case _ =>
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

    def findInitialDynamicState(documentUUID: String): Option[DynamicState] =
      XFormsStores.stateStore.get(createInitialDynamicStateKey(documentUUID)).collect {
        case ds: DynamicState => ds
      }
  }
}