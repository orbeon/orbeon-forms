package org.orbeon.oxf.cache

import java.util.concurrent.locks.Lock


/**
 * Interface that cacheable objects can optionally implement to support callback methods.
 */
trait Cacheable {
  /**
   * Called when the object is added to the cache.
   */
  def added(): Unit

  /**
   * Called when the object is explicitly removed from the cache.
   */
  def removed(): Unit

  /**
   * Optional lock the cache must obtain to evict the item from cache.
   *
   * @return lock or null
   */
  def getEvictionLock: Lock

  /**
   * Called when the object is being evicted from cache.
   */
  def evicted(): Unit
}