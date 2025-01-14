package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.oxf.util.DateUtils
import org.scalatest.funspec.AnyFunSpecLike

import scala.jdk.CollectionConverters.*


class CachingResponseSupportTest
  extends ResourceManagerSupport
    with AnyFunSpecLike {

  describe("Caching response support") {

    val ifModifiedHeaderString = "Thu, 28 Jun 2007 14:17:36 GMT"
    val ifModifiedHeaderLong   = DateUtils.parseRFC1123(ifModifiedHeaderString)

    it ("should check if modified since") {

      val request = new RequestAdapter {
        override def getMethod: HttpMethod = HttpMethod.GET
        override val getHeaderValuesMap = Map(Headers.IfModifiedSince -> Array(ifModifiedHeaderString)).asJava
      }

      assert(! CachingResponseSupport.checkIfModifiedSince(request, ifModifiedHeaderLong - 1))
      assert(! CachingResponseSupport.checkIfModifiedSince(request, ifModifiedHeaderLong))
      // For some reason the code checks that there is more than one second of difference
      assert(CachingResponseSupport.checkIfModifiedSince(request, ifModifiedHeaderLong + 1001))
    }
  }
}
