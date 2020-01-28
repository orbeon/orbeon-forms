/**
  * Copyright (C) 2007 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.fr

import org.orbeon.xforms.{$, XFormsId}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpec
import scala.collection.compat._


class ClientFormRunnerApiTest extends AnyFunSpec {

  describe("the `findControlsByName` function") {

    // NOTE: Ideally, the HTML markup would be produced by running the XForms engine. Change this when we can.
    $(dom.document).find("body").append(
      """
        | <form class="xforms-form" id="xforms-form-1">
        |    <span id="fr-view-component≡text-controls-section≡xf-796≡input-control"
        |          class="fr-grid-1-1 xforms-control xforms-input">
        |        <label class="xforms-label"
        |               for="fr-view-component≡text-controls-section≡xf-796≡input-control≡xforms-input-1">Input Field</label>
        |        <input id="fr-view-component≡text-controls-section≡xf-796≡input-control≡xforms-input-1" type="text"
        |               name="fr-view-component≡text-controls-section≡xf-796≡input-control≡xforms-input-1" value="Michelle" class="xforms-input-input">
        |        <span class="xforms-alert"></span>
        |        <span class="xforms-hint">Standard input field</span>
        |    </span>
        |    <span id="fr-view-component≡text-controls-section≡xf-796≡input-counter-control"
        |          class="fr-grid-1-2 xforms-input-appearance-character-counter max-length-30 xbl-component xbl-fr-character-counter">
        |        <label class="xforms-label"
        |               for="fr-view-component≡text-controls-section≡xf-796≡input-counter-control≡field≡xforms-input-1">Input Field with Character Counter</label>
        |        <span class="fr-charcounter-wrapper">
        |            <span id="fr-view-component≡text-controls-section≡xf-796≡input-counter-control≡field"
        |                  class="xforms-control xforms-input xforms-incremental max-length-30">
        |                <input id="fr-view-component≡text-controls-section≡xf-796≡input-counter-control≡field≡xforms-input-1"
        |                       type="text"
        |                       name="fr-view-component≡text-controls-section≡xf-796≡input-counter-control≡field≡xforms-input-1"
        |                       value="This must not be &quot;too long&quot;!"
        |                       class="xforms-input-input">
        |            </span>
        |            <span class="fr-charcounter-count">
        |                <span id="fr-view-component≡text-controls-section≡xf-796≡input-counter-control≡xf-828"
        |                      class="label fr-charcounter-remaining xforms-control xforms-output">
        |                    <span id="fr-view-component≡text-controls-section≡xf-796≡input-counter-control≡xf-828≡≡c"
        |                          class="xforms-output-output">2</span>
        |                </span>
        |            </span>
        |        </span>
        |        <span class="xforms-alert"></span>
        |        <span class="xforms-hint"></span>
        |    </span>
        |    <div id="my-section-control≡my-grid-control" class="xbl-component xbl-fr-grid xforms-visited">
        |      <span id="my-section-control≡my-grid-control≡xf-449" class="xforms-group xforms-visited">
        |        <table class="fr-grid fr-grid-1 fr-grid-my-grid table table-bordered table-condensed fr-repeat fr-repeat-single-row" role="presentation">
        |          <colgroup>
        |            <col id="my-section-control≡my-grid-control≡xf-451" class="fr-repeat-column-left xforms-group xforms-visited">
        |            <col class="fr-grid-col-1">
        |          </colgroup>
        |          <thead class="fr-grid-head">
        |            <tr class="fr-grid-master-row">
        |              <th id="my-section-control≡my-grid-control≡xf-452" class="fr-repeat-column-left xforms-group xforms-visited">
        |                  <span id="my-section-control≡my-grid-control≡fr-grid-add"
        |                        class="xforms-control xforms-trigger xforms-trigger-appearance-minimal xforms-visited">
        |                      <a id="my-section-control≡my-grid-control≡fr-grid-add≡≡c" tabindex="0" role="link" title="Add Another">
        |                          <i class="icon-plus-sign"></i>
        |                      </a>
        |                  </span>
        |                  <span id="my-section-control≡my-grid-control≡xf-454" class="xforms-group xforms-disabled">
        |                      <i class="icon-plus-sign disabled"></i>
        |                  </span>
        |              </th>
        |              <th class="fr-grid-th">
        |                  <span id="my-section-control≡my-grid-control≡fb-lhh-editor-for-name-control"
        |                        class="fr-grid-lhh-for-xforms-input  xforms-control xforms-output xforms-visited">
        |                      <label class="xforms-label" for="my-section-control≡my-grid-control≡fb-lhh-editor-for-name-control≡≡c">Name</label>
        |                      <output id="my-section-control≡my-grid-control≡fb-lhh-editor-for-name-control≡≡c" class="xforms-output-output"></output>
        |                      <span class="xforms-hint"></span>
        |                  </span>
        |              </th>
        |            </tr>
        |          </thead>
        |          <tbody class="fr-grid-body">
        |            <tr id="repeat-begin-my-section-control≡my-grid-control≡my-grid-control-repeat" class="xforms-repeat-begin-end"></tr>
        |            <tr class="xforms-repeat-delimiter"></tr>
        |            <tr class="fr-grid-tr can-insert-above can-insert-below can-remove can-move-down" id="my-section-control≡my-grid-control≡fr-tr⊙1">
        |              <td id="my-section-control≡my-grid-control≡xf-484⊙1" class="fr-repeat-column-left xforms-group xforms-visited">
        |                <div class="dropdown">
        |                    <button class="btn btn-mini fr-grid-dropdown-button"
        |                            aria-label="Menu"
        |                            aria-expanded="false"
        |                            id="my-section-control≡my-grid-control≡xf-485⊙1">
        |                        <span class="caret"></span>
        |                    </button>
        |                </div>
        |              </td>
        |              <td class="fr-grid-td">
        |                  <span id="my-section-control≡my-grid-control≡name-control⊙1" class="fr-grid-1-1 xforms-control xforms-input xforms-visited">
        |                      <label class="xforms-label" for="my-section-control≡my-grid-control≡name-control≡xforms-input-1⊙1">Name</label>
        |                      <input id="my-section-control≡my-grid-control≡name-control≡xforms-input-1⊙1"
        |                             type="text"
        |                             name="my-section-control≡my-grid-control≡name-control≡xforms-input-1⊙1"
        |                             value=""
        |                             class="xforms-input-input">
        |                      <span class="xforms-alert"></span>
        |                      <span class="xforms-hint"></span>
        |                  </span>
        |              </td>
        |            </tr>
        |            <tr class="xforms-repeat-delimiter"></tr>
        |            <tr class="fr-grid-tr can-remove can-move-up can-insert-above can-insert-below xforms-repeat-selected-item-1"
        |                id="my-section-control≡my-grid-control≡fr-tr⊙2">
        |              <td id="my-section-control≡my-grid-control≡xf-484⊙2" class="fr-repeat-column-left xforms-group xforms-visited">
        |                <div class="dropdown">
        |                    <button class="btn btn-mini fr-grid-dropdown-button"
        |                            aria-label="Menu"
        |                            aria-expanded="false"
        |                            id="my-section-control≡my-grid-control≡xf-485⊙2">
        |                        <span class="caret"></span>
        |                    </button>
        |                </div>
        |              </td>
        |              <td class="fr-grid-td">
        |                  <span id="my-section-control≡my-grid-control≡name-control⊙2" class="fr-grid-1-1 xforms-control xforms-input xforms-visited">
        |                      <label class="xforms-label" for="my-section-control≡my-grid-control≡name-control≡xforms-input-1⊙2">Name</label>
        |                      <input id="my-section-control≡my-grid-control≡name-control≡xforms-input-1⊙2"
        |                             type="text"
        |                             name="my-section-control≡my-grid-control≡name-control≡xforms-input-1⊙2"
        |                             value=""
        |                             class="xforms-input-input">
        |                      <span class="xforms-alert"></span>
        |                      <span class="xforms-hint"></span>
        |                  </span>
        |              </td>
        |            </tr>
        |            <tr class="xforms-repeat-delimiter"></tr>
        |            <tr id="repeat-end-my-section-control≡my-grid-control≡my-grid-control-repeat" class="xforms-repeat-begin-end"></tr>
        |          </tbody>
        |        </table>
        |      </span>
        |    </div>
        |</form>
        |<form class="xforms-form" id="xforms-form-2"></form>
      """.stripMargin)

    val form1 = $("#xforms-form-1")(0)
    val form2 = $("#xforms-form-2")(0)

    val controls = List("input", "input-counter", "name")

    for (control <- controls)
      it(s"must find the `$control` control in the first form but not the second form") {
        assert(FormRunnerAPI.findControlsByName(control).nonEmpty)
        assert(FormRunnerAPI.findControlsByName(control, form1.asInstanceOf[html.Form]).nonEmpty)
        assert(FormRunnerAPI.findControlsByName(control, form2.asInstanceOf[html.Form]).isEmpty)
      }

    it("must find the correct iterations for the `name` controls") {
      assert(
        List(List(1), List(2)) === (FormRunnerAPI.findControlsByName("name").to(List) map (e => XFormsId.fromEffectiveId(e.id).iterations))
      )
    }
  }

}
