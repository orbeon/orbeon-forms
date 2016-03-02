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

import org.junit.Test
import org.orbeon.oxf.fr.DataMigration
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.test.{XMLSupport, DocumentTestBase}
import org.orbeon.oxf.util.{ScalaUtils, XPath}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.scaxon.XML._

class MigrationTest extends DocumentTestBase with FormBuilderSupport with XMLSupport with AssertionsForJUnit {

  val MigrationJSON =
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

  @Test def buildGridMigrationMap(): Unit = {

    val form    = readURLAsImmutableXMLDocument("oxf:/org/orbeon/oxf/fb/form-to-migrate.xhtml")
    val toolbox = readURLAsImmutableXMLDocument("oxf:/org/orbeon/oxf/fb/form-to-migrate-library.xml")

    val result =
      MigrationOps.buildGridMigrationMap(form, Some(toolbox), legacyGridsOnly = false)

    import spray.json._
    assert(MigrationJSON.parseJson === result.parseJson)
  }

  val DataOrbeonForms47: NodeInfo =
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

  val DataOrbeonForms47EmptyIterations: NodeInfo =
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

  @Test def migrateDataTo(): Unit =
    for {
      (from, to) ← List(
        DataOrbeonForms47                → DataOrbeonForms48,
        DataOrbeonForms47EmptyIterations → DataOrbeonForms48EmptyIterations
      )
    } locally {
      assertXMLDocumentsIgnoreNamespacesInScope(
        to.root,
        DataMigration.migrateDataTo(from.root, MigrationJSON)
      )
    }

  @Test def migrateDataFrom(): Unit =
    for {
      (from, to) ← List(
        DataOrbeonForms47                → DataOrbeonForms48,
        DataOrbeonForms47EmptyIterations → DataOrbeonForms48EmptyIterations
      )
    } locally {
      assertXMLDocumentsIgnoreNamespacesInScope(
        from.root,
        DataMigration.migrateDataFrom(to.root, MigrationJSON, pruneMetadata = false)
      )
    }

  @Test def roundTripData(): Unit =
    for {
      (from, to) ← List(
        DataOrbeonForms47                → DataOrbeonForms48,
        DataOrbeonForms47EmptyIterations → DataOrbeonForms48EmptyIterations
      )
    } locally {
      assertXMLDocumentsIgnoreNamespacesInScope(
        from.root,
        DataMigration.migrateDataFrom(DataMigration.migrateDataTo(from.root, MigrationJSON), MigrationJSON, pruneMetadata = false)
      )
    }
}
