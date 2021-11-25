/**
 * Copyright (C) 2018 Orbeon, Inc.
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
package org.orbeon.oxf.fr.process

import org.orbeon.oxf.util.StringUtils._
import org.scalatest.funspec.AnyFunSpec


class FormRunnerActionsTest extends AnyFunSpec{

  describe("URL update when sending or changing mode") {

    object SendProcessParams extends ProcessParams {
      def runningProcessId  : String  = "123456789"
      def app               : String  = "acme"
      def form              : String  = "order"
      def formVersion       : Int     = 42
      def document          : String  = "b77484136e0d4631908adba3e42ebe44fa224ed2"
      def valid             : Boolean = true
      def language          : String  = "fr"
      def dataFormatVersion : String  = "edge"
      def workflowStage     : String  = "initial"
    }

    val ServerBase = "https://example.org/"

    val expected = List(
      (
        "no parameters",
        s"$ServerBase",
        "",
        ServerBase
      ),
      (
        "without parameters in the original URL",
        s"$ServerBase",
        "process app form form-version document valid language data-format-version workflow-stage",
        s"$ServerBase?process=123456789&app=acme&form=order&form-version=42&document=b77484136e0d4631908adba3e42ebe44fa224ed2&valid=true&language=fr&data-format-version=edge&workflow-stage=initial"
      ),
      (
        "with parameters in the original URL",
        s"$ServerBase?form-version=2&language=en",
        "process app form form-version document valid language data-format-version workflow-stage",
        s"$ServerBase?process=123456789&app=acme&form=order&document=b77484136e0d4631908adba3e42ebe44fa224ed2&valid=true&data-format-version=edge&workflow-stage=initial&form-version=2&language=en"
      )
    )

    for ((description, uri, params, expected) <- expected)
      it (description) {

        val actual =
          FormRunnerActionsSupport.updateUriWithParams(
            processParams       = SendProcessParams,
            uri                 = uri,
            requestedParamNames = params.splitTo[List]()
          )

        assert(expected === actual)
      }
  }
}
