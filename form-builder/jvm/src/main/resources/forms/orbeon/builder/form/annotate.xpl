<!--
  Copyright (C) 2011 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input"  name="data"/>
    <p:param type="input"  name="bindings"/>
    <p:param type="input"  name="is-readonly-mode"/>
    <p:param type="output" name="data"/>

    <!--
        Fix forms that have a missing `name` attribute for section or grid `<xf:bind>`.
        These are unlikely to be binds that are in the `filter-mips augment-mips` modes.
        https://github.com/orbeon/orbeon-forms/issues/6860
    -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input  name="config">
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xf="http://www.w3.org/2002/xforms">

                <xsl:import href="/oxf/xslt/utils/copy-modes.xsl"/>

                <xsl:variable
                    xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi"
                    name="model-id"
                    select="generate-id(fbf:findModelElem(/))"/>

                <xsl:template match="xf:model[generate-id() = $model-id]">
                    <xsl:copy>
                        <xsl:apply-templates select="@*  | node()" mode="within-model"/>
                    </xsl:copy>
                </xsl:template>

                <xsl:template
                    match="xf:bind[empty(@name) and exists(@id) and exists(@ref) and @id = concat(@ref, '-bind')]"
                    mode="within-model">
                    <xsl:copy>
                        <xsl:apply-templates select="@*" mode="#current"/>
                        <xsl:attribute name="name" select="@ref"/>
                        <xsl:apply-templates select="node()" mode="#current"/>
                    </xsl:copy>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:input name="data" href="#data"/>
        <p:output name="data" id="with-updated-names"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config">
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

                <xsl:import href="/oxf/xslt/utils/copy-modes.xsl"/>

                <xsl:template match="/">
                    <xsl:apply-templates
                        xmlns:migration="java:org.orbeon.oxf.fb.FormBuilderMigrationXPathApi"
                        select="migration:migrateGridsEnclosingElements(p:mutable-document(/))/root()/*"/>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:input name="data" href="#with-updated-names"/>
        <p:output name="data" id="with-native-migrations"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input  name="data"   href="#with-native-migrations"/>
        <p:input  name="config" href="annotate-migrate-to-12-columns.xsl"/>
        <p:output name="data"   id="migrated-to-12-columns"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data"             href="#migrated-to-12-columns"/>
        <p:input name="bindings"         href="#bindings"/>
        <p:input name="is-readonly-mode" href="#is-readonly-mode"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                xmlns:xh="http://www.w3.org/1999/xhtml"
                xmlns:xf="http://www.w3.org/2002/xforms"
                xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                xmlns:xbl="http://www.w3.org/ns/xbl">

                <xsl:import href="/oxf/xslt/utils/copy-modes.xsl"/>
                <xsl:include href="annotate-migrate.xsl"/>
                <xsl:include href="annotate-design-time.xsl"/>

                <!-- NOTE: `annotate-migrate.xsl` relies on `$model`. -->
                <xsl:variable
                    xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi"
                    name="model"
                    select="fbf:findModelElem(/)"/>

                <xsl:variable
                    xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi"
                    name="metadata-root-id"
                    select="fbf:findMetadataInstanceElem(/)/generate-id()"/>

                <!-- Add model actions -->
                <xsl:template match="xf:model[generate-id() = generate-id($model)]">

                    <xsl:copy>
                        <!-- Make sure xxf:custom-mips is not missing otherwise all fb:* MIPs are evaluated -->
                        <xsl:if test="empty(@xxf:custom-mips)">
                            <xsl:attribute name="xxf:custom-mips"/>
                            <xsl:attribute name="fb:added-empty-custom-mips" select="'true'"/>
                        </xsl:if>

                        <xsl:apply-templates
                            select="(@* except (@xxf:custom-mips, @fb:added-empty-custom-mips)) | node()"
                            mode="within-model"/>

                        <!-- Upon model creation, recalculation and revalidation, notify Form Builder -->
                        <xsl:for-each select="('xforms-model-construct', 'xxforms-recalculate-started', 'xxforms-xpath-error')">
                            <xf:action event="{.}" target="#observer" class="fb-annotation">
                                <!-- Upon MIP XPath error cancel the default error behavior (which otherwise can open an
                                     error dialog at inopportune times.) -->
                                <xsl:if test=". = 'xxforms-xpath-error'">
                                    <xsl:attribute name="defaultAction">cancel</xsl:attribute>
                                </xsl:if>
                                <!-- Dispatch custom event to FB model -->
                                <!-- USE OF ABSOLUTE ID -->
                                <xf:dispatch name="fb-{.}" targetid="|fr-form-model|"/>
                            </xf:action>
                        </xsl:for-each>

                        <!-- Dummy variable values so that user XPath expressions find them -->
                        <xf:var name="fr-roles" value="''" class="fb-annotation"/>
                        <xf:var name="fr-mode" value="'edit'" class="fb-annotation"/>

                    </xsl:copy>
                </xsl:template>

                <!-- Remove migration information, which might be present if we load a published form -->
                <xsl:template
                        match="xf:model/xf:instance[@id = 'fr-form-metadata']/metadata/migration"
                        mode="within-model"/>

                <!--
                    fr:view:
                    - copied over along with nested fr:buttons
                    - the XForms engine must ignore foreign elements such as fr:view in the XForms view
                    - we annotate fr:body below
                -->

                <!-- https://github.com/orbeon/orbeon-forms/issues/3450 -->
                <xsl:template match="xh:body/fr:view/xf:label"/>

                <!-- fr:body → xf:group -->
                <xsl:template match="xh:body//fr:body[not(parent::fr:repeat) and not (parent::fr:grid)]">

                    <xf:group id="fb-body" class="fb-body fr-view-appearance-full">
                        <xsl:copy-of select="namespace::*"/>
                        <!-- Scope $lang which is the language of the form being edited -->
                        <xf:var
                            name="lang"
                            value="xxf:get-variable('fr-form-model', 'fb-lang')"
                            as="element()"
                            class="fb-annotation"/>
                        <!-- Scope $form-resources: resources of the form being edited.
                             Use the same logic as in resources-model. In the builder, we don't have a resources-model
                             running for the form being edited, so we duplicate this here. -->
                        <xf:var
                            name="form-resources"
                            value="instance('fr-form-resources')/(resource[@xml:lang = $lang], resource[1])[1]"
                            as="element(resource)?"
                            class="fb-annotation"/>
                        <!-- Scope $fr-resources for Form Runner resources -->
                        <xf:var
                            name="fr-resources"
                            value="xxf:get-variable('fr-resources-model', 'fr-fr-resources')"
                            as="element(resource)?"
                            class="fb-annotation"/>
                        <!-- Scope $fb-resources for Form Builder resources -->
                        <xf:var
                            name="fb-resources"
                            value="xxf:get-variable('fr-resources-model', 'fr-form-resources')"
                            as="element(resource)?"
                            class="fb-annotation"/>

                        <!-- Apply all the content -->
                        <xsl:apply-templates select="node()" mode="within-body"/>

                        <!-- Listen to activations on grid cells -->
                        <xf:action event="DOMActivate" xxf:phantom="true" class="fb-annotation">
                            <xf:var
                                name="control-element"
                                value="xxf:control-element(event('xxf:absolute-targetid'))"/>

                            <xf:action if="xxf:split($control-element/@class) = 'xforms-activable'">
                                <xf:var
                                    name="new-selected-cell-id"
                                    value="
                                        if ($control-element/@xxf:element = 'xh:th') then
                                            (: Q: Is this still working? What is it trying to do exactly? :)
                                            ($control-element/following-sibling::xf:repeat//*[@xxf:element = 'xh:td'])[
                                                count($control-element/preceding-sibling::*[@xxf:element = 'xh:th']) + 1
                                            ]/@id
                                        else
                                            $control-element/@id"/>
                                <xf:setvalue
                                    ref="xxf:get-variable('fr-form-model', 'selected-cell')"
                                    value="$new-selected-cell-id"/>
                                <!-- Manual cell selection sets the starting cell -->
                                <xf:setvalue
                                    ref="xxf:get-variable('fr-form-model', 'starting-cell')"
                                    value="$new-selected-cell-id"/>
                            </xf:action>
                        </xf:action>

                        <!-- Listen to changes to grid iterations -->
                        <xf:action
                            event="fr-iteration-added fr-iteration-removed"
                            class="fb-annotation"
                            type="xpath"
                            xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi">
                            fbf:updateTemplatesFromDynamicIterationChange(event('target'))
                        </xf:action>

                    </xf:group>
                </xsl:template>

                <!-- Saxon serialization adds an extra meta element, make sure to remove it -->
                <xsl:template match="xh:head/meta[@http-equiv = 'Content-Type']"/>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>