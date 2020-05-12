/**
 *  Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.controller

import org.orbeon.oxf.http.Headers._
import org.junit.Test
import org.mockito.Mockito
import org.orbeon.oxf.externalcontext.{ExternalContext, WebAppContext}

import collection.JavaConverters._
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar

class AuthorizerTest extends AssertionsForJUnit with MockitoSugar {

  import Authorizer._

  @Test def testAuthorizedWithToken(): Unit = {

    val appAttributes     = collection.mutable.Map[String, AnyRef]()
    val requestAttributes = collection.mutable.Map[String, Array[String]]()

    val ec = mock[ExternalContext]
    val webAppContext = mock[WebAppContext]
    val request = mock[ExternalContext.Request]

    Mockito when ec.getRequest thenReturn request
    Mockito when ec.getWebAppContext thenReturn webAppContext
    Mockito when webAppContext.attributes thenReturn appAttributes
    Mockito when request.getHeaderValuesMap thenReturn requestAttributes.asJava

    assert(! authorizedWithToken(ec))

    appAttributes += OrbeonTokenLower -> "1234567890"
    assert(! authorizedWithToken(ec))

    requestAttributes += OrbeonTokenLower -> Array("abcdefghij")
    assert(! authorizedWithToken(ec))

    requestAttributes += OrbeonTokenLower -> Array("1234567890")
    assert(authorizedWithToken(ec))
  }
}
