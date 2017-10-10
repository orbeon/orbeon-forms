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
    <p:param type="output" name="data"/>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data"     href="#data"/>
        <p:input name="bindings" href="#bindings"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                xmlns:xh="http://www.w3.org/1999/xhtml"
                xmlns:xf="http://www.w3.org/2002/xforms"
                xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                xmlns:ev="http://www.w3.org/2001/xml-events"
                xmlns:xbl="http://www.w3.org/ns/xbl">

                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
                <xsl:import href="annotate-migrate-to-12-columns.xsl"/>

                <xsl:variable name="model"    select="/*/xh:head/xf:model[@id = 'fr-form-model']"/>
                <xsl:variable name="instance" select="$model/xf:instance [@id = 'fr-form-instance']"/>
                <xsl:variable name="xbl-ids"  select="/*/xh:head/xbl:xbl/generate-id()"/>
                <xsl:variable name="bindings" select="doc('input:bindings')/*/xbl:xbl/xbl:binding"/>

                <!-- Identify the range of nodes which contain the service instances.
                     NOTE: We also try to prune text nodes between the two service instances and one text node after,
                     but that doesn't seem to work -->
                <xsl:variable
                    name="service-instances"
                    select="
                        $model/
                            xf:instance[
                                @id = (
                                    'fr-service-request-instance',
                                    'fr-service-response-instance'
                                )
                            ]"/>

                <xsl:variable
                    name="service-instance-comment"
                    select="
                        $service-instances/
                            preceding-sibling::comment()[
                                normalize-space() = 'Utility instances for services'
                            ]"/>

                <xsl:variable
                    name="service-instance-after"
                    select="$service-instances/following-sibling::node()[1]/self::text()"/>

                <xsl:variable
                    name="service-nodes"
                    select="$service-instance-comment | $service-instances | $service-instance-after"/>

                <xsl:variable
                    name="service-nodes-all"
                    select="
                        $service-nodes[1],
                        (
                            $service-nodes[1]/following-sibling::node() intersect
                            $service-nodes[last()]/preceding-sibling::node()
                        ),
                        $service-nodes[last()]"/>

                <xsl:variable
                    name="service-nodes-all-ids"
                    select="$service-nodes-all/generate-id()"/>

                <!-- Only look at templates which are actually for repeats, in case form author manually added
                     template instances. -->
                <xsl:variable
                    xmlns:migration="java:org.orbeon.oxf.fb.MigrationOps"
                    name="template-ids"
                    select="
                        for $name in migration:findAllRepeatNames(/)
                        return $model/xf:instance[@id = concat($name, '-template')]/generate-id()
                    "/>

                <!-- Whether we have "many" controls -->
                <xsl:variable
                    name="many-controls"
                    select="count(/*/xh:body//*:td[exists(*)]) ge p:property('oxf.fb.section.close')"/>

                <!-- For legacy grid migration -->
                <xsl:variable
                    xmlns:migration="java:org.orbeon.oxf.fb.MigrationOps"
                    name="legacy-grid-binds-templates"
                    select="migration:findLegacyGridBindsAndTemplates(/)/generate-id()"/>

                <!-- All unneeded help elements -->
                <xsl:variable
                    xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder"
                    name="unneeded-elements"
                    select="fbf:findBlankLHHAHoldersAndElements(/, 'help')/generate-id()"/>

                <!-- Migrate constraints, see https://github.com/orbeon/orbeon-forms/issues/1829 -->
                <xsl:variable
                    name="ids-of-alert-validations"
                    select="//xf:alert/@validation/string()"/>

                <xsl:variable
                    name="ids-of-binds-with-constraint-attribute-and-custom-alert"
                    select="$model//xf:bind[@constraint and @id = $ids-of-alert-validations]/@id/string()"/>

                <xsl:variable
                    xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder"
                    name="new-validation-ids"
                    select="fbf:nextIdsXPath(/, 'validation', count($ids-of-binds-with-constraint-attribute-and-custom-alert))"/>

                <!-- Temporarily mark read-only instances as read-write -->
                <xsl:template match="xf:model/xf:instance/@xxf:readonly[. = 'true']" mode="within-model">
                    <xsl:attribute name="fb:readonly" select="'true'"/><!-- so we remember to set the value back -->
                </xsl:template>

                <!-- Update namespace on actions and services so that they don't run at design time -->
                <!-- NOTE: We disable all event handlers below so this is probably not needed anymore, but Form Builder
                     currently (2015-07-06) depends on the fb:* prefixes on the elements. -->
                <xsl:template
                        match="xf:model/xf:*[p:classes() = ('fr-service', 'fr-database-service')] | xf:model/xf:action[ends-with(@id, '-binding')]"
                        mode="within-model">
                    <xsl:element name="fb:{local-name()}">
                        <xsl:apply-templates select="@* | node()" mode="#current"/>
                    </xsl:element>
                </xsl:template>

                <!-- Disable all event handlers at design time... -->
                <xsl:template match="@ev:event | @event" mode="#all">
                    <xsl:attribute name="fb:{local-name()}" select="."/>
                </xsl:template>
                <!--  ...except those under xbl:xbl which must be preserved -->
                <xsl:template
                        match="@event[../@class = 'fr-design-time-preserve']"
                        mode="within-xbl">
                    <xsl:copy-of select="."/>
                </xsl:template>

                <!--
                    fr:view:
                    - copied over along with nested fr:buttons
                    - the XForms engine must ignore foreign elements such as fr:view in the XForms view
                    - we annotate fr:body below
                -->

                <!-- fr:body → xf:group -->
                <xsl:template match="xh:body//fr:body[not(parent::fr:repeat) and not (parent::fr:grid)]">

                    <xf:group id="fb-body" class="fb-body">
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
                            </xf:action>
                        </xf:action>

                        <!-- Listen to changes to grid iterations -->
                        <xf:action
                            event="fr-iteration-added fr-iteration-removed"
                            class="fb-annotation"
                            type="xpath"
                            xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder">
                            fbf:updateTemplatesFromDynamicIterationChange(event('target'))
                        </xf:action>

                    </xf:group>
                </xsl:template>

                <!-- fr:section → fr:section/(@edit-ref, @xxf:update) -->
                <xsl:template match="fr:section" mode="within-body">
                    <xsl:copy>
                        <xsl:attribute name="edit-ref"/>
                        <xsl:attribute name="xxf:update" select="'full'"/>
                        <!-- Save current value of @open as @fb:open -->
                        <xsl:if test="@open"><xsl:attribute name="fb:open" select="@open"/></xsl:if>
                        <!-- If "many" controls close all sections but the first -->
                        <xsl:if test="$many-controls and preceding::fr:section">
                            <xsl:attribute name="open" select="'false'"/>
                        </xsl:if>
                        <xsl:apply-templates select="@* | node()" mode="#current"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Convert MIP names (attributes and nested elements) -->
                <!-- NOTE: We leave custom MIPs as they are. The user must not use fb:* custom MIPs. -->
                <xsl:template match="xf:bind/@relevant
                                   | xf:bind/@readonly
                                   | xf:bind/@constraint
                                   | xf:bind/@calculate
                                   | xf:bind/@xxf:default"
                              mode="within-model">
                    <xsl:attribute name="fb:{local-name()}" select="."/>
                </xsl:template>

                <xsl:template match="xf:bind/xf:relevant
                                   | xf:bind/xf:readonly
                                   | xf:bind/xf:constraint
                                   | xf:bind/xf:calculate
                                   | xf:bind/xxf:default"
                              mode="within-model">
                    <xsl:element name="fb:{local-name()}">
                        <xsl:apply-templates select="@* | node()" mode="#current"/>
                    </xsl:element>
                </xsl:template>

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
                        <xsl:for-each select="('xforms-model-construct', 'xforms-recalculate', 'xforms-revalidate', 'xxforms-xpath-error')">
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

                <!-- Prevent fr:buttons from showing/running -->
                <xsl:template match="fr:buttons">
                    <xf:group class="fr-buttons" ref="()">
                        <xsl:apply-templates select="node()"/>
                    </xf:group>
                </xsl:template>

                <xsl:template match="xbl:xbl[generate-id() = $xbl-ids]">
                    <xsl:copy>
                        <xsl:apply-templates select="@* | node()" mode="within-xbl"/>
                    </xsl:copy>
                </xsl:template>

                <!-- ======== Upgrading form ======== -->

                <!--
                    Remove actions implementations as they are unneeded (and can be out of date). See also:

                    - actions.xsl
                    - https://github.com/orbeon/orbeon-forms/issues/1019
                -->
                <xsl:template
                    match="
                        xf:model/xf:action[
                            ends-with(@id, '-binding')
                        ]//xf:action[
                            true() = (
                                for $c in (
                                    'fr-set-service-value-action',
                                    'fr-set-database-service-value-action',
                                    'fr-set-control-value-action',
                                    'fr-itemset-action',
                                    'fr-save-to-dataset-action'
                                )
                                return
                                    p:has-class($c)
                            )
                        ]"
                    mode="within-model">
                    <xsl:copy>
                        <xsl:apply-templates
                            select="@*"
                            mode="#current"/>

                        <xsl:variable
                            name="vars1"
                            select="
                                (*:variable | *:var)[
                                    @name = (
                                        'control-name',
                                        'control-value',
                                        'path',
                                        'parameter',
                                        'response-items',
                                        'item-label',
                                        'item-value',
                                        'dataset-name'
                                    )
                                ]"/>

                        <!-- These two variables may be nested for forms generated with an older inline implementation of the action -->
                        <xsl:variable
                            name="vars2"
                            select="
                                if (p:has-class('fr-itemset-action')) then
                                    *//(*:variable | *:var)[
                                        @name = (
                                            'item-label',
                                            'item-value'
                                        )
                                    ]
                                else
                                    ()"/>

                        <xsl:apply-templates
                            select="$vars1 | $vars2"
                            mode="#current"/>

                    </xsl:copy>
                </xsl:template>

                <!-- Upgrade "form load" actions from `xforms-ready` to `fr-run-form-load-action-after-controls` -->
                <!-- See https://github.com/orbeon/orbeon-forms/issues/3126 -->
                <xsl:template
                    match="
                        xf:model/xf:action[
                            ends-with(@id, '-binding')
                        ]//xf:action/@*:event[. = 'xforms-ready']"
                    mode="within-model">
                    <xsl:attribute name="fb:{local-name()}" select="'fr-run-form-load-action-after-controls'"/>
                </xsl:template>

                <!-- Saxon serialization adds an extra meta element, make sure to remove it -->
                <xsl:template match="xh:head/meta[@http-equiv = 'Content-Type']"/>

                <!-- Remove unneeded help elements -->
                <xsl:template match="xf:help[generate-id() = $unneeded-elements]"
                              mode="within-body"/>

                <!-- nodeset → ref -->
                <xsl:template match="xf:*/@nodeset" mode="#all">
                    <xsl:attribute name="ref" select="."/>
                </xsl:template>

                <!-- origin → template on fr:grid and fr:section -->
                <xsl:template match="fr:grid/@origin | fr:section/@origin"
                              mode="within-body">
                    <xsl:attribute name="template" select="."/>
                </xsl:template>

                <!-- repeat="true" → repeat="content" on fr:grid and fr:section -->
                <xsl:template match="fr:grid/@repeat[. = 'true'] | fr:section/@repeat[. = 'true']"
                              mode="within-body">
                    <xsl:attribute name="repeat" select="'content'"/>
                </xsl:template>

                <!-- minOccurs → min on fr:grid and fr:section -->
                <xsl:template match="fr:grid/@minOccurs | fr:section/@minOccurs"
                              mode="within-body">
                    <xsl:attribute name="min" select="."/>
                </xsl:template>

                <!-- maxOccurs → max on fr:grid and fr:section -->
                <xsl:template match="fr:grid/@maxOccurs | fr:section/@maxOccurs"
                              mode="within-body">
                    <xsl:attribute name="max" select="."/>
                </xsl:template>

                <!-- Convert minimal xf:select1 to fr:dropdown-select1 -->
                <xsl:template match="xf:select1[@appearance = 'minimal']"
                              mode="within-body">
                    <fr:dropdown-select1>
                        <xsl:apply-templates select="@* except @appearance | node() except xf:item[xf:value = '']" mode="#current"/>
                    </fr:dropdown-select1>
                </xsl:template>

                <!-- Convert xf:output[@mediatype = 'image/*'] to fr:image -->
                <xsl:template match="xf:output[@mediatype = 'image/*']"
                              mode="within-body">
                    <fr:image>
                        <xsl:apply-templates select="@* except @mediatype | node()" mode="#current"/>
                    </fr:image>
                </xsl:template>

                <!-- Convert constraint attributes to nested elements, see https://github.com/orbeon/orbeon-forms/issues/1829  -->
                <xsl:template match="xf:bind[@constraint and @id = $ids-of-binds-with-constraint-attribute-and-custom-alert]"
                              mode="within-model">
                    <xsl:copy>
                        <xsl:apply-templates select="@* except @constraint" mode="#current"/>
                        <xsl:variable name="bind-id" select="@id/string()"/>
                        <xsl:variable name="validation-id" select="$new-validation-ids[index-of($ids-of-binds-with-constraint-attribute-and-custom-alert, $bind-id)]"/>
                        <fb:constraint id="{$validation-id}" value="{@constraint}" level="error"/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="xf:alert[@validation and @validation = $ids-of-binds-with-constraint-attribute-and-custom-alert]"
                              mode="within-body">
                    <xsl:copy>
                        <xsl:apply-templates select="@* except @validation" mode="#current"/>
                        <xsl:variable name="bind-id" select="@validation"/>
                        <xsl:attribute name="validation" select="$new-validation-ids[index-of($ids-of-binds-with-constraint-attribute-and-custom-alert, $bind-id)]"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Remove service instances, comment, and newlines in the middle -->
                <xsl:template match="xf:model/node()[generate-id() = $service-nodes-all-ids]" mode="within-model"/>

                <!-- Migrate grid format -->

                <!-- NOTE: This shouldn't intersect with xf:bind[@constraint ...] above -->
                <xsl:template match="xf:bind[generate-id() = $legacy-grid-binds-templates]"
                              mode="within-model">
                    <xsl:copy>
                        <xsl:apply-templates select="@*" mode="#current"/>
                        <xsl:element name="xf:bind" xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder">
                            <xsl:variable  name="grid-name"      select="fbf:getBindNameOrEmpty(.)"/>
                            <xsl:variable  name="iteration-name" select="fbf:defaultIterationName($grid-name)"/>

                            <xsl:attribute name="id"             select="fbf:bindId($iteration-name)"/>
                            <xsl:attribute name="ref"            select="$iteration-name"/>
                            <xsl:attribute name="name"           select="$iteration-name"/>

                            <xsl:apply-templates select="node()" mode="#current"/>
                        </xsl:element>
                    </xsl:copy>
                </xsl:template>

                <!--
                    Update all templates:

                    - Templates for grids which need migration are wrapped into an element with the default iteration name. We can't
                      just run `createTemplateContentFromName()` because the binds in the input form have not been upgraded yet.
                    - Templates for grids which don't need migration just run `createTemplateContentFromName()`.

                    We recreate all templates because some form definitions might have been migrated with incorrect templates, see:

                        https://github.com/orbeon/orbeon-forms/issues/2440

                    Also ensure `xxf:exclude-result-prefixes` attribute on repeat templates, see:

                        https://github.com/orbeon/orbeon-forms/issues/2278

                    NOTE: In the future, we could have annotate.xpl create all templates, and deannotate.xpl remove all of them.
                -->
                <xsl:template match="xf:model/xf:instance[generate-id() = $template-ids]"
                              mode="within-model">
                    <xsl:copy>
                        <xsl:attribute name="xxf:exclude-result-prefixes" select="'#all'"/>
                        <xsl:apply-templates select="@*" mode="#current"/>

                        <xsl:choose>
                            <xsl:when test="generate-id() = $legacy-grid-binds-templates">
                                <xsl:variable
                                    name="iteration-name"
                                    select="fbf:defaultIterationName(fbf:controlNameFromId(@id))"
                                    xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder"/>
                                <xsl:element name="{$iteration-name}">
                                    <xsl:copy-of
                                        xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder"
                                        select="fbf:createTemplateContentFromBindNameXPath(/, fbf:controlNameFromId(@id), $bindings)/(@*, *)"/>
                                </xsl:element>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:variable
                                    name="new-template"
                                    select="fbf:createTemplateContentFromBindNameXPath(/, fbf:controlNameFromId(@id), $bindings)/*[1]"
                                    xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder"/>

                                <xsl:choose>
                                    <xsl:when test="exists($new-template/self::*)">
                                        <xsl:copy-of select="$new-template"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <!--
                                            Handle further cases where form author manually added template instances, in which case we might not get any
                                            elements to copy. So the assumption is that `createTemplateContentFromBindNameXPath()` will not return a node
                                            containing elements if generating the template fail.
                                        -->
                                        <xsl:apply-templates select="node()"/>
                                    </xsl:otherwise>
                                </xsl:choose>

                            </xsl:otherwise>
                        </xsl:choose>

                    </xsl:copy>
                </xsl:template>

                <!-- Update instance content-->
                <xsl:template match="xf:model/xf:instance[generate-id() = generate-id($instance)]"
                              mode="within-model">
                    <xsl:copy>
                        <xsl:apply-templates select="@*" mode="#current"/>

                        <!-- Enable indexing by id so that itemsets can resolve -->
                        <xsl:attribute name="xxf:index" select="'id'"/>

                        <xsl:variable
                            name="migration"
                            select="migration:buildGridMigrationMap(/, (), true())"
                            xmlns:migration="java:org.orbeon.oxf.fb.MigrationOps"/>

                        <xsl:choose>
                            <xsl:when test="normalize-space($migration)">
                                <!-- Update inline instance using migration map -->
                                <xsl:variable name="instance" as="document-node()">
                                    <xsl:document>
                                        <xsl:copy-of select="*[1]"/>
                                    </xsl:document>
                                </xsl:variable>
                                <xsl:copy-of
                                    select="migration:migrateDataTo($instance, $migration)/*"
                                    xmlns:migration="java:org.orbeon.oxf.fr.DataMigration"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- Leave inline instance unchanged -->
                                <xsl:apply-templates select="node()" mode="#current"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:copy>
                </xsl:template>

                <!-- For a while (not in a release) we supported xf:validation/@* (but not custom MIPs on them) -->
                <xsl:template match="xf:bind/xf:validation[@relevant | @readonly | @constraint | @calculate | @xxf:default]"
                              mode="within-model">

                    <xsl:variable
                        name="validation"
                        select="."/>

                    <xsl:variable
                        name="atts"
                        select="@relevant | @readonly | @constraint | @calculate | @xxf:default"/>

                    <!-- XForms supported more than one attribute, but FB only generated one -->
                    <xsl:for-each select="$atts[1]">
                        <xsl:element name="fb:{local-name()}">
                            <xsl:attribute name="value" select="."/>
                            <xsl:apply-templates select="$validation/@* except $atts" mode="#current"/>
                        </xsl:element>
                    </xsl:for-each>
                </xsl:template>

                <xsl:template match="xf:bind/xf:validation[@type]"
                              mode="within-model">
                    <xsl:element name="xf:type">
                        <xsl:apply-templates select="@* except @type" mode="#current"/>
                        <xsl:value-of select="@type"/>
                    </xsl:element>
                </xsl:template>

                <xsl:template match="xf:bind/xf:validation[@required]"
                              mode="within-model">
                    <xsl:element name="xf:required">
                        <xsl:attribute name="value" select="@required"/>
                        <xsl:apply-templates select="@* except @required" mode="#current"/>
                    </xsl:element>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>