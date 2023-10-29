/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl}
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.xforms.Constants

import scala.collection.compat._
import scala.reflect.ClassTag


class ItemsetActionTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  private val Logger = LoggerFactory.createLogger(classOf[ItemsetActionTest])
  private implicit val indentedLogger: IndentedLogger = new IndentedLogger(Logger)

  def resolveObjectAssert[T: ClassTag](
    staticOrAbsoluteId : String,
    sourceEffectiveId  : String       = Constants.DocumentId,
    indexes            : List[Int]    = Nil,
    container          : XBLContainer = document
  ): T =
    resolveObject[T](
      staticOrAbsoluteId, sourceEffectiveId, indexes, container
    ).getOrElse(throw new AssertionError(s"object `$staticOrAbsoluteId` not found`"))

  describe("Form Runner itemset actions") {

    val (processorService, docOpt, _) =
      runFormRunner("tests", "itemset-action", "new", document = "", initialize = true)

    val doc = docOpt.get

    it("must pass setting dependent and non-dependent itemsets and internationalization") {
      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {

          def resolveCityAndZipControls(indexes: List[Int]) = {
            val cityControl = resolveObjectAssert[XFormsComponentControl]("city-control", indexes = indexes)
            val zipControl  = resolveObjectAssert[XFormsComponentControl]("zip-control", cityControl.effectiveId, indexes)

            cityControl -> zipControl
          }

          def assertEmptyRowValues(indexes: List[Int]) = {

            val (cityControl, zipControl) = resolveCityAndZipControls(indexes)

            assert("" === getControlValue(cityControl.effectiveId))
            assert("" === getControlValue(zipControl.effectiveId))
          }

          def assertRowItemsetsContain(indexes: List[Int], cityOpt: Option[String], zipOpt: Option[String]) = {

            val (cityControl, zipControl) = resolveCityAndZipControls(indexes)

            def assertOne(control: XFormsComponentControl, itemValueOpt: Option[String]) = itemValueOpt match {
              case Some(itemValue) =>
                assert(getItemsetSearchNested(control).get.allItemsWithValueIterator(reverse = false) exists (_._2 == Left(itemValue)))
              case None =>
                // The `fr:dropdown` has at least a blank item, so all we can say is that it's not empty
                assert(getItemsetSearchNested(control).get.allItemsIterator.nonEmpty)
            }

            assertOne(cityControl, cityOpt)
            assertOne(zipControl, zipOpt)
          }

          def assertRowValues(indexes: List[Int], cityToSet: String, zipToSet: String) = {
            val (cityControl, zipControl) = resolveCityAndZipControls(indexes)

            assert(cityToSet === getControlValue(cityControl.effectiveId))
            assert(zipToSet  === getControlValue(zipControl.effectiveId))

            assertRowItemsetsContain(indexes, Some(cityToSet), Some(zipToSet))
          }

          def assertItemsetsChange(indexes: List[Int], cityToSet: String, zipToSet: String) = {

            val (cityControl, zipControl) = resolveCityAndZipControls(indexes)

            setControlValueWithEventSearchNested(cityControl.getEffectiveId, cityToSet)
            setControlValueWithEventSearchNested(zipControl.effectiveId, zipToSet)

            assertRowValues(indexes, cityToSet, zipToSet)
          }

          object Counts {

            def attributesValues(name: String) =
              instance("fr-form-instance").get.rootElement descendantOrSelf * att s"*:$name" map (_.stringValue)

            def countAttributes(name: String) = attributesValues(name).size

            def checkWidowsAndOrphans() = {

              val instanceRootElement = instance("fr-form-instance").get.rootElement

              def metadataItemsetIds =
                instanceRootElement child "*:metadata" descendantOrSelf * att "id" map (_.stringValue)

              val uniqueIdsInUse           = FormRunner.itemsetIdsInUse(instanceRootElement)
              val uniqueMetadataItemsetIds = metadataItemsetIds.to(Set)

              assert(uniqueIdsInUse == uniqueMetadataItemsetIds)
            }

            def withAssertNewCountsAndWindowsAndOrphans[T](counts: List[(String, Int)])(thunk: => T): T = {
              val before = counts map { case (name, _) => countAttributes(name) }
              val result = thunk
              val after  = counts map { case (name, _) => countAttributes(name) }
              counts.zip(before.zip(after)) foreach { case ((_, expected), (before, after)) =>
                val newCount = after - before
                assert(expected === newCount)
              }
              checkWidowsAndOrphans()
              result
            }
          }

          import Counts._

          def assertSectionIteration(stateControl: XFormsComponentControl, stateValue: String, expected: List[(List[Int], String, String)]): Unit = {

            val sectionIndex = expected.head._1.head

            // Initial state
            assert("" === getControlValue(stateControl.effectiveId))
            assertEmptyRowValues(List(sectionIndex, 1))
            assertRowItemsetsContain(List(sectionIndex, 1), None, None)

            // Switch to CA
            withAssertNewCountsAndWindowsAndOrphans(List("itemsetid" -> 1, "itemsetmap" -> 1)) {
              setControlValueWithEventSearchNested(stateControl.getEffectiveId, stateValue)
              assert(stateValue === getControlValue(stateControl.effectiveId))
            }

            // Set values and add iterations
            withAssertNewCountsAndWindowsAndOrphans(List("itemsetid" -> (expected.size * 2), "itemsetmap" -> 0)) {
              for ((indexes @ List(sectionIndex, gridIndex), city, zip) <- expected) {

                assertEmptyRowValues(indexes)
                assertItemsetsChange(indexes, city, zip)

                performGridAction(
                  resolveObjectAssert[XFormsControl]("city-zip-grid-control", indexes = indexes take 1),
                  "fr-insert-below"
                )

                val newRowIndexes = sectionIndex :: (gridIndex + 1) :: Nil

                assertEmptyRowValues(newRowIndexes)
                assertRowItemsetsContain(newRowIndexes, Some(city), None)
              }
            }

            for ((indexes, city, zip) <- expected) {
              assertRowValues(indexes, city, zip)
            }

            // Change state
            setControlValueWithEventSearchNested(stateControl.getEffectiveId, "AK")
            assert("AK" === getControlValue(stateControl.effectiveId))

            // Check that all values are cleared on all iterations, and that the city itemsets are updated
            for ((indexes @ List(sectionIndex, gridIndex), _, _) <- expected ) {
              assertEmptyRowValues(indexes)
              assertRowItemsetsContain(indexes, Some("Anchorage"), None)
            }
          }

          withDebug("check initial state") {
            // One top-level map after initial state
            assert(1 === countAttributes("itemsetid"))
            assert(1 === countAttributes("itemsetmap"))
          }

          withDebug("checking section iteration 1") {
            assertSectionIteration(
              stateControl = resolveObjectAssert[XFormsComponentControl]("state-control", indexes = List(1)),
              stateValue   = "CA",
              expected     = List(
                (List(1, 1), "Los Angeles",   "90001"),
                (List(1, 2), "Beverly Hills", "90212"),
                (List(1, 3), "Hermosa Beach", "90254")
              )
            )
          }

          // New section iteration
          withDebug("inserting new section iteration") {
            performSectionAction(
              resolveObjectAssert[XFormsControl]("states-section-control"),
              "fr-insert-below"
            )
          }

          withDebug("inserting new section iteration 2") {
            assertSectionIteration(
              stateControl = resolveObjectAssert[XFormsComponentControl]("state-control", indexes = List(2)),
              stateValue   = "LA",
              expected     = List(
                (List(2, 1), "Metairie",      "70001"),
                (List(2, 2), "Belle Chasse",  "70037"),
                (List(2, 3), "Des Allemands", "70030")
              )
            )
          }

          // Itemset internationalization
          def assertStateItemsetsContain(label: String): Unit =
            withDebug("checking state itemset contains `$label`") {
              for (sectionIndex <- 1 to 2) {
                val stateControl = resolveObjectAssert[XFormsComponentControl]("state-control", indexes = List(sectionIndex))
                assert(getItemsetSearchNested(stateControl).get.allItemsIterator exists (_.label.label == label))
              }
            }

          assertStateItemsetsContain("California")

          // Switch language to French
          setFormRunnerLanguage("fr")

          // Check that state names have switched
          assertStateItemsetsContain("Californie")

          // Remove 2nd iteration
          withAssertNewCountsAndWindowsAndOrphans(List("itemsetid" -> -8, "itemsetmap" -> -1)) {
            withDebug("removing section iteration") {
              performSectionAction(
                resolveObjectAssert[XFormsControl]("states-section-control"),
                "fr-remove"
              )
            }
          }
        }
      }
    }
  }
}
