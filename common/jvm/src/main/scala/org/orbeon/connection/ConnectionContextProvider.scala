package org.orbeon.connection

import java.{util => ju}
import java.net.URI


/**
 * Interface for a provider that can provide a connection context. Orbeon Forms can perform internal
 * service connections that run on different threads than the incoming service thread. In case information related
 * to the incoming service thread needs to be passed downstream, an implementation of this interface can be used.
 * For example, information can be retrieved and set from and to `ThreadLocal`s.
 *
 * @tparam Context The type of the context object, defined by the implementation
 */
trait ConnectionContextProvider[Context <: AnyRef] {

  /**
   * Get context information. This method is called on the incoming service thread.
   *
   * The returned object must not keep reference to `Thread` or other mutable data, as the calling thread can be
   * returned to a thread pool before `pushContext()` is called.
   *
   * @param extension immutable extension map, reserved for the future, currently empty and not `null`
   * @return instance of context information or `null`
   */
  def getContext(extension: ju.Map[String, Any]): Context

  /**
   * Push context information. This method is called on the outgoing connection thread.
   *
   * If `getContext()` returned `null`, this method is not called.
   *
   * @param ctx       instance of context information, as returned by `getContext()`; not `null`
   * @param url       URI containing information about the connection
   * @param method    HTTP method name (`GET`, `PUT`, etc.)
   * @param headers   HTTP headers
   * @param extension immutable extension map, reserved for the future, currently empty and not `null`
   */
  def pushContext(
    ctx      : Context,
    url      : URI,
    method   : String,
    headers  : ju.Map[String, Array[String]],
    extension: ju.Map[String, Any]
  ): Unit

  /**
   * Pop context information. This method is called on the outgoing connection thread. The purpose is to allow
   * cleanup of resources allocated by `pushContext()`. For example, a `ThreadLocal` can be removed.
   *
   * If `getContext()` returned `null`, this method is not called.
   *
   * This is guaranteed to be called after the outgoing connection is done, even if an exception is thrown.
   *
   * @param ctx instance of context information, as returned by `getContext()`; not `null`
   */
  def popContext(ctx: Context): Unit
}
