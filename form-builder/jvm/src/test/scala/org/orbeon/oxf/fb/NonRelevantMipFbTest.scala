/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fb

import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames.XXFORMS_NAMESPACE_URI
import org.scalatest.funspec.AnyFunSpecLike


class NonRelevantMipFbTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  private val SectionsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"

  describe("#2760: Form Builder `nonrelevant` MIP read/write/normalize") {

    it("must read, write, and normalize `xxf:nonrelevant` on controls, grids, sections, and root bind") {
      withActionAndFBDoc(SectionsRepeatsDoc) { implicit ctx =>
        for (target <- List(Some("control-1"), Some("grid-1"), Some("section-1"), None)) {
          val nameOrNull = target.orNull

          // Default when omitted
          assert(FormBuilderXPathApi.readDenormalizedCalculatedMip(nameOrNull, "nonrelevant") == "default")

          // Explicit `empty`
          FormBuilderXPathApi.writeAndNormalizeCalculatedMip(nameOrNull, "nonrelevant", "empty")
          assert(FormBuilderXPathApi.readDenormalizedCalculatedMip(nameOrNull, "nonrelevant") == "empty")

          val bindElem = target.flatMap(FormRunner.findBindByName).orElse(ctx.topLevelBindElemOpt).get
          assert(bindElem.attValueOpt(XXFORMS_NAMESPACE_URI -> "nonrelevant").contains("empty"))

          // Explicit `keep` (must remain as attribute so child overrides parent `empty`)
          FormBuilderXPathApi.writeAndNormalizeCalculatedMip(nameOrNull, "nonrelevant", "keep")
          assert(FormBuilderXPathApi.readDenormalizedCalculatedMip(nameOrNull, "nonrelevant") == "keep")
          assert(bindElem.attValueOpt(XXFORMS_NAMESPACE_URI -> "nonrelevant").contains("keep"))

          // Reset to `default` removes attribute
          FormBuilderXPathApi.writeAndNormalizeCalculatedMip(nameOrNull, "nonrelevant", "default")
          assert(FormBuilderXPathApi.readDenormalizedCalculatedMip(nameOrNull, "nonrelevant") == "default")
          assert(bindElem.attValueOpt(XXFORMS_NAMESPACE_URI -> "nonrelevant").isEmpty)
        }
      }
    }
  }
}
