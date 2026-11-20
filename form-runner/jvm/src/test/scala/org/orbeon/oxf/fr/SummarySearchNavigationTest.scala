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
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.SummarySearchContext.AroundDoc
import org.orbeon.oxf.fr.permission.{AnyOperation, Operation, Operations, SpecificOperations}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.api.PersistenceApi.{SearchNeighbor, SearchNeighbors}
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.scaxon.NodeConversions
import org.scalatest.funspec.AnyFunSpec

import scala.util.Success


class SummarySearchNavigationTest extends AnyFunSpec with ResourceManagerSupport {

  private val appForm = AppForm("acme", "order")
  private val encoded = "e30"

  describe("SummarySearchNavigation.buildNeighborHref") {

    it("keeps edit mode when update is allowed and falls back to view when only read is allowed") {
      val editable = SearchNeighbor("doc-2", isDraft = false, Some(AnyOperation), position = 2)
      assert(
        SummarySearchNavigation.buildNeighborHref(appForm, "edit", editable, encoded)
          .contains("/fr/acme/order/edit/doc-2?fr-search=e30&fr-search-pos=2")
      )

      val readOnly = SearchNeighbor("doc-3", isDraft = false, Some(SpecificOperations(Set(Operation.Read))), position = 3)
      assert(
        SummarySearchNavigation.buildNeighborHref(appForm, "edit", readOnly, encoded)
          .contains("/fr/acme/order/view/doc-3?fr-search=e30&fr-search-pos=3")
      )

      val noAccess = SearchNeighbor("doc-4", isDraft = false, Some(SpecificOperations(Set.empty)), position = 4)
      assert(SummarySearchNavigation.buildNeighborHref(appForm, "edit", noAccess, encoded).isEmpty)
    }

    it("keeps view mode when currently in view mode and includes draft=true for drafts") {
      val draftNeighbor = SearchNeighbor("doc-5", isDraft = true, Some(AnyOperation), position = 5)
      assert(
        SummarySearchNavigation.buildNeighborHref(appForm, "view", draftNeighbor, encoded)
          .contains("/fr/acme/order/view/doc-5?draft=true&fr-search=e30&fr-search-pos=5")
      )
    }
  }

  describe("PersistenceApi.findNeighborsInPagedResponses (custom persistence fallback)") {

    def pageDoc(total: Int, docs: Seq[(String, Boolean)]): org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType =
      NodeConversions.elemToDocumentInfo(
        <documents search-total={total.toString}>{
          docs.map { case (id, draft) =>
            <document name={id} draft={draft.toString} operations="*"/>
          }
        }</documents>
      )

    it("finds neighbors across page boundaries and stops scanning early") {
      var fetchedPages = List.empty[Int]
      val p1 = pageDoc(5, List("d1" -> false, "d2" -> false))
      val p2 = pageDoc(5, List("d3" -> false, "d4" -> false))
      val p3 = pageDoc(5, List("d5" -> false))

      val result = PersistenceApi.findNeighborsInPagedResponses(
        firstPage     = p1,
        around        = AroundDoc("d2", isDraft = false, fallbackPosition = Some(2)),
        fetchNextPage = { p =>
          fetchedPages = fetchedPages :+ p
          Success(if (p == 2) p2 else p3)
        }
      ).get

      assert(result.total == 5)
      assert(result.currentPosition.contains(2))
      assert(result.previous.map(n => (n.documentId, n.position)).contains(("d1", 1)))
      assert(result.next.map(n => (n.documentId, n.position)).contains(("d3", 3)))
      assert(fetchedPages == List(2)) // did not fetch page 3
    }

    it("uses fallbackPosition when the current document is no longer in search results") {
      val p1 = pageDoc(4, List("d1" -> false, "d2" -> false, "d4" -> false, "d5" -> false))

      val result = PersistenceApi.findNeighborsInPagedResponses(
        firstPage     = p1,
        around        = AroundDoc("d3", isDraft = false, fallbackPosition = Some(3)),
        fetchNextPage = _ => fail("Should not fetch another page")
      ).get

      assert(result.total == 4)
      assert(result.currentPosition.isEmpty)
      assert(result.previous.map(n => (n.documentId, n.position)).contains(("d2", 2)))
      assert(result.next.map(n => (n.documentId, n.position)).contains(("d4", 3)))
    }

    it("handles first and last positions cleanly") {
      val p1 = pageDoc(2, List("d1" -> false, "d2" -> false))

      val first = PersistenceApi.findNeighborsInPagedResponses(
        p1, AroundDoc("d1", isDraft = false, None), _ => fail("unexpected")
      ).get
      assert(first.currentPosition.contains(1))
      assert(first.previous.isEmpty)
      assert(first.next.map(_.documentId).contains("d2"))

      val last = PersistenceApi.findNeighborsInPagedResponses(
        p1, AroundDoc("d2", isDraft = false, None), _ => fail("unexpected")
      ).get
      assert(last.currentPosition.contains(2))
      assert(last.previous.map(_.documentId).contains("d1"))
      assert(last.next.isEmpty)
    }
  }
}
