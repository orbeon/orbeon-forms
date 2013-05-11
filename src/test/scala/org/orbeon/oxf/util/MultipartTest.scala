/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.util

import java.io.ByteArrayInputStream
import org.apache.commons.fileupload.FileItem
import org.junit.Test
import org.mockito.Mockito
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.processor.test.TestExternalContext.TestSession
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.util.Multipart.UploadProgress
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import scala.collection.mutable

class MultipartTest extends ResourceManagerTestBase with AssertionsForJUnit with MockitoSugar {

    @Test def testParseMultipartRequest(): Unit = {

        val rm = ResourceManagerWrapper.instance

        val body       = NetUtils.inputStreamToByteArray(rm.getContentAsStream("/org/orbeon/oxf/util/multipart-request.bin"))
        val miserables = NetUtils.inputStreamToByteArray(rm.getContentAsStream("/org/orbeon/oxf/util/miserables-8000.txt"))

        assert(body.length == 8326)
        assert(miserables.length == 8000)

        val UUID = "3160b6fe0df34b94ee31a19cb1f79c95b6351441"
        val FieldName = "xf-5"

        def newRequest = {
            val request = mock[ExternalContext.Request]

            val session = new TestSession(SecureUtils.randomHexId)

            Mockito when request.getInputStream thenReturn new ByteArrayInputStream(body)
            Mockito when request.getContentType thenReturn "multipart/form-data; boundary=----WebKitFormBoundaryicZu65oDuNAzes9o"
            Mockito when request.getContentLength thenReturn body.length
            Mockito when request.getCharacterEncoding thenReturn null
            Mockito when request.getSession(false) thenReturn session

            request
        }

        def newRead(request: Request, maxSize: Int) = {
            val (pairs, throwableOpt) = Multipart.parseMultipartRequest(request, maxSize, "utf-8")
            (pairs map { case (a, b) ⇒ a → convertFileItemContent(b) }, throwableOpt map (_.getClass.getName))
        }

        locally {
            val mustSucceedWith = Seq(-1, 8326, 10000)

            val expectedPairs = Seq(
                ("$uuid", UUID),
                (FieldName,  FileItemContent("text/plain", FieldName, 8000L, "miserables-8000.txt", miserables))
            )

            mustSucceedWith foreach { limit ⇒
                val request = newRequest
                assert((expectedPairs, None) === newRead(request, limit))
                assert(Some(UploadProgress(FieldName, Some(body.length), miserables.length, Multipart.Completed)) === Multipart.getUploadProgress(request, UUID, FieldName))
            }
        }

        locally {
            val mustFailWith = Seq(0, 4097, 8000)// NOTE: any value under 4096 is the same as 4096 (buffer size)

            val expectedPairs = Seq(
                ("$uuid", UUID)
            )

            mustFailWith foreach { limit ⇒
                val request = newRequest
                assert((expectedPairs, Some("org.apache.commons.fileupload.FileUploadBase$SizeLimitExceededException")) === newRead(request, limit))
                assert(Some(UploadProgress(FieldName, Some(body.length), 0, Multipart.Interrupted)) === Multipart.getUploadProgress(request, UUID, FieldName))
            }
        }
    }

    // NOTE: Use WrappedArray so that Array content comparison works
    case class FileItemContent(contentType: String, fieldName: String, size: Long, filename: String, content: mutable.WrappedArray[Byte])

    def convertFileItemContent(a: AnyRef) = Some(a) collect {
        case f: FileItem ⇒ FileItemContent(f.getContentType, f.getFieldName, f.getSize, f.getName, NetUtils.inputStreamToByteArray(f.getInputStream))
        case other       ⇒ other
    } get
}
