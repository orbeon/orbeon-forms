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

import org.orbeon.oxf.fr.SummarySearchContext.*
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpec


class SummarySearchContextTest extends AnyFunSpec with ResourceManagerSupport {

  describe("SummarySearchContext encode/decode") {

    it("encodes an empty/default search compactly and roundtrips") {
      val ctx     = SummarySearchContext()
      val encoded = SummarySearchContext.encode(ctx)
      assert(encoded == "e30") // Base64URL("{}")
      assert(SummarySearchContext.decode(encoded).contains(ctx))
    }

    it("roundtrips a full search context with filters, sort, drafts, operations, version, and lang") {
      val ctx = SummarySearchContext(
        freeText = Some("hello world"),
        controlQueries = List(
          ControlFilter("section-1/first-name", Some("substring"), Some("input"), "Jane"),
          ControlFilter("section-1/country",    Some("exact"),     None,          "US")
        ),
        metadataQueries = List(
          MetadataFilter("created",        "gte",   "2026-01-01T00:00:00Z"),
          MetadataFilter("workflow-stage", "exact", "submitted")
        ),
        sort            = Some(SortSpec("section-1/first-name", isMetadata = false, "asc")),
        drafts          = Some(DraftsSpec("only", forNeverSavedDocument = true)),
        anyOfOperations = Some("update"),
        version         = Some(3),
        lang            = Some("fr")
      )

      val encoded = SummarySearchContext.encode(ctx)
      assert(! encoded.contains('+'))
      assert(! encoded.contains('/'))
      assert(! encoded.contains('='))
      assert(SummarySearchContext.decode(encoded).contains(ctx))
    }

    it("omits default last-modified desc sort during encoding") {
      val ctx = SummarySearchContext(
        sort = Some(SortSpec("last-modified", isMetadata = true, "desc"))
      )
      assert(SummarySearchContext.encode(ctx) == "e30")
      assert(SummarySearchContext.decode("e30").contains(SummarySearchContext()))
    }

    it("returns None for blank or malformed encoded strings") {
      assert(SummarySearchContext.decode("").isEmpty)
      assert(SummarySearchContext.decode("   ").isEmpty)
      assert(SummarySearchContext.decode("a").isEmpty)
      assert(SummarySearchContext.decode("not-valid-json!!!").isEmpty)
    }
  }

  describe("SummarySearchContext.fromSearchInstance") {

    it("extracts only active filters, sort, drafts, operations, version, and lang") {
      val searchInstanceXml = NodeConversions.elemToDocumentInfo(
        <search>
          <query>  acme  </query>
          <query path="section-1/name" match="substring" control="input" summary-search="true">Alice</query>
          <query path="section-1/empty" match="substring" control="input" summary-show="true"/>
          <query metadata="created" match="gte">2026-01-01T00:00:00Z</query>
          <query metadata="created" match="lt"/>
          <query metadata="last-modified" match="gte" sort="asc"/>
          <query metadata="workflow-stage" match="exact">review</query>
          <drafts for-never-saved-document="true">only</drafts>
          <operations any-of="update"/>
          <page-size>10</page-size>
          <page-number>2</page-number>
          <lang>en</lang>
        </search>
      ).rootElement

      val ctx = SummarySearchContext.fromSearchInstance(searchInstanceXml, "2")
      assert(ctx.freeText.contains("acme"))
      assert(ctx.controlQueries == List(ControlFilter("section-1/name", Some("substring"), Some("input"), "Alice")))
      assert(ctx.metadataQueries == List(
        MetadataFilter("created",        "gte",   "2026-01-01T00:00:00Z"),
        MetadataFilter("workflow-stage", "exact", "review")
      ))
      assert(ctx.sort.contains(SortSpec("last-modified", isMetadata = true, "asc")))
      assert(ctx.drafts.contains(DraftsSpec("only", forNeverSavedDocument = true)))
      assert(ctx.anyOfOperations.contains("update"))
      assert(ctx.version.contains(2))
      assert(ctx.searchVersion == SearchVersion.Specific(2))
      assert(ctx.lang.contains("en"))

      val allVersionCtx = SummarySearchContext.fromSearchInstance(searchInstanceXml, "all")
      assert(allVersionCtx.version.isEmpty)
      assert(allVersionCtx.searchVersion == SearchVersion.All)
    }
  }

  describe("SummarySearchContext.toSearchElem") {

    it("produces valid search XML with around-document and standalone sort") {
      val ctx = SummarySearchContext(
        freeText = Some("invoice"),
        controlQueries = List(ControlFilter("s/a", Some("substring"), None, "val")),
        sort = Some(SortSpec("created", isMetadata = true, "asc")),
        lang = Some("en")
      )

      val elem = ctx.toSearchElem(
        aroundDocOpt = Some(AroundDoc("doc-123", isDraft = true, fallbackPosition = Some(5))),
        pageSize     = 100,
        pageNumber   = 1
      )

      assert((elem \ "around-document" \ "@id").text == "doc-123")
      assert((elem \ "around-document" \ "@draft").text == "true")
      assert((elem \ "around-document" \ "@position").text == "5")
      assert((elem \ "page-size").text == "100")
      assert((elem \ "page-number").text == "1")
      assert((elem \ "query").exists(q => (q \ "@metadata").text == "created" && (q \ "@sort").text == "asc"))
      assert((elem \ "query").exists(q => (q \ "@path").text == "s/a" && q.text == "val"))
      assert((elem \ "query").exists(q => (q \ "@path").isEmpty && (q \ "@metadata").isEmpty && q.text == "invoice"))
    }
  }
}
