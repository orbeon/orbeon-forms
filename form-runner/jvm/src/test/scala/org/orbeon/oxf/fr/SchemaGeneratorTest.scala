/**
 * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fr.FormRunnerSupport.*
import org.orbeon.oxf.fr.schema.SchemaGenerator
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsStaticStateImpl}
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.saxon.om.DocumentInfo
import org.scalatest.funspec.AnyFunSpecLike

class SchemaGeneratorTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("SchemaGenerator") {

    it("should generate schema for orbeon/pta-remittance form") {

      val (processorService, docOpt, _) = runFormRunner(
        app        = "orbeon",
        form       = "pta-remittance",
        mode       = "edit",
        documentId = Some("aed126ad8341b629f3b19ee7f3e9d4f7c83ebfe5")
      )

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, docOpt.get) {
          val containingDocument = docOpt.get

          // Read the form definition directly from the test resources
          val formPath = "/forms/orbeon/pta-remittance/form/form.xhtml"
          val formInputStream = ResourceManagerWrapper.instance.getContentAsStream(formPath)
          assert(formInputStream != null, s"Form definition not found at $formPath")

          val formOrbeonDom = IOSupport.readOrbeonDom(formInputStream, formPath, org.orbeon.oxf.xml.ParserConfiguration.XIncludeOnly)
          val formSource: DocumentInfo = new DocumentWrapper(
            formOrbeonDom,
            null,
            XPath.GlobalConfiguration
          )

          val schema = SchemaGenerator.createSchema(formSource, containingDocument)

          // For now, we just want to ensure the schema generation runs without error
          assert(schema != null)
        }
      }
    }
  }
}