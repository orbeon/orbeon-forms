package org.orbeon.properties.api

import java.net.URI
import java.util as ju


trait PropertyDefinition {
  def getName      : String
  def getValue     : String
  def getType      : String                 // `string | integer | boolean | nmtokens | anyURI`
  def getNamespaces: ju.Map[String, String] // immutable
  def getCategory  : ju.Optional[String]    // EQName
}

trait PropertyDefinitionsWithETag {
  val getProperties: PropertyDefinitions
  val getETag      : ETag
}

trait Organization {
  def getLevels: ju.Collection[String]
}

trait Credentials {
  def getUsername     : String
  def getGroupname    : ju.Optional[String]
  def getRoles        : ju.Collection[String]
  def getOrganizations: ju.Collection[Organization]
}

// The provider must only set and remove its own attributes
trait Session {
  def getId: String
  def getAttribute(name: String): ju.Optional[AnyRef]
  def setAttribute(name: String, value: AnyRef): Unit
  def removeAttribute(name: String): Unit
  //  def getAttributeNames: ju.Collection[String]
}

trait Request {
  def getMethod    : String  // `HEAD`, `GET`, `POST`, but also `PUT`, `DELETE`, etc. for service calls
  def getRequestUri: URI     // includes scheme, host, port, path, query string as available from the container
  def getHeaders   : ju.Map[String, ju.Collection[String]]
  //  def incomingCookies: Iterable[(String, String)]
  //  def getAttributesMap: ju.Map[String, AnyRef]
}

trait PropertyProvider {

  /**
   * Return a cache key to identify the properties for the given request and credentials.
   *
   * If the provider does not handle properties that can vary with the user, this method does not have to be overridden.
   * A default implementation returns an empty `Optional`.
   *
   * If returned, the key must be a non-blank string, be reasonably small, and uniquely identify the user, tenant, or
   * other category of callers that share the same properties.
   *
   * The key is used by the caller to cache the ETag which is then passed to `getPropertiesIfNeeded()`.
   */
  def getCacheKey(
    request    : ju.Optional[Request],
    credentials: ju.Optional[Credentials],
    session    : ju.Optional[Session],
    extension  : ju.Map[String, Any],
  ): ju.Optional[String] =
    ju.Optional.empty[String]

  /**
   * Return a `Collection` of properties.
   *
   * If `eTag` is empty or does not match the provider's current ETag for the properties, possibly based on `cacheKey`,
   * the provider must return up-to-date properties along with their ETag. On the other hand, if `eTag` matches
   * the provider's current ETag for the properties, possibly based on `cacheKey`, then the provider must return
   * an empty `Optional`, indicating that the caller can use cached properties.
   *
   * If the provider does not have any properties to return, it should return `PropertyDefinitionsWithETag` with an
   * empty `properties` collection and an appropriate ETag. It should not return an empty `Optional` in this case.
   *
   * WARNING: This method can be called concurrently from multiple threads.
   */
  def getPropertiesIfNeeded(
    cacheKey   : ju.Optional[CacheKey],
    eTag       : ju.Optional[ETag],
    // Q: Do we need to pass all of these again? Maybe `getCacheKey()` should be enough.
    request    : ju.Optional[Request],
    credentials: ju.Optional[Credentials],
    session    : ju.Optional[Session],
    extension  : ju.Map[String, Any],
  ): ju.Optional[PropertyDefinitionsWithETag]
}
