/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.test

import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.relational.Version.{Specific, Unspecified}
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory, XPath}
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.Futures.{interval, timeout}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Second, Seconds, Span}

class DistinctControlValuesTest
  extends DocumentTestBase
    with XFormsSupport
    with ResourceManagerSupport
    with AnyFunSpecLike {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[SearchTest]), true)
  private implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

  describe("Distinct Control Values API") {

    it("must work with a single static input") {
      withTestExternalContext { implicit externalContext =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val testForm    = TestForm(controls = Seq(TestForm.Control("control label")))
          val controlPath = testForm.controlPath(0)

          val request =
            <distinct-control-values>
              <control path={controlPath}/>
            </distinct-control-values>.toDocument

          // TODO: add single helper method?
          val formURL = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"

          HttpAssert.put(formURL, Unspecified, HttpCall.XML(testForm.formDefinition(provider)), StatusCode.Created)

          // TODO: we might want to publish the form and remove the `migration` metadata from the form definition

          // TODO: add single helper method?
          def dataURL(id: String) = HttpCall.crudURLPrefix(provider) + s"data/$id/data.xml"

          val version = Specific(1)

          HttpAssert.put(dataURL("1"), version, HttpCall.XML(testForm.formData(Seq("a"))), StatusCode.Created)
          HttpAssert.put(dataURL("2"), version, HttpCall.XML(testForm.formData(Seq("b"))), StatusCode.Created)

          val url = HttpCall.distinctControlValueURLPrefix(provider)

          val httpResponse = HttpCall.post(
            url     = url,
            version = version,
            body    = HttpCall.XML(request)
          )

          val result = XFormsCrossPlatformSupport.readTinyTree(
            XPath.GlobalConfiguration,
            httpResponse.content.inputStream,
            url,
            handleXInclude = false,
            handleLexical = false
          )

          val distinctControlValues =
            for {
              control <- result.rootElement / "control"
              if control.attValue("path") == controlPath
              values  <- control / "values"
              value   <- values / "value"
            } yield value.stringValue

          eventually(timeout(Span(10, Seconds)), interval(Span(1, Second))) {
            assert(distinctControlValues.toSet == Set("a", "b"))
          }
        }
      }
    }
  }
}
