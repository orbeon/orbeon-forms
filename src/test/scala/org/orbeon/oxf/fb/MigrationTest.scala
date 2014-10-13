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
import org.orbeon.oxf.test.{TestSupport, DocumentTestBase}
import org.orbeon.oxf.util.{ScalaUtils, XPath}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.scaxon.XML._

class MigrationTest extends DocumentTestBase with FormBuilderSupport with TestSupport with AssertionsForJUnit {

    val MigrationJSON =
        """
          |[
          |  {
          |    "path": "section-3/section-3-iteration/grid-4",
          |    "iteration-name": "grid-4-iteration"
          |  },
          |  {
          |    "path": "section-13/grid-6",
          |    "iteration-name": "grid-6-iteration"
          |  },
          |  {
          |    "path": "section-13/grid-14",
          |    "iteration-name": "grid-14-iteration"
          |  },
          |  {
          |    "path": "section-8/grid-3",
          |    "iteration-name": "my-custom-grid-3-iteration"
          |  },
          |  {
          |    "path": "section-23/grid-3",
          |    "iteration-name": "my-custom-grid-3-iteration"
          |  }
          |]
        """.stripMargin

    @Test def buildGridMigrationMap(): Unit = {

        def readTree(url: String) =
            ScalaUtils.useAndClose(URLFactory.createURL(url).openStream()) { is ⇒
                TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, null, false, false)
            }

        val form    = readTree("oxf:/org/orbeon/oxf/fb/form-to-migrate.xhtml")
        val toolbox = readTree("oxf:/org/orbeon/oxf/fb/form-to-migrate-library.xml")

        val result =
            MigrationOps.buildGridMigrationMap(form, Some(toolbox), legacyGridsOnly = false)

        import spray.json._
        assert(MigrationJSON.asJson === result.asJson)
    }

    val Data47: NodeInfo =
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

    val Data48: NodeInfo =
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

    @Test def migrateDataTo(): Unit =
        assertXMLDocumentsIgnoreNamespacesInScope(
            Data48.root,
            DataMigration.migrateDataTo(Data47.root, MigrationJSON)
        )

    @Test def migrateDataFrom(): Unit =
        assertXMLDocumentsIgnoreNamespacesInScope(
            Data47.root,
            DataMigration.migrateDataFrom(Data48.root, MigrationJSON)
        )

    @Test def roundTripData(): Unit =
        assertXMLDocumentsIgnoreNamespacesInScope(
            Data47.root,
            DataMigration.migrateDataFrom(DataMigration.migrateDataTo(Data47.root, MigrationJSON), MigrationJSON)
        )

    // TODO: annotate.xpl migrations
}
