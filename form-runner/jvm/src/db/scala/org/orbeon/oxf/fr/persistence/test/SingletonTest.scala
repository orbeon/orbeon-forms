/**
 * Copyright (C) 2025 Orbeon, Inc.
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

import org.orbeon.dom
import org.orbeon.dom.Document
import org.orbeon.oxf.externalcontext.SafeRequestContext
import org.orbeon.oxf.fr.Version._
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.fr.persistence.db._
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Provider._
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport, XMLSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.Converter._
import org.scalatest.funspec.AnyFunSpecLike

class SingletonTest
  extends DocumentTestBase
     with AnyFunSpecLike
     with ResourceManagerSupport
     with XMLSupport
     with XFormsSupport {

  private implicit val Logger: IndentedLogger = new IndentedLogger(LoggerFactory.createLogger(classOf[SingletonTest]), true)

  private def createSingletonForm(provider: Provider, isSingleton: Boolean, formName: String)(implicit safeRequestCtx: SafeRequestContext): Unit = {
    val formURL = HttpCall.crudURLPrefix(provider, formName) + "form/form.xhtml"
    val form    = HttpCall.XML(
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
        <xh:head>
          <xf:model id="fr-form-model">
            <xf:instance id="fr-form-metadata">
              <metadata>
                <application-name>{provider.entryName}</application-name>
                <form-name>{formName}</form-name>
                {if (isSingleton) <singleton>true</singleton> else null}
              </metadata>
            </xf:instance>
          </xf:model>
        </xh:head>
      </xh:html>.toDocument
    )
    HttpAssert.put(formURL, Unspecified, form, StatusCode.Created)
  }

  describe("Singleton forms") {
    it("second PUT should succeed regular forms, fail for singleton forms") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("singleton document creation") { (_, provider) =>
          val singletonSecondPut = List(
            true  -> StatusCode.Conflict,
            false -> StatusCode.Created
          )
          for ((isSingleton, expectedStatus) <- singletonSecondPut) {
            val formName = s"singleton-$isSingleton"
            createSingletonForm(provider, isSingleton, formName)
            val dataURL1 = HttpCall.crudURLPrefix(provider, formName) + "data/1/data.xml"
            val dataURL2 = HttpCall.crudURLPrefix(provider, formName) + "data/2/data.xml"
            val formData = HttpCall.XML(<form/>.toDocument)
            HttpAssert.put(dataURL1, Specific(1), formData, StatusCode.Created)
            HttpAssert.put(dataURL2, Specific(1), formData, expectedStatus)
          }
        }
      }
    }
  }
}
