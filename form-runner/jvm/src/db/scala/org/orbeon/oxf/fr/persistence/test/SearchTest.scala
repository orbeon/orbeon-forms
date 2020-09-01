/**
 * Copyright (C) 2019 Orbeon, Inc.
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
import org.orbeon.oxf.fr.persistence.http.HttpCall
import org.orbeon.oxf.fr.persistence.relational.Version.Specific
import org.orbeon.oxf.http.HttpMethod.POST
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.Converter._
import org.scalatest.funspec.AnyFunSpecLike

class SearchTest
    extends DocumentTestBase
     with XFormsSupport
     with ResourceManagerSupport
     with AnyFunSpecLike {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[SearchTest]), true)

  describe("Search API") {

    it("returns an empty result when there are no documents") {
      withTestExternalContext { implicit externalContext =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val FormURL   = HttpCall.crudURLPrefix  (provider) + "form/form.xhtml"
          val DataURL   = HttpCall.crudURLPrefix  (provider) + "data/123/data.xml"
          val SearchURL = HttpCall.searchURLPrefix(provider)

          val data =
            <form>
              <my-section>
                <my-field>42</my-field>
              </my-section>
            </form>.toDocument

          val searchRequest =
            <search>
                <query/>
                <drafts>include</drafts>
                <page-size>10</page-size>
                <page-number>1</page-number>
                <lang>en</lang>
            </search>.toDocument

          val searchResult =
            <documents search-total="0"/>.toDocument

          HttpCall.assertCall(
            HttpCall.SolicitedRequest(
              path = SearchURL,
              version = Specific(1),
              method = POST,
              body = Some(HttpCall.XML(searchRequest))
            ),
            HttpCall.ExpectedResponse(
              code = 200,
              body = Some(HttpCall.XML(searchResult))
            )
          )
        }
      }
    }
  }
}
