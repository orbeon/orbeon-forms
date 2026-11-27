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
package org.orbeon.fr

import org.orbeon.fr.DropIndicatorDnD.*
import org.orbeon.xforms.CleanDocumentBody
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike


class DropIndicatorDnDTest extends AnyFunSpecLike with CleanDocumentBody {

  describe("DropIndicatorDnD.computeLinearDropTargetsFromRects") {

    it("computes boundaries, midpoints, and noop positions for movable items") {
      val rects = Vector(
        (100.0, 140.0), // item 0
        (146.0, 186.0), // item 1
        (192.0, 232.0)  // item 2
      )

      // Dragging item 2 (last item)
      val targets = computeLinearDropTargetsFromRects(
        containerLeft      = 20.0,
        containerWidth     = 400.0,
        itemTopsAndBottoms = rects,
        movableIndices     = Vector(0, 1, 2),
        draggedIndex       = 2
      )

      assert(targets == List(
        LinearDropTarget(boundaryY = 100.0, left = 20.0, width = 400.0, targetIndexOpt = Some(0)),
        LinearDropTarget(boundaryY = 143.0, left = 20.0, width = 400.0, targetIndexOpt = Some(1)),
        LinearDropTarget(boundaryY = 189.0, left = 20.0, width = 400.0, targetIndexOpt = None),
        LinearDropTarget(boundaryY = 232.0, left = 20.0, width = 400.0, targetIndexOpt = None)
      ))
    }

    it("respects frozen leading items by excluding boundaries above the first movable item") {
      val rects = Vector(
        (100.0, 140.0), // item 0 (frozen)
        (146.0, 186.0), // item 1 (movable)
        (192.0, 232.0)  // item 2 (movable)
      )

      val targets = computeLinearDropTargetsFromRects(
        containerLeft      = 10.0,
        containerWidth     = 300.0,
        itemTopsAndBottoms = rects,
        movableIndices     = Vector(1, 2),
        draggedIndex       = 1
      )

      assert(targets.map(t => (t.boundaryY, t.targetIndexOpt)) == List(
        (146.0, None),
        (189.0, None),
        (232.0, Some(2))
      ))
    }

    it("returns Nil when fewer than two items are movable") {
      val rects = Vector((100.0, 140.0), (146.0, 186.0))
      assert(computeLinearDropTargetsFromRects(0.0, 200.0, rects, Vector(1), draggedIndex = 1).isEmpty)
    }
  }

  describe("DropIndicatorDnD tree helpers") {

    case class Node(id: String, parentIdOpt: Option[String])

    val nodes = List(
      Node("a", None),
      Node("a1", Some("a")),
      Node("a1x", Some("a1")),
      Node("b", None)
    )
    val parentById = nodes.map(n => n.id -> n.parentIdOpt).toMap
    val parentOf   = (id: String) => parentById.getOrElse(id, None)

    it("identifies descendants and last descendant row") {
      assert(isDescendantOrSelf("a1x", "a", parentOf))
      assert(isDescendantOrSelf("a", "a", parentOf))
      assert(! isDescendantOrSelf("b", "a", parentOf))
      assert(lastDescendantOrSelf(nodes.head, nodes, (n: Node) => n.id, parentOf) == Node("a1x", Some("a1")))
    }

    it("spreads clustered boundary anchors without overlapping the next cluster") {
      case class Target(id: String, boundaryY: Double, anchorY: Double)

      val raw = List(
        Target("inner", 200.0, 200.0),
        Target("outer", 201.0, 201.0),
        Target("next",  214.0, 214.0)
      )

      val spread = spreadClusterAnchors[Target](
        sorted       = raw,
        boundaryY    = _.boundaryY,
        withAnchorY  = (t, y) => t.copy(anchorY = y),
        clusterGapPx = 10.0,
        maxStepPx    = 7.0
      )

      // Next cluster is at 214.0 -> maxSpan = 214 - 8 - 200 = 6.0 -> step capped at 6.0
      assert(spread == List(
        Target("inner", 200.0, 200.0),
        Target("outer", 201.0, 206.0),
        Target("next",  214.0, 214.0)
      ))
    }
  }

  describe("DropIndicatorDnD DOM helpers") {

    it("strips duplicate IDs and preserves form control state when cloning a mirror") {
      val host = dom.document.createElement("div").asInstanceOf[html.Element]
      host.id = "row-1"
      host.innerHTML =
        """<input id="inp-1" type="text"/><input id="chk-1" type="checkbox"/><select id="sel-1"><option value="a">A</option><option value="b">B</option></select>"""

      val origInp = host.querySelector("#inp-1").asInstanceOf[html.Input]
      val origChk = host.querySelector("#chk-1").asInstanceOf[html.Input]
      val origSel = host.querySelector("#sel-1").asInstanceOf[html.Select]
      origInp.value = "Typed text"
      origChk.checked = true
      origSel.selectedIndex = 1

      val clone = cloneElementForMirror(host)
      assert(! clone.hasAttribute("id"))
      assert(clone.querySelectorAll("[id]").length == 0)

      val cloneInputs = clone.querySelectorAll("input")
      assert(cloneInputs(0).asInstanceOf[html.Input].value == "Typed text")
      assert(cloneInputs(1).asInstanceOf[html.Input].checked)
      assert(clone.querySelector("select").asInstanceOf[html.Select].selectedIndex == 1)
    }

    it("resolves modal dialog as overlay host when inside a dialog") {
      val root   = dom.document.createElement("div").asInstanceOf[html.Element]
      root.className = "orbeon"
      val dialog = dom.document.createElement("dialog").asInstanceOf[html.Element]
      val inner  = dom.document.createElement("div").asInstanceOf[html.Element]
      dialog.appendChild(inner)
      root.appendChild(dialog)
      dom.document.body.appendChild(root)

      assert(overlayHostEl(inner) eq dialog)
      assert(overlayHostEl(root) eq root)
    }
  }
}
