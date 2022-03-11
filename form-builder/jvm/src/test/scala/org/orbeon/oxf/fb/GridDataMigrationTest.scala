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


import org.orbeon.dom.saxon.NodeWrapper
import org.orbeon.oxf.fr.DataFormatVersion
import org.orbeon.oxf.fr.datamigration.MigrationSupport._
import org.orbeon.oxf.fr.datamigration._
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XMLSupport}
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsCrossPlatformSupport.readTinyTreeFromUrl
import org.scalatest.funspec.AnyFunSpecLike

import java.net.URI
import scala.util.{Failure, Success}


class GridDataMigrationTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport
     with XMLSupport {

  lazy val form   : DocumentInfo = readTinyTreeFromUrl(URI.create("oxf:/org/orbeon/oxf/fb/form-to-migrate.xhtml"))
  lazy val toolbox: DocumentInfo = readTinyTreeFromUrl(URI.create("oxf:/org/orbeon/oxf/fb/form-to-migrate-library.xml"))

  val Migration48Json =
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

  val Migration20191Json =
    """
      {
        "migrations": [
          {
            "containerPath": [
              {
                "value": "section-1"
              }
            ],
            "newGridElem": {
              "value": "grid-1"
            },
            "afterElem": null,
            "content": [
              {
                "value": "control-1"
              }
            ],
            "topLevel" : true
          },
          {
            "containerPath": [
              {
                "value": "section-3"
              },
              {
                "value": "section-3-iteration"
              }
            ],
            "newGridElem": {
              "value": "grid-2"
            },
            "afterElem": null,
            "content": [
              {
                "value": "control-6"
              }
            ],
            "topLevel" : true
          },
          {
            "containerPath": [
              {
                "value": "section-8"
              }
            ],
            "newGridElem": {
              "value": "grid-16"
            },
            "afterElem": null,
            "content": [
              {
                "value": "control-1"
              }
            ],
            "topLevel" : false
          },
          {
            "containerPath": [
              {
                "value": "section-23"
              }
            ],
            "newGridElem": {
              "value": "grid-16"
            },
            "afterElem": null,
            "content": [
              {
                "value": "control-1"
              }
            ],
            "topLevel" : false
          },
          {
            "containerPath": [
              {
                "value": "section-24"
              }
            ],
            "newGridElem": {
              "value": "grid-15"
            },
            "afterElem": null,
            "content": [
              {
                "value": "street-number"
              },
              {
                "value": "street-name"
              },
              {
                "value": "apt-suite"
              },
              {
                "value": "city"
              },
              {
                "value": "state"
              },
              {
                "value": "zip"
              }
            ],
            "topLevel" : false
          }
        ]
      }
    """

  describe("Decoding from JSON") {
    it(s"must decode ${DataFormatVersion.V480.entryName} migrations with or without parentheses") {

      val Migration48JsonWithParens =
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

      val expected =
        List(
          Migration48(List(PathElem("section-3"),  PathElem("section-3-iteration"), PathElem("grid-4")), PathElem("grid-4-iteration")),
          Migration48(List(PathElem("section-13"), PathElem("grid-6")),                                  PathElem("grid-6-iteration")),
          Migration48(List(PathElem("section-13"), PathElem("grid-14")),                                 PathElem("grid-14-iteration")),
          Migration48(List(PathElem("section-8"),  PathElem("grid-3")),                                  PathElem("my-custom-grid-3-iteration")),
          Migration48(List(PathElem("section-23"), PathElem("grid-3")),                                  PathElem("my-custom-grid-3-iteration"))
        )

      for (migrations <- List(Migration48JsonWithParens, Migration48Json))
        assert(expected === MigrationOps48.decodeMigrationSetFromJson(migrations).migrations)
    }

    it(s"must decode ${DataFormatVersion.V20191.entryName} migrations") {

      val expected =
        List(
          Migration20191(List(PathElem("section-1")),                                   PathElem("grid-1"),  None, List(PathElem("control-1")), topLevel = true),
          Migration20191(List(PathElem("section-3"), PathElem("section-3-iteration")),  PathElem("grid-2"),  None, List(PathElem("control-6")), topLevel = true),
          Migration20191(List(PathElem("section-8")),                                   PathElem("grid-16"), None, List(PathElem("control-1")), topLevel = false),
          Migration20191(List(PathElem("section-23")),                                  PathElem("grid-16"), None, List(PathElem("control-1")), topLevel = false),
          Migration20191(List(PathElem("section-24")),                                  PathElem("grid-15"), None, List(
            PathElem("street-number"), PathElem("street-name"), PathElem("apt-suite"), PathElem("city"), PathElem("state"), PathElem("zip")), topLevel = false),
        )

      assert(expected === MigrationOps20191.decodeMigrationSetFromJson(Migration20191Json).migrations)
    }
  }

  describe("Building the grid migration map") {

    val expected = List(
      (MigrationOps48,    Migration48Json),
      (MigrationOps20191, Migration20191Json)
    )

    for ((ops, jsonString) <- expected)
      it(s"must encode ${ops.version.entryName} migrations to the expected JSON") {

        val result =
          ops.buildMigrationSet(form, Some(toolbox), legacyGridsOnly = false) map ops.encodeMigrationsToJson

        def parseOrThrow(s: String): io.circe.Json =
          io.circe.parser.parse(s).fold(Failure.apply, Success.apply).getOrElse(throw new IllegalArgumentException(s))

        assert(result map parseOrThrow contains parseOrThrow(jsonString))
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
      <section-24>
        <street-number/>
        <street-name/>
        <apt-suite/>
        <city/>
        <state/>
        <zip/>
      </section-24>
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
      <section-24>
        <street-number/>
        <street-name/>
        <apt-suite/>
        <city/>
        <state/>
        <zip/>
      </section-24>
    </form>

  val DataOrbeonForms20191: NodeInfo =
    <form>
      <section-1>
        <grid-1>
          <control-1/>
        </grid-1>
      </section-1>
      <section-3>
        <section-3-iteration>
          <grid-2>
            <control-6/>
          </grid-2>
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
          <grid-2>
            <control-6/>
          </grid-2>
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
          <grid-2>
            <control-6/>
          </grid-2>
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
        <grid-16>
          <control-1/>
        </grid-16>
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
        <grid-16>
          <control-1/>
        </grid-16>
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
      <section-24>
        <grid-15>
          <street-number/>
          <street-name/>
          <apt-suite/>
          <city/>
          <state/>
          <zip/>
        </grid-15>
      </section-24>
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

  val DataOrbeonForms20191EmptyIterations: NodeInfo =
    <form>
      <section-1>
        <grid-1>
          <control-1/>
        </grid-1>
      </section-1>
      <section-3>
        <section-3-iteration>
          <grid-2>
            <control-6/>
          </grid-2>
          <grid-4/>
        </section-3-iteration>
      </section-3>
      <section-13>
        <grid-6/>
        <grid-14/>
      </section-13>
      <section-8>
        <grid-16>
          <control-1/>
        </grid-16>
        <grid-3/>
      </section-8>
      <section-23>
        <grid-16>
          <control-1/>
        </grid-16>
        <grid-3/>
      </section-23>
    </form>

  describe(s"Migrating data towards edge format and back") {

    def testSetOfData(allData: List[(String, List[(DataFormatVersion, NodeInfo)])], migrateUp: Boolean): Unit =
      allData foreach {
        case (desc, versionAndData @ (_, originData) :: _) =>

          for {
            ((srcVersion, srcData), (dstVersion, dstData)) <- versionAndData.sliding(2) map { case List(a, b) => a -> b }
            mutableSrcData = MigrationSupport.copyDocumentKeepInstanceData(srcData.root).rootElement.asInstanceOf[NodeWrapper]
          } locally {

            MigrationSupport.migrateDataInPlace(
              dataRootElem     = mutableSrcData,
              srcVersion       = srcVersion,
              dstVersion       = dstVersion,
              findMigrationSet =
                new MigrationsFromForm(
                  outerDocument        = form,
                  availableXBLBindings = Some(toolbox),
                  legacyGridsOnly      = false
                )
            )

            it(s"must migrate from ${srcVersion.entryName} to ${dstVersion.entryName}$desc") {
              assertXMLDocumentsIgnoreNamespacesInScope(
                mutableSrcData.root,
                dstData.root
              )
            }
          }
        case _ =>
          throw new IllegalArgumentException
      }

    val testData: List[(String, List[(DataFormatVersion, NodeInfo)])] =
      List(
        (
          "",
          List(
            DataFormatVersion.V400   -> DataOrbeonForms40,
            DataFormatVersion.V480   -> DataOrbeonForms48,
            DataFormatVersion.V20191 -> DataOrbeonForms20191
          )
        ),
        (
          " with empty iterations",
          List(
            DataFormatVersion.V400   -> DataOrbeonForms40EmptyIterations,
            DataFormatVersion.V480   -> DataOrbeonForms48EmptyIterations,
            DataFormatVersion.V20191 -> DataOrbeonForms20191EmptyIterations
          )
        )
      )

    testSetOfData(testData,                                                  migrateUp = true)
    testSetOfData(testData map { case (desc, data) => (desc, data.reverse) }, migrateUp = false)
  }
}
