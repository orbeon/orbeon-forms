package org.orbeon.oxf.portlet.liferay

import org.orbeon.oxf.portlet.{RequestPrependHeaders, RequestRemoveHeaders}
import org.orbeon.oxf.util.CollectionUtils

import javax.portlet.filter._
import javax.portlet._


class AddLiferayUserHeadersFilter
  extends PortletFilter
     with RenderFilter
     with ActionFilter
     with ResourceFilter
     with EventFilter {

  import AddLiferayUserHeadersFilter._

  def doFilter(req: RenderRequest, res: RenderResponse, chain: FilterChain): Unit =
    chain.doFilter(amendRequestWithUser(req)(wrapWithLiferayUserHeaders), res)

  def doFilter(req: ActionRequest, res: ActionResponse, chain: FilterChain): Unit =
    chain.doFilter(amendRequestWithUser(req)(wrapWithLiferayUserHeaders), res)

  def doFilter(req: ResourceRequest, res: ResourceResponse, chain: FilterChain): Unit =
    chain.doFilter(amendRequestWithUser(req)(wrapWithLiferayUserHeaders), res)

  def doFilter(req: EventRequest, res: EventResponse, chain: FilterChain): Unit =
    chain.doFilter(amendRequestWithUser(req)(wrapWithLiferayUserHeaders), res)

  def init(filterConfig: filter.FilterConfig) = ()
  def destroy() = ()
}

object AddLiferayUserHeadersFilter {

  // NOTE: request.getRemoteUser() can be configured in liferay-portlet.xml with user-principal-strategy to either
  // userId (a number) or screenName (a string). It seems more reliable to use the API below to obtain the user.
  def amendRequestWithUser[T <: PortletRequest](req: T)(amend: (T, LiferayUser) => T): T =
    LiferaySupport.getLiferayUser(req) match {
      case Some(user) => amend(req, user)
      case None       => req
    }

  def wrapWithLiferayUserHeaders[T <: PortletRequest](req: T, user: LiferayUser): T = {

    val liferayUserHeaders =
      CollectionUtils.combineValues[String, String, Array](user.userHeaders) map
        { case (name, value) => name.toLowerCase -> value } toMap

    wrap(req, LiferaySupport.AllHeaderNamesLower, liferayUserHeaders)
  }

  def wrap[T <: PortletRequest](req: T, remove: Set[String], prepend: Map[String, Array[String]]): T = {

    trait CustomProperties extends RequestRemoveHeaders with RequestPrependHeaders  {
      val headersToRemove  = remove
      val headersToPrepend = prepend
    }

    req match {
      case r: RenderRequest   => new RenderRequestWrapper(r)   with CustomProperties
      case r: ActionRequest   => new ActionRequestWrapper(r)   with CustomProperties
      case r: ResourceRequest => new ResourceRequestWrapper(r) with CustomProperties
      case r: EventRequest    => new EventRequestWrapper(r)    with CustomProperties
      case r: PortletRequest  => new PortletRequestWrapper(r)  with CustomProperties
    }

  }.asInstanceOf[T] // We can prove that the types work out for us ;)
}
