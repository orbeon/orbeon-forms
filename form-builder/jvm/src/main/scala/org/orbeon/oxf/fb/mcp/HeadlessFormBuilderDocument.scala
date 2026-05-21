package org.orbeon.oxf.fb.mcp


private[mcp] object HeadlessFormBuilderDocument {

  val Xml =
    <xh:html
      xmlns:xh="http://www.w3.org/1999/xhtml"
      xmlns:xf="http://www.w3.org/2002/xforms"
      xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
      xmlns:xbl="http://www.w3.org/ns/xbl"
      xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">
      <xh:head>
        <xf:model
          id="fr-form-model"
          xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
          <xf:instance id="fb-form-instance" xxf:index="id"><_/></xf:instance>
          <xf:instance id="fr-form-instance"><_/></xf:instance>
          <xf:instance id="fr-form-resources" src="oxf:/forms/orbeon/builder/form/resources.xml"
                       xxf:readonly="true" xxf:cache="true" xxf:xinclude="true"/>

          <xf:var name="model"             value="xh:head/xf:model[@id = 'fr-form-model']"/>
          <xf:var name="metadata-instance" value="frf:metadataInstanceRootOpt(instance('fb-form-instance'))"/>
          <xf:var name="resources"         value="frf:resourcesInstanceRootOpt(instance('fb-form-instance'))"/>
          <xf:var name="current-resources" value="$resources/resource[1]"/>

          <xf:instance id="fb-variables">
            <variables>
              <selected-cell/>
              <starting-cell/>
            </variables>
          </xf:instance>
          <xf:var name="variables"     value="instance('fb-variables')"/>
          <xf:var name="selected-cell" value="$variables/selected-cell"/>
          <xf:var name="starting-cell" value="$variables/starting-cell"/>

          <xf:instance id="fb-undo-instance">
            <_>
              <undos/>
              <redos/>
              <undo-trigger/>
              <redo-trigger/>
            </_>
          </xf:instance>
          <xf:var name="undo" value="instance('fb-undo-instance')"/>

          <xf:var name="component-bindings" value="xxf:instance('fb-components-instance')//xbl:binding"/>
        </xf:model>
        <xf:model id="fb-toolbox-model">
          <xf:instance id="fb-components-instance">
            <components/>
          </xf:instance>
        </xf:model>
        <xf:model id="fr-resources-model">
          <xf:var name="fr-form-resources" value="xxf:instance('fr-form-resources')/resource[@xml:lang = 'en']"/>
        </xf:model>
        <xf:model id="fr-persistence-model">
          <xf:instance id="fr-persistence-instance">
            <save xmlns="">
              <message/>
            </save>
          </xf:instance>
        </xf:model>
      </xh:head>
      <xh:body/>
    </xh:html>
}
