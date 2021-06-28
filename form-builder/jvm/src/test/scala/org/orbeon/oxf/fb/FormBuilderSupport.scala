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
package org.orbeon.oxf.fb

import org.orbeon.dom.Document
import org.orbeon.dom.io.XMLWriter
import org.orbeon.oxf.fr.Names
import org.orbeon.oxf.test.{DocumentTestBase, XFormsSupport}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions

trait FormBuilderSupport extends XFormsSupport {

  self: DocumentTestBase =>

  val TemplateDoc = "oxf:/forms/orbeon/builder/form/template.xml"

  // Run the body in the action context of a form which simulates the main Form Builder model
  def withActionAndFBDoc[T](url: String)(body: FormBuilderDocContext => T): T =
    withTestExternalContext { _ =>
      withActionAndFBDoc(formBuilderContainingDocument(url))(body)
    }

  private def formBuilderContainingDocument(url: String) =
    setupDocument(formBuilderDoc(url))

  private def withActionAndFBDoc[T](doc: XFormsContainingDocument)(body: FormBuilderDocContext => T): T =
    withActionAndDoc(doc) {
      body(
        doc.models
        find    (_.getId == Names.FormModel)
        map     FormBuilderDocContext.apply
        orNull
      )
    }

  def prettyPrintElem(elem: NodeInfo): Unit =
    println(TransformerUtils.tinyTreeToDom4j(elem).getRootElement.serializeToString(XMLWriter.PrettyFormat))

  private def formBuilderDoc(url: String): Document =
    NodeConversions.elemToDom4j(
      <xh:html
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">
        <xh:head>
          <xf:model id="fr-form-model">
            <xf:instance id="fb-form-instance"  xxf:index="id"><dummy/></xf:instance>
            <xf:instance id="fr-form-instance"  src={url}/>
            <xf:instance id="fr-form-resources" src="oxf:/forms/orbeon/builder/form/resources.xml"
                   xxf:readonly="true" xxf:cache="true"/>

            <xf:var name="model"             value="xh:head/xf:model[@id = 'fr-form-model']"/>
            <xf:var name="metadata-instance" value="frf:metadataInstanceRootOpt(instance('fb-form-instance'))"/>
            <xf:var name="resources"         value="frf:resourcesInstanceRootOpt(instance('fb-form-instance'))"/>
            <xf:var name="current-resources" value="$resources/resource[1]"/>

            <xf:instance id="fb-variables">
              <variables>
                <selected-cell/>
              </variables>
            </xf:instance>

            <xf:var name="variables"     value="instance('fb-variables')"/>
            <xf:var name="selected-cell" value="$variables/selected-cell"/>

            <xf:instance id="fb-undo-instance">
                <_>
                    <undos/>
                    <redos/>
                    <undo-trigger/>
                    <redo-trigger/>
                </_>
            </xf:instance>
            <xf:var name="undo" value="instance('fb-undo-instance')"/>

            <xf:var
              name="component-bindings"
              value="xxf:instance('fb-components-instance')//xbl:binding"/>

            <xf:action event="xforms-model-construct-done">
              <!-- Load components -->
              <xf:insert
                ref="xxf:instance('fb-components-instance')"
                origin="xxf:call-xpl('oxf:/org/orbeon/oxf/fb/simple-toolbox.xpl', (), (), 'data')"/>

              <xf:var name="temp" value="xxf:create-document()"/>

              <xf:insert
                context="$temp"
                origin="
                  xxf:call-xpl(
                    'oxf:/forms/orbeon/builder/form/annotate.xpl',
                    (
                      'data',
                      'bindings'
                    ),
                    (
                      xxf:call-xpl(
                        'oxf:/forms/orbeon/builder/form/add-template-bindings.xpl',
                        (
                          'data',
                          'bindings',
                          'for-form-builder'
                        ),
                        (
                          instance('fr-form-instance'),
                          xxf:instance('fb-components-instance'),
                          xf:element('for-form-builder', 'false')
                        ),
                        'data'
                      ),
                      xxf:instance('fb-components-instance')
                    ),
                    'data'
                  )"
              />

              <xf:action type="xpath" xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi">
                fbf:initializeGrids($temp),
                fbf:updateSectionTemplateContentHolders($temp)
              </xf:action>

              <xf:insert ref="instance('fb-form-instance')" origin="$temp"/>
            </xf:action>
          </xf:model>
          <xf:model id="fb-toolbox-model">
            <xf:instance id="fb-components-instance">
              <components/>
            </xf:instance>
          </xf:model>
          <xf:model id="fr-resources-model">
            <xf:var name="fr-form-resources" value="xxf:instance('fr-form-resources')/resource[@xml:lang = 'en']"/>
          </xf:model>
        </xh:head>
        <xh:body>
        </xh:body>
      </xh:html>
    )
}
