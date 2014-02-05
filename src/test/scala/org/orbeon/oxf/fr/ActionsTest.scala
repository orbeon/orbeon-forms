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

import org.orbeon.oxf.test.DocumentTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.dom4j.{Document ⇒ JDocument}
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.value.Value

class ActionsTest extends DocumentTestBase with AssertionsForJUnit {

    def source: JDocument =
        <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
                 xmlns:xs="http://www.w3.org/2001/XMLSchema"
                 xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                 xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                 xmlns:xf="http://www.w3.org/2002/xforms"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <xh:head>
                <xf:model id="fr-form-model" xxf:expose-xpath-types="true">
                    <xf:instance id="fr-form-instance">
                        <form>
                            <section-1>
                                <button/>
                                <result>42</result>
                                <grid>
                                    <number>1</number>
                                    <other>o1</other>
                                </grid>
                                <grid>
                                    <number>2</number>
                                    <other>o2</other>
                                </grid>
                                <grid>
                                    <number>3</number>
                                    <other>o3</other>
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
                            </section-1>
                        </form>
                    </xf:instance>
                    <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                        <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                            <xf:bind id="button-bind" ref="button" name="button"/>
                            <xf:bind id="result-bind" ref="result" name="result"/>
                            <xf:bind id="grid-bind" ref="grid" name="grid">
                                <xf:bind id="number-bind" ref="number" name="number"/>
                                <xf:bind id="other-bind" ref="other" name="other"/>
                            </xf:bind>
                            <xf:bind id="hidden-grid-bind" ref="hidden-grid" name="hidden-grid" relevant="false()">
                                <xf:bind id="hidden-number-bind" ref="hidden-number" name="hidden-number"/>
                                <xf:bind id="hidden-other-bind" ref="hidden-other" name="hidden-other"/>
                            </xf:bind>
                        </xf:bind>
                    </xf:bind>
                    <xf:instance id="fr-form-resources" xxf:readonly="false">
                        <resources>
                            <resource xml:lang="en"/>
                        </resources>
                    </xf:instance>
                    <xf:instance xxf:readonly="true" id="grid-template">
                        <grid>
                            <number/>
                        </grid>
                    </xf:instance>
                </xf:model>
            </xh:head>
            <xh:body>
                <fr:view>
                    <fr:body>
                        <fr:section id="section-1-control" bind="section-1-bind">
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
                            <fr:grid id="grid-control" repeat="true" bind="grid-bind"
                                     template="instance('grid-template')"
                                     min="1">
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
        </xh:html>

    def resolveAllNodeValues(actionSourceAbsoluteId: String, targetControlName: String) =
        FormRunner.resolveTargetRelativeToActionSource(actionSourceAbsoluteId, targetControlName) match {
            case value: Value   ⇒ asScalaIterator(value.iterate()) map (_.getStringValue) toList
            case node: NodeInfo ⇒ List(node.getStringValue)
        }

    @Test def resolveTarget(): Unit =
        withActionAndDoc(setupDocument(source)) {

            val model = resolveModel("fr-form-model").get

            XPath.withFunctionContext(XFormsFunction.Context(containingDocument, null, model.getId, model, null)) {

                // 1. Resolution via concrete controls

                val buttonControl = resolveControl("button-control").get
                val resultControl = resolveValueControl("result-control").get

                val numberControls = {

                    val result =
                        for (index ← 1 to 3)
                        yield {
                            setindex("grid-control-repeat", index)
                            resolveValueControl("number-control").get
                        }

                    setindex("grid-control-repeat", 1)

                    result
                }

                // Resolve from buttonControl
                assert(List(resultControl.getValue) === resolveAllNodeValues(buttonControl.absoluteId, "result"))

                for (index ← 1 to 3) {
                    setindex("grid-control-repeat", index)
                    assert(List(index.toString) === resolveAllNodeValues(buttonControl.absoluteId, "number"))
                }

                // Resolve from numberControls
                for (numberControl ← numberControls) {
                    assert(List(s"o${numberControl.getValue}") === resolveAllNodeValues(numberControl.absoluteId, "other"))
                    assert(List(resultControl.getValue)        === resolveAllNodeValues(numberControl.absoluteId, "result"))
                }

                // 2. Resolution via binds

                assert((4 to 6 map (_.toString)) === resolveAllNodeValues(buttonControl.absoluteId, "hidden-number"))
            }
        }
}
