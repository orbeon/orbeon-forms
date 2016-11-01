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

import org.orbeon.oxf.logging.LifecycleLogger
import net.sf.ehcache.{Element ⇒ EhElement}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.util.SecureUtils

/**
 * XForms state cache based on Ehcache.
 */
object EhcacheStateStore extends XFormsStateStore {

  private val storeDebugName = "Ehcache"

  private def stateCache = Caches.stateCache

  def storeDocumentState(document: XFormsContainingDocument, session: ExternalContext.Session, isInitialState: Boolean) = {

    assert(document.getStaticState.isServerStateHandling)

    if (! isInitialState)
      LifecycleLogger.eventAssumingRequest("xforms", "save state", List("uuid" → document.getUUID))

    if (isDebugEnabled)
      debug("store size before storing: " + getCurrentSize + " entries.")

    val documentUUID = document.getUUID
    val staticStateDigest = document.getStaticState.digest
    val dynamicStateKey = getDynamicStateKey(documentUUID, isInitialState)

    def addOrReplaceOne(key: String, value: java.io.Serializable) =
      stateCache.put(new EhElement(key, value))

    // Mapping (UUID → static state key : dynamic state key
    addOrReplaceOne(documentUUID, staticStateDigest + ":" + dynamicStateKey)

    // Static and dynamic states
    addOrReplaceOne(staticStateDigest, document.getStaticState.encodedState) // XXX Q: is there a cost to replacing static state? value will be the same!
    addOrReplaceOne(dynamicStateKey, DynamicState(document))
  }

  def findState(session: ExternalContext.Session, documentUUID: String, isInitialState: Boolean): XFormsState =
    LifecycleLogger.withEventAssumingRequest("xforms", "restore state", List("uuid" → documentUUID, "backOrReload" → isInitialState.toString)) {

      if (isDebugEnabled)
        debug("store size before finding: " + getCurrentSize + " entries.")

      def findOne(key: String) = stateCache.get(key) match {
        case element: EhElement ⇒ element.getObjectValue
        case _ ⇒ null
      }

      findOne(documentUUID) match {
        case keyString: String ⇒
          // Found the keys, split into parts
          val parts = keyString split ':'

          assert(parts.size == 2)
          assert(parts(0).length == SecureUtils.HexIdLength)   // static state key is an hex hash

          // If isInitialState == true, force finding the initial state. Otherwise, use current state stored in mapping.
          val dynamicStateKey = if (isInitialState) getDynamicStateKey(documentUUID, true) else parts(1)

          // Gather values from cache for both keys and return state only if both are non-null
          Stream(parts(0), dynamicStateKey) map findOne filter (_ ne null) match {
            case Stream(staticState: String, dynamicState: DynamicState) ⇒
              XFormsState(Some(parts(0)), staticState, dynamicState)
            case _ ⇒ null
          }

        case _ ⇒ null
      }
    }

  def getMaxSize = stateCache.getCacheConfiguration.getMaxEntriesLocalHeap
  def getCurrentSize = stateCache.getMemoryStoreSize

  def findStateCombined(staticStateDigest: String, dynamicStateUUID: String) = null
  def addStateCombined(staticStateDigest: String, dynamicStateUUID: String, xformsState: XFormsState, sessionId: String) = ()

  private def getDynamicStateKey(documentUUID: String, isInitialState: Boolean) =
    documentUUID + (if (isInitialState) "-I" else "-C") // key is different for initial vs. subsequent state

  private def isDebugEnabled = XFormsStateManager.getIndentedLogger.isDebugEnabled

  private def debug(message: String) =
    XFormsStateManager.getIndentedLogger.logDebug("", storeDebugName + " store: " + message)
}