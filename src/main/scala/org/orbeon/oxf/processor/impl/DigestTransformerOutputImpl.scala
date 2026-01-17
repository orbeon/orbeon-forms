/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.impl

import org.orbeon.oxf.cache.*
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util.NumberUtils

import java.util
import scala.jdk.CollectionConverters.*


/**
 * Implementation of a caching transformer output that assumes that an output simply depends on
 * all the inputs plus optional local information that can be digested.
 */
object DigestTransformerOutputImpl {
  private val DEFAULT_VALIDITY = 0L
}

abstract class DigestTransformerOutputImpl(processor: ProcessorImpl, name: String) extends CacheableTransformerOutputImpl(processor, name) {

  override final protected def supportsLocalKeyValidity = true

  override protected def getLocalKey(pipelineContext: PipelineContext): CacheKey = {

    for (inputName <- getProcessor(pipelineContext).getInputNames.asScala)
      if (! getProcessor(pipelineContext).isInputInCache(pipelineContext, inputName)) { // NOTE: We don't really support multiple inputs with the same name.
        System.out.println("yyy DigestTransformerOutputIMpl: config not in cache")
        return null
      }
    getFilledOutState(pipelineContext).key
  }

  override final protected def getLocalValidity(pipelineContext: PipelineContext): AnyRef = {
    for (inputName <- getProcessor(pipelineContext).getInputNames.asScala)
      if (! getProcessor(pipelineContext).isInputInCache(pipelineContext, inputName)) // NOTE: We don't really support multiple inputs with the same name.
        return null

    getFilledOutState(pipelineContext).validity
  }

  /**
   * Fill-out user data into the state, if needed. Return caching information.
   *
   * @param pipelineContext the current PipelineContext
   * @param digestState     state set during processor start() or reset()
   * @return false if private information is known that requires disabling caching, true otherwise
   */
  def fillOutState(pipelineContext: PipelineContext, digestState: DigestState): Boolean

  /**
   * Compute a digest of the internal document on which the output depends.
   *
   * @param digestState state set during processor start() or reset()
   * @return the digest
   */
  def computeDigest(pipelineContext: PipelineContext, digestState: DigestState): Array[Byte]

  final protected def getFilledOutState(pipelineContext: PipelineContext): DigestState = {
    // This is called from both readImpl and getLocalValidity. Based on the assumption that
    // a getKeyImpl will be followed soon by a readImpl if it fails, we compute key,
    // validity, and user-defined data.
    val state = getProcessor(pipelineContext).getState(pipelineContext).asInstanceOf[DigestState]

    // Create request document
    val allowCaching = fillOutState(pipelineContext, state)

    // Compute key and validity if possible
    if ((state.validity == null || state.key == null) && allowCaching) {
      // Compute digest
      if (state.digest == null)
        state.digest = computeDigest(pipelineContext, state)
      // Compute local key
      if (state.key == null)
        state.key = new InternalCacheKey(getProcessor(pipelineContext), "requestHash", NumberUtils.toHexString(state.digest))
      // Compute local validity
      if (state.validity == null) {
        state.validity = DigestTransformerOutputImpl.DEFAULT_VALIDITY // HACK so we don't recurse at the next line

        val outputCacheKey = getKeyImpl(pipelineContext)
        if (outputCacheKey != null) {
          val cache = ObjectCache.instance
          val digestValidity = cache.findValid(outputCacheKey, DigestTransformerOutputImpl.DEFAULT_VALIDITY).asInstanceOf[DigestValidity]
          if (digestValidity != null && util.Arrays.equals(state.digest, digestValidity.digest))
            state.validity = digestValidity.lastModified
          else {
            val currentValidity = System.currentTimeMillis: java.lang.Long
            cache.add(outputCacheKey, DigestTransformerOutputImpl.DEFAULT_VALIDITY, new DigestValidity(state.digest, currentValidity))
            state.validity = currentValidity
          }
        }
        else state.validity = null // HACK restore
      }
    }
    state
  }
}
