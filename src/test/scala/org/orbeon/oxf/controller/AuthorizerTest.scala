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

import org.mockito.Mockito
import org.orbeon.oxf.externalcontext.{ExternalContext, WebAppContext}
import org.orbeon.oxf.http.Headers._
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.collection.JavaConverters._
import scala.collection.mutable

class AuthorizerTest extends AnyFunSpecLike with MockitoSugar {

  import Authorizer._

  describe("Authorization with token") {

    def buildExternalContextWith(appAttributes: Map[String, AnyRef], reqAttributes: Map[String, Array[String]]): ExternalContext = {

      val ec = mock[ExternalContext]
      val webAppContext = mock[WebAppContext]
      val request = mock[ExternalContext.Request]

      Mockito when ec.getRequest thenReturn request
      Mockito when ec.getWebAppContext thenReturn webAppContext
      Mockito when webAppContext.attributes thenReturn (mutable.HashMap() ++ appAttributes)
      Mockito when request.getHeaderValuesMap thenReturn reqAttributes.asJava

      ec
    }

    it("must not be authorized when no token is set in the app") {
      assert(
        ! authorizedWithToken(
          buildExternalContextWith(Map.empty, Map.empty)
        )
      )
    }

    it("must not be authorized when no token is passed") {
      assert(
        ! authorizedWithToken(
          buildExternalContextWith(Map(OrbeonTokenLower -> "1234567890"), Map.empty)
        )
      )
    }

    it("must not be authorized when an incorrect token is passed") {
      assert(
        ! authorizedWithToken(buildExternalContextWith(
          Map(OrbeonTokenLower -> "1234567890"),
          Map(OrbeonTokenLower -> Array("abcdefghij")))
        )
      )
    }

    it("must be authorized when the tokens match") {
      assert(
        authorizedWithToken(
          buildExternalContextWith(
            Map(OrbeonTokenLower -> "1234567890"),
            Map(OrbeonTokenLower -> Array("1234567890"))
          )
        )
      )
    }
  }
}
