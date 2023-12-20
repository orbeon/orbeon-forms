/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.junit.Test
import org.orbeon.dom
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.XFormsModel
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.value.Value
import org.orbeon.scaxon.Implicits._
import org.scalatestplus.junit.AssertionsForJUnit

class ResolutionTest extends DocumentTestBase with AssertionsForJUnit {

  def source: dom.Document =
    <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
         xmlns:xs="http://www.w3.org/2001/XMLSchema"
         xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
         xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
         xmlns:xf="http://www.w3.org/2002/xforms"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <xh:head>
        <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
          <xf:instance id="fr-form-instance">
            <form>
              <section>
                <section-iteration>
                  <button/>
                  <result>42</result>
                  <grid>
                    <grid-iteration>
                      <number>1</number>
                      <other>o1</other>
                    </grid-iteration>
                    <grid-iteration>
                      <number>2</number>
                      <other>o2</other>
                    </grid-iteration>
                    <grid-iteration>
                      <number>3</number>
                      <other>o3</other>
                    </grid-iteration>
                  </grid>
                  <hidden-grid>
                    <hidden-number>4</hidden-number>
                    <hidden-other>o4</hidden-other>
                  </hidden-grid>
                  <hidden-grid>
                    <hidden-number>5</hidden-number>
                    <hidden-other>o5</hidden-other>
                  </hidden-grid>
                  <hidden-grid>
                    <hidden-number>6</hidden-number>
                    <hidden-other>o6</hidden-other>
                  </hidden-grid>
                </section-iteration>
                <section-iteration>
                  <button/>
                  <result>43</result>
                  <grid>
                    <grid-iteration>
                      <number>21</number>
                      <other>o21</other>
                    </grid-iteration>
                    <grid-iteration>
                      <number>22</number>
                      <other>o22</other>
                    </grid-iteration>
                    <grid-iteration>
                      <number>23</number>
                      <other>o23</other>
                    </grid-iteration>
                  </grid>
                  <hidden-grid>
                    <hidden-number>24</hidden-number>
                    <hidden-other>o24</hidden-other>
                  </hidden-grid>
                  <hidden-grid>
                    <hidden-number>25</hidden-number>
                    <hidden-other>o25</hidden-other>
                  </hidden-grid>
                  <hidden-grid>
                    <hidden-number>26</hidden-number>
                    <hidden-other>o26</hidden-other>
                  </hidden-grid>
                </section-iteration>
              </section>
            </form>
          </xf:instance>
          <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
            <xf:bind id="section-bind" name="section" ref="section">
              <xf:bind id="section-iteration-bind" name="section-iteration" ref="section-iteration">
                <xf:bind id="button-bind" ref="button" name="button"/>
                <xf:bind id="result-bind" ref="result" name="result"/>
                <xf:bind id="grid-bind" ref="grid" name="grid">
                  <xf:bind id="grid-iteration-bind" ref="grid-iteration" name="grid-iteration">
                    <xf:bind id="number-bind" ref="number" name="number"/>
                    <xf:bind id="other-bind" ref="other" name="other"/>
                  </xf:bind>
                </xf:bind>
                <xf:bind id="hidden-grid-bind" ref="hidden-grid" name="hidden-grid" relevant="false()">
                  <xf:bind id="hidden-number-bind" ref="hidden-number" name="hidden-number"/>
                  <xf:bind id="hidden-other-bind" ref="hidden-other" name="hidden-other"/>
                </xf:bind>
              </xf:bind>
            </xf:bind>
          </xf:bind>
          <xf:instance id="fr-form-resources" xxf:readonly="false">
            <resources>
              <resource xml:lang="en"/>
            </resources>
          </xf:instance>
          <xf:instance xxf:readonly="true" id="dummy-template">
            <dummy/>
          </xf:instance>
        </xf:model>
      </xh:head>
      <xh:body>
        <fr:view>
          <fr:body>
            <fr:section
              id="section-control"
              repeat="content"
              bind="section-bind"
              template="instance('dummy-template')">
              <fr:grid>
                <xh:tr>
                  <xh:td>
                    <xf:trigger id="button-control" bind="button-bind"/>
                  </xh:td>
                  <xh:td>
                    <xf:input id="result-control" bind="result-bind"/>
                  </xh:td>
                </xh:tr>
              </fr:grid>
              <fr:grid
                  id="grid-control"
                  repeat="content"
                  bind="grid-bind"
                  template="instance('dummy-template')">
                <xh:tr>
                  <xh:td>
                    <xf:input id="number-control" bind="number-bind"/>
                  </xh:td>
                  <xh:td>
                    <xf:input id="other-control" bind="other-bind"/>
                  </xh:td>
                </xh:tr>
              </fr:grid>
              <fr:grid id="hidden-grid-control" bind="hidden-grid-bind">
                <xh:tr>
                  <xh:td>
                    <xf:input id="hidden-number-control" bind="hidden-number-bind"/>
                  </xh:td>
                  <xh:td>
                    <xf:input id="hidden-other-control" bind="hidden-other-bind"/>
                  </xh:td>
                </xh:tr>
              </fr:grid>
            </fr:section>
          </fr:body>
        </fr:view>
      </xh:body>
    </xh:html>.toDocument

  private def resolveAllNodeValues(
    actionSourceAbsoluteId: String,
    targetControlName     : String,
    followIndexes         : Boolean
  ): List[String] =
    FormRunner
      .resolveTargetRelativeToActionSourceOpt(actionSourceAbsoluteId, targetControlName, followIndexes, None)
      .toList
      .flatMap(_.map(_.getStringValue))

  @Test def resolveTarget(): Unit =
    withActionAndDoc(setupDocument(source)) {

      val model = resolveObject[XFormsModel](Names.FormModel).get

      XPath.withFunctionContext(XFormsFunction.Context(inScopeContainingDocument, null, model.getId, Some(model), None)) {

        // 1. Resolution via concrete controls

        val buttonControl = resolveObject[XFormsControl]("button-control").get
        val resultControl = resolveObject[XFormsValueControl]("result-control").get

        val numberControls = {

          val result =
            for (index <- 1 to 3)
            yield {
              setindex("grid-control-repeat", index)
              resolveObject[XFormsValueControl]("number-control").get
            }

          setindex("grid-control-repeat", 1)

          result
        }

        // Resolve from buttonControl
        for (followIndexes <- List(true, false))
          assert(List(resultControl.getValue(EventCollector.Throw)) === resolveAllNodeValues(buttonControl.absoluteId, "result", followIndexes))

        for (index <- 1 to 3) {
          setindex("grid-control-repeat", index)
          assert(List(index.toString) === resolveAllNodeValues(buttonControl.absoluteId, "number", followIndexes = true))
        }

        for (index <- 1 to 3) {
          setindex("grid-control-repeat", index)
          assert(((1 to 3) map (_.toString)) === resolveAllNodeValues(buttonControl.absoluteId, "number", followIndexes = false))
        }

        // Resolve from numberControls
        for {
          followIndexes <- List(true, false)
          numberControl <- numberControls
        } locally {
          assert(List(s"o${numberControl.getValue(EventCollector.Throw)}") === resolveAllNodeValues(numberControl.absoluteId, "other", followIndexes))
          assert(List(resultControl.getValue(EventCollector.Throw))        === resolveAllNodeValues(numberControl.absoluteId, "result", followIndexes))
        }

        // 2. Resolution via binds

        for (followIndexes <- List(true, false))
          assert((4 to 6 map (_.toString)) === resolveAllNodeValues(buttonControl.absoluteId, "hidden-number", followIndexes))
      }
    }
}
