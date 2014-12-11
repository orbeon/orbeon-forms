/**
 *  Copyright (C) 2014 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.client.fb

import org.junit.Test
import org.orbeon.oxf.client.{FormBuilderOps, XFormsOps}
import org.orbeon.oxf.common.Version

trait Services extends FormBuilderOps with XFormsOps {

    val requestBody = """<response>
                        |    <row>
                        |        <value>us</value>
                        |        <lang>en</lang>
                        |        <label>United States</label>
                        |    </row>
                        |    <row>
                        |        <value>us</value>
                        |        <lang>fr</lang>
                        |        <label>États-Unis</label>
                        |    </row>
                        |    <row>
                        |        <value>ch</value>
                        |        <lang>en</lang>
                        |        <label>Switzerland</label>
                        |    </row>
                        |    <row>
                        |        <value>ch</value>
                        |        <lang>fr</lang>
                        |        <label>Suisse</label>
                        |    </row>
                        |</response>""".stripMargin

    @Test def populateDropdownI18n(): Unit = {
        if (Version.isPE) {
            Builder.onNewForm {
                for {
                    // Add dropdown, and open its control details
                    _ ← Builder.insertControl("fr-dropdown-select1")
                    _ ← cssSelector(".fb-body .xbl-fr-dropdown-select1 select").element.moveToElement()
                    _ ← Builder.openControlSettings()

                    // Set dropdown name to "my-dropdown"
                    _ ← Builder.ControlSettings.setControlName("my-dropdown")
                    _ ← Builder.ControlSettings.applySettings()

                    // Fill service dialog
                    _ ← clickOn(cssSelector("#fb-add-service button"))
                    _ ← textField(cssSelector("#fb-service-name-input input")).ensuring(_.isDisplayed).value = "my-service"
                    _ ← textField(cssSelector("#fb-service-resource input")).value = "/fr/service/custom/orbeon/echo"
                    _ ← singleSel(cssSelector("#fb-service-method select")).selectByVisibleText("POST")
                    _ ← textArea(cssSelector("#fb-service-body textarea")).value = requestBody
                    _ ← clickOn(cssSelector("#fb-service-save button").element.ensuring(_.isEnabled))

                    // Fill action dialog
                    _ ← clickOn(cssSelector("#fb-add-action button"))
                    _ ← textField(cssSelector("#fb-action-grid≡fb-binding-name-input input")).ensuring(_.isDisplayed).value = "my-action"
                    _ ← singleSel(cssSelector("#fb-action-grid≡fb-action-react-to select")).selectByVisibleText("Form Load")
                    _ ← singleSel(cssSelector("#fb-action-grid≡fb-bindings-submission-select select")).selectByVisibleText("my-service")
                    _ ← clickOn(cssSelector("#fb-bindings-itemset-repeat≡fr-grid-add a"))
                    _ ← singleSel(cssSelector("#fb-bindings-itemset-repeat≡fb-actions-control⊙1 select")).ensuring(_.isDisplayed).selectByVisibleText("(my-dropdown)")
                    _ ← textField(cssSelector("#fb-bindings-itemset-repeat≡fb-actions-items⊙1 input")).ensuring(_.isDisplayed).value = "/response/row[lang = $fr-lang]"
                    _ ← textField(cssSelector("#fb-bindings-itemset-repeat≡fb-actions-label⊙1 input")).value = "label"
                    _ ← textField(cssSelector("#fb-bindings-itemset-repeat≡fb-actions-value⊙1 input")).value = "value"
                    _ ← clickOn(cssSelector("#fb-actions-save button").element.ensuring(_.isEnabled))

                    // Add French as a language
                    _ ← clickOn(cssSelector("#fb-add-language a"))
                    _ ← singleSel(cssSelector("#fb-add-language-select1 select")).ensuring(_.isDisplayed).selectByVisibleText("Français (French)")
                    _ ← clickOn(cssSelector("#fb-language-add button").element.ensuring(_.isEnabled))

                    // Save, publish
                    _ ← clickOn(cssSelector(".fr-save-button button"))
                    _ ← clickOn(cssSelector(".fr-publish-button button").element.ensuring(_.isEnabled))
                    _ ← clickOn(cssSelector("#fb-publish-publish button").element.ensuring(_.isDisplayed))
                    _ ← clickOn(cssSelector("#fb-publish-close button").element.ensuring(_.isDisplayed))
                }()
            }

            for {
                // Open form and check the dropdown is populated with English labels
                _ ← loadOrbeonPage("/fr/a/a/new")
                myDropdown ← singleSel(cssSelector("[id $= 'my-dropdown-control'] select")).ensuring(_.isDisplayed)
                _ ← assert(myDropdown.labels === List("Please select:", "United States", "Switzerland"))

                // Switch to French, and check the dropdown is populated with French labels
                _ ← clickOn(linkText("Français"))
                _ ← assert(myDropdown.labels === List("Veuillez sélectionner:", "États-Unis", "Suisse"))
            }()
        }
    }
}
