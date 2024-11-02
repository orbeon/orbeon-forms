package org.orbeon.oxf.xforms

import org.log4s
import org.orbeon.connection.BufferedContent
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XMLSupport}
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger, LoggerFactory, PathUtils}
import org.orbeon.oxf.xforms.model.InstanceCaching
import org.orbeon.scaxon.NodeConversions.elemToDocumentInfo
import org.scalatest.funspec.AnyFunSpecLike


class XFormsServerSharedInstancesCacheTest
  extends XFormsServerSharedInstancesCacheTestPlatform
     with AnyFunSpecLike {

  val logger: log4s.Logger = LoggerFactory.createLogger(classOf[XFormsServerSharedInstancesCacheTest])

  describe("XFormsServerSharedInstancesCache") {

    implicit val indentedLogger: IndentedLogger = new IndentedLogger(logger, true)

    // The two `GET` requests are different by path only
    val instanceCachingGet1 = InstanceCaching(
      timeToLive        = -1L,
      handleXInclude    = false,
      pathOrAbsoluteURI = "https://example.com/get/1",
      method            = HttpMethod.GET,
      requestContent    = None
    )

    val instanceCachingGet2 = InstanceCaching(
      timeToLive        = -1L,
      handleXInclude    = false,
      pathOrAbsoluteURI = "https://example.com/get/2",
      method            = HttpMethod.GET,
      requestContent    = None
    )

    // The two `POST` requests are different by request content only
    val postRequestContent1 = BufferedContent(Array(1), Some(ContentTypes.XmlContentType), None)
    val postRequestContent2 = BufferedContent(Array(2), Some(ContentTypes.XmlContentType), None)

    val instanceCachingPost1 = InstanceCaching(
      timeToLive        = -1L,
      handleXInclude    = false,
      pathOrAbsoluteURI = "https://example.com/post",
      method            = HttpMethod.POST,
      requestContent    = Some(postRequestContent1)
    )

    val instanceCachingPost2 = InstanceCaching(
      timeToLive        = -1L,
      handleXInclude    = false,
      pathOrAbsoluteURI = "https://example.com/post",
      method            = HttpMethod.POST,
      requestContent    = Some(postRequestContent2)
    )

    val GetDoc1: DocumentNodeInfoType  = elemToDocumentInfo(<_>GET1</_>)
    val GetDoc2: DocumentNodeInfoType  = elemToDocumentInfo(<_>GET2</_>)
    val PostDoc1: DocumentNodeInfoType = elemToDocumentInfo(<_>POST1</_>)
    val PostDoc2: DocumentNodeInfoType = elemToDocumentInfo(<_>POST2</_>)

    def loader(instanceCaching: InstanceCaching): DocumentNodeInfoType =
      instanceCaching match {
        case InstanceCaching(_, _, instanceCachingGet1.pathOrAbsoluteURI,  instanceCachingGet1.method,  None)                        => GetDoc1
        case InstanceCaching(_, _, instanceCachingGet2.pathOrAbsoluteURI,  instanceCachingGet2.method,  None)                        => GetDoc2
        case InstanceCaching(_, _, instanceCachingPost1.pathOrAbsoluteURI, instanceCachingPost1.method, Some(`postRequestContent1`)) => PostDoc1
        case InstanceCaching(_, _, instanceCachingPost2.pathOrAbsoluteURI, instanceCachingPost2.method, Some(`postRequestContent2`)) => PostDoc2
        case _ => throw new IllegalArgumentException(s"Unsupported instance caching: $instanceCaching")
      }

    def throwingLoader(instanceCaching: InstanceCaching): DocumentNodeInfoType =
      throw new RuntimeException("This loader should not be called")

    describe("initially") {
      for (instanceCaching <- List(instanceCachingGet1, instanceCachingGet2, instanceCachingPost1, instanceCachingPost2))
        it(s"should not find ${instanceCaching.method} ${instanceCaching.pathOrAbsoluteURI} ${instanceCaching.requestContent.map(_.body.toList)} in cache") {
          assert(
            XFormsServerSharedInstancesCache.findContent(
              instanceCaching,
              readonly         = true,
              exposeXPathTypes = true,
            ).isEmpty
          )
        }
    }

    for ((instanceCaching, doc) <- List(
      instanceCachingGet1  -> GetDoc1,
      instanceCachingGet2  -> GetDoc2,
      instanceCachingPost1 -> PostDoc1,
      instanceCachingPost2 -> PostDoc2,
    )) describe(s"adding and getting for ${instanceCaching.method} ${instanceCaching.pathOrAbsoluteURI} ${instanceCaching.requestContent.map(_.body.toList)}") {
      it(s"should not find it in cache initially") {
        XFormsServerSharedInstancesCache.findContent(
          instanceCaching,
          readonly         = true,
          exposeXPathTypes = true
        ).isEmpty
      }
      it(s"should load and cache it") {
        XMLSupport.assertXMLDocumentsIgnoreNamespacesInScope(
          XFormsServerSharedInstancesCache.findContentOrLoad(
            instanceCaching,
            readonly         = true,
            exposeXPathTypes = true,
            loadInstance     = loader
          ),
          doc
        )
      }
      it(s"should find it in cache without loading") {
        XMLSupport.assertXMLDocumentsIgnoreNamespacesInScope(
          XFormsServerSharedInstancesCache.findContentOrLoad(
            instanceCaching,
            readonly         = true,
            exposeXPathTypes = true,
            loadInstance     = throwingLoader
          ),
          doc
        )
      }
    }

    describe(s"removing from cache") {

      it(s"${instanceCachingGet1.pathOrAbsoluteURI} should not removed from cache if query string is different") {
        XFormsServerSharedInstancesCache.remove(
          PathUtils.appendQueryString(instanceCachingGet1.pathOrAbsoluteURI, "query=1"),
          handleXInclude    = Some(false),
          ignoreQueryString = false
        )
        XFormsServerSharedInstancesCache.findContent(
          instanceCachingGet1,
          readonly         = true,
          exposeXPathTypes = true
        ).nonEmpty
      }

      it(s"${instanceCachingGet1.pathOrAbsoluteURI} should be removed from cache if query string is ignored") {
        XFormsServerSharedInstancesCache.remove(
          PathUtils.appendQueryString(instanceCachingGet1.pathOrAbsoluteURI, "query=1"),
          handleXInclude    = Some(false),
          ignoreQueryString = false
        )
        XFormsServerSharedInstancesCache.findContent(
          instanceCachingGet1,
          readonly         = true,
          exposeXPathTypes = true
        ).isEmpty
      }
      it(s"${instanceCachingGet2.pathOrAbsoluteURI} should be removed from cache") {
        XFormsServerSharedInstancesCache.remove(
          instanceCachingGet2.pathOrAbsoluteURI,
          handleXInclude    = Some(false),
          ignoreQueryString = false
        )
        XFormsServerSharedInstancesCache.findContent(
          instanceCachingGet2,
          readonly         = true,
          exposeXPathTypes = true
        ).isEmpty
      }
      it(s"${instanceCachingPost1.pathOrAbsoluteURI} should be removed from cache independently of request content") {
        XFormsServerSharedInstancesCache.remove(
          instanceCachingPost1.pathOrAbsoluteURI,
          handleXInclude    = Some(false),
          ignoreQueryString = false
        )
        XFormsServerSharedInstancesCache.findContent(
          instanceCachingPost1,
          readonly         = true,
          exposeXPathTypes = true
        ).isEmpty
        XFormsServerSharedInstancesCache.findContent(
          instanceCachingPost2,
          readonly         = true,
          exposeXPathTypes = true
        ).isEmpty
      }
    }

    describe(s"removing all from cache") {
      it("must not find any of the instances after `removeAll()`") {
        for ((instanceCaching, doc) <- List(
          instanceCachingGet1  -> GetDoc1,
          instanceCachingGet2  -> GetDoc2,
          instanceCachingPost1 -> PostDoc1,
          instanceCachingPost2 -> PostDoc2,
        )) XMLSupport.assertXMLDocumentsIgnoreNamespacesInScope(
            XFormsServerSharedInstancesCache.findContentOrLoad(
              instanceCaching,
              readonly         = true,
              exposeXPathTypes = true,
              loadInstance     = loader
            ),
            doc
          )
        XFormsServerSharedInstancesCache.removeAll()
        for (instanceCaching <- List(
          instanceCachingGet1,
          instanceCachingGet2,
          instanceCachingPost1,
          instanceCachingPost2,
        ))
          assert(
            XFormsServerSharedInstancesCache.findContent(
              instanceCaching,
              readonly         = true,
              exposeXPathTypes = true
            ).isEmpty
          )
      }
    }
  }
}
