/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import org.orbeon.oxf.fr.DataMigration
import org.orbeon.oxf.fr.DataMigration.{Migration, PathElem}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XMLSupport}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.XML._
import org.scalatest.FunSpecLike

class MigrationTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormBuilderSupport
     with XMLSupport {

  val MigrationJSONWithParens =
    """
      [
        {
          "path": "(section-3)/(section-3-iteration)/(grid-4)",
          "iteration-name": "grid-4-iteration"
        },
        {
          "path": "(section-13)/(grid-6)",
          "iteration-name": "grid-6-iteration"
        },
        {
          "path": "(section-13)/(grid-14)",
          "iteration-name": "grid-14-iteration"
        },
        {
          "path": "(section-8)/(grid-3)",
          "iteration-name": "my-custom-grid-3-iteration"
        },
        {
          "path": "(section-23)/(grid-3)",
          "iteration-name": "my-custom-grid-3-iteration"
        }
      ]
    """

  val MigrationJSON =
    """
      [
        {
          "path": "section-3/section-3-iteration/grid-4",
          "iteration-name": "grid-4-iteration"
        },
        {
          "path": "section-13/grid-6",
          "iteration-name": "grid-6-iteration"
        },
        {
          "path": "section-13/grid-14",
          "iteration-name": "grid-14-iteration"
        },
        {
          "path": "section-8/grid-3",
          "iteration-name": "my-custom-grid-3-iteration"
        },
        {
          "path": "section-23/grid-3",
          "iteration-name": "my-custom-grid-3-iteration"
        }
      ]
    """

  describe("Decoding from JSON") {

    val expected =
      List(
        Migration(List(PathElem("section-3"),  PathElem("section-3-iteration"), PathElem("grid-4")), PathElem("grid-4-iteration")),
        Migration(List(PathElem("section-13"), PathElem("grid-6")),                                  PathElem("grid-6-iteration")),
        Migration(List(PathElem("section-13"), PathElem("grid-14")),                                 PathElem("grid-14-iteration")),
        Migration(List(PathElem("section-8"),  PathElem("grid-3")),                                  PathElem("my-custom-grid-3-iteration")),
        Migration(List(PathElem("section-23"), PathElem("grid-3")),                                  PathElem("my-custom-grid-3-iteration"))
      )

    it("must decode with or without parentheses") {
      for (migrations ← List(MigrationJSONWithParens, MigrationJSON))
        assert(expected === DataMigration.decodeMigrationsFromJSON(migrations))
    }
  }

  describe("Building the grid migration map") {

    val form    = readURLAsImmutableXMLDocument("oxf:/org/orbeon/oxf/fb/form-to-migrate.xhtml")
    val toolbox = readURLAsImmutableXMLDocument("oxf:/org/orbeon/oxf/fb/form-to-migrate-library.xml")

    val result =
      MigrationOps.buildGridMigrationMap(form, Some(toolbox), legacyGridsOnly = false)

    import spray.json._
    it("must encode to the expected JSON") {
      assert(MigrationJSON.parseJson === result.parseJson)
    }
  }

  val DataOrbeonForms40: NodeInfo =
    <form>
      <section-1>
        <control-1/>
      </section-1>
      <section-3>
        <section-3-iteration>
          <control-6/>
          <grid-4>
            <control-7/>
          </grid-4>
          <grid-4>
            <control-7/>
          </grid-4>
          <grid-4>
            <control-7/>
          </grid-4>
        </section-3-iteration>
        <section-3-iteration>
          <control-6/>
          <grid-4>
            <control-7/>
          </grid-4>
          <grid-4>
            <control-7/>
          </grid-4>
          <grid-4>
            <control-7/>
          </grid-4>
        </section-3-iteration>
        <section-3-iteration>
          <control-6/>
        </section-3-iteration>
      </section-3>
      <section-13>
        <grid-6>
          <control-10/>
          <control-11/>
          <control-12/>
          <control-13/>
        </grid-6>
        <grid-6>
          <control-10/>
          <control-11/>
          <control-12/>
          <control-13/>
        </grid-6>
        <grid-6>
          <control-10/>
          <control-11/>
          <control-12/>
          <control-13/>
        </grid-6>
        <grid-14>
          <control-16/>
        </grid-14>
        <grid-14>
          <control-16/>
        </grid-14>
        <grid-14>
          <control-16/>
        </grid-14>
      </section-13>
      <section-8>
        <control-1/>
        <grid-3>
          <control-8/>
          <control-9/>
        </grid-3>
        <grid-3>
          <control-8/>
          <control-9/>
        </grid-3>
        <grid-3>
          <control-8/>
          <control-9/>
        </grid-3>
      </section-8>
      <section-23>
        <control-1/>
        <grid-3>
          <control-8/>
          <control-9/>
        </grid-3>
        <grid-3>
          <control-8/>
          <control-9/>
        </grid-3>
        <grid-3>
          <control-8/>
          <control-9/>
        </grid-3>
      </section-23>
    </form>

  val DataOrbeonForms48: NodeInfo =
    <form>
      <section-1>
        <control-1/>
      </section-1>
      <section-3>
        <section-3-iteration>
          <control-6/>
          <grid-4>
            <grid-4-iteration>
              <control-7/>
            </grid-4-iteration>
            <grid-4-iteration>
              <control-7/>
            </grid-4-iteration>
            <grid-4-iteration>
              <control-7/>
            </grid-4-iteration>
          </grid-4>
        </section-3-iteration>
        <section-3-iteration>
          <control-6/>
          <grid-4>
            <grid-4-iteration>
              <control-7/>
            </grid-4-iteration>
            <grid-4-iteration>
              <control-7/>
            </grid-4-iteration>
            <grid-4-iteration>
              <control-7/>
            </grid-4-iteration>
          </grid-4>
        </section-3-iteration>
        <section-3-iteration>
          <control-6/>
          <grid-4/>
        </section-3-iteration>
      </section-3>
      <section-13>
        <grid-6>
          <grid-6-iteration>
            <control-10/>
            <control-11/>
            <control-12/>
            <control-13/>
          </grid-6-iteration>
          <grid-6-iteration>
            <control-10/>
            <control-11/>
            <control-12/>
            <control-13/>
          </grid-6-iteration>
          <grid-6-iteration>
            <control-10/>
            <control-11/>
            <control-12/>
            <control-13/>
          </grid-6-iteration>
        </grid-6>
        <grid-14>
          <grid-14-iteration>
            <control-16/>
          </grid-14-iteration>
          <grid-14-iteration>
            <control-16/>
          </grid-14-iteration>
          <grid-14-iteration>
            <control-16/>
          </grid-14-iteration>
        </grid-14>
      </section-13>
      <section-8>
        <control-1/>
        <grid-3>
          <my-custom-grid-3-iteration>
            <control-8/>
            <control-9/>
          </my-custom-grid-3-iteration>
          <my-custom-grid-3-iteration>
            <control-8/>
            <control-9/>
          </my-custom-grid-3-iteration>
          <my-custom-grid-3-iteration>
            <control-8/>
            <control-9/>
          </my-custom-grid-3-iteration>
        </grid-3>
      </section-8>
      <section-23>
        <control-1/>
        <grid-3>
          <my-custom-grid-3-iteration>
            <control-8/>
            <control-9/>
          </my-custom-grid-3-iteration>
          <my-custom-grid-3-iteration>
            <control-8/>
            <control-9/>
          </my-custom-grid-3-iteration>
          <my-custom-grid-3-iteration>
            <control-8/>
            <control-9/>
          </my-custom-grid-3-iteration>
        </grid-3>
      </section-23>
    </form>

  val DataOrbeonForms40EmptyIterations: NodeInfo =
    <form>
      <section-1>
        <control-1/>
      </section-1>
      <section-3>
        <section-3-iteration>
          <control-6/>
        </section-3-iteration>
      </section-3>
      <section-13/>
      <section-8>
        <control-1/>
      </section-8>
      <section-23>
        <control-1/>
      </section-23>
    </form>

  val DataOrbeonForms48EmptyIterations: NodeInfo =
    <form>
      <section-1>
        <control-1/>
      </section-1>
      <section-3>
        <section-3-iteration>
          <control-6/>
          <grid-4/>
        </section-3-iteration>
      </section-3>
      <section-13>
        <grid-6/>
        <grid-14/>
      </section-13>
      <section-8>
        <control-1/>
        <grid-3/>
      </section-8>
      <section-23>
        <control-1/>
        <grid-3/>
      </section-23>
    </form>

  describe("Migrating data to newer format") {
    for {
      (desc, from, to) ← List(
        ("from 4.0 format to 4.8 format",                       DataOrbeonForms40               , DataOrbeonForms48),
        ("from 4.0 format to 4.8 format with empty iterations", DataOrbeonForms40EmptyIterations, DataOrbeonForms48EmptyIterations)
      )
    } locally {
      it(s"must pass $desc") {
        assertXMLDocumentsIgnoreNamespacesInScope(
          to.root,
          DataMigration.migrateDataTo(from.root, MigrationJSON)
        )
      }
    }
  }

  describe("Migrating data from newer format") {
    for {
      (desc, from, to) ← List(
        ("from 4.8 format to 4.0 format",                       DataOrbeonForms40               , DataOrbeonForms48),
        ("from 4.8 format to 4.0 format with empty iterations", DataOrbeonForms40EmptyIterations, DataOrbeonForms48EmptyIterations)
      )
    } locally {
      it(s"must pass $desc") {
        assertXMLDocumentsIgnoreNamespacesInScope(
          from.root,
          DataMigration.migrateDataFrom(to.root, MigrationJSON, pruneMetadata = false)
        )
      }
    }
  }

  describe("Migrating data to and from newer format") {
    for {
      (desc, from) ← List(
        ("without empty iterations", DataOrbeonForms40),
        ("with empty iterations",    DataOrbeonForms40EmptyIterations)
      )
    } locally {
      it(s"must pass $desc") {
        assertXMLDocumentsIgnoreNamespacesInScope(
          from.root,
          DataMigration.migrateDataFrom(DataMigration.migrateDataTo(from.root, MigrationJSON), MigrationJSON, pruneMetadata = false)
        )
      }
    }
  }
}
