<!--
  Copyright (C) 2017 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:array="http://www.w3.org/2005/xpath-functions/array"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!-- ======== Persistent migrations of the form definition ======== -->

    <!-- NOTE: Requires`$model` defined by the including stylesheet. -->

    <xsl:variable name="bindings-doc" select="doc('input:bindings')/root()"/>
    <xsl:variable name="bindings"     select="$bindings-doc/*/xbl:xbl/xbl:binding"/>

    <xsl:variable
        xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi"
        name="instance"
        select="fbf:findDataInstanceElem(/)"/>

    <!-- All unneeded help elements AND resources -->
    <xsl:variable
        xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi"
        name="unneeded-elements"
        select="fbf:findBlankHelpHoldersAndElements(/)/generate-id()"/>

    <!-- Migrate constraints, see https://github.com/orbeon/orbeon-forms/issues/1829 -->
    <xsl:variable
        name="ids-of-alert-validations"
        select="//xf:alert/@validation/string()"/>

    <xsl:variable
        name="ids-of-binds-with-constraint-attribute-and-custom-alert"
        select="$model//xf:bind[@constraint and @id = $ids-of-alert-validations]/@id/string()"/>

    <xsl:variable
        xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi"
        name="new-validation-ids"
        select="fbf:nextValidationIds(/, count($ids-of-binds-with-constraint-attribute-and-custom-alert))"/>

    <!-- Only look at templates which are actually for repeats, in case form author manually added
     template instances. -->
    <xsl:variable
        xmlns:migration="java:org.orbeon.oxf.fb.FormBuilderMigrationXPathApi"
        name="template-ids"
        select="
            for $name in migration:findAllRepeatNames(/)
            return $model/xf:instance[@id = concat($name, '-template')]/generate-id()
        "/>

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

    <xsl:template match="/*">
        <xsl:copy>
            <!-- https://github.com/orbeon/orbeon-forms/issues/4508 -->
            <xsl:if test="not(in-scope-prefixes(.) = 'math')">
                <xsl:namespace name="math" select="'http://www.w3.org/2005/xpath-functions/math'"/>
            </xsl:if>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- `nodeset` → `ref` -->
    <xsl:template match="xf:*/@nodeset" mode="#all">
        <xsl:attribute name="ref" select="."/>
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

    <xsl:template match="xf:model/xf:instance[generate-id() = generate-id($instance)]"
                  mode="within-model">
        <xsl:copy>
            <xsl:apply-templates select="@*" mode="#current"/>
            <!-- Enable indexing by id so that itemsets can resolve -->
            <xsl:attribute name="xxf:index" select="'id'"/>
            <xsl:apply-templates select="node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <!--
        Update all templates.

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

            <xsl:variable
                name="new-template"
                select="fbf:createTemplateContentFromBindName(/, frf:controlNameFromId(@id), $bindings)/*[1]"
                xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
                xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilderXPathApi"/>

            <xsl:choose>
                <xsl:when test="exists($new-template/self::*)">
                    <xsl:copy-of select="$new-template"/>
                </xsl:when>
                <xsl:otherwise>
                    <!--
                        Handle further cases where form author manually added template instances, in which case we might not get any
                        elements to copy. So the assumption is that `createTemplateContentFromBindName()` will not return a node
                        containing elements if generating the template fail.
                    -->
                    <xsl:apply-templates select="node()"/>
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

    <!-- Remove unneeded help elements -->
    <xsl:template match="xf:help[generate-id() = $unneeded-elements]"
                  mode="within-body"/>

    <xsl:template match="help[generate-id() = $unneeded-elements]"
                  mode="within-model"/>
    <!-- `origin` → `template` on `fr:grid` and `fr:section` -->
    <xsl:template match="fr:grid/@origin | fr:section/@origin"
                  mode="within-body">
        <xsl:attribute name="template" select="."/>
    </xsl:template>

    <!-- `repeat="true"` → `repeat="content" on `fr:grid` and `fr:section` -->
    <xsl:template match="fr:grid/@repeat[. = 'true'] | fr:section/@repeat[. = 'true']"
                  mode="within-body">
        <xsl:attribute name="repeat" select="'content'"/>
    </xsl:template>

    <!-- `minOccurs` → `min` on `fr:grid` and `fr:section` -->
    <xsl:template match="fr:grid/@minOccurs | fr:section/@minOccurs"
                  mode="within-body">
        <xsl:attribute name="min" select="."/>
    </xsl:template>

    <!-- `maxOccurs` → `max` on `fr:grid` and `fr:section` -->
    <xsl:template match="fr:grid/@maxOccurs | fr:section/@maxOccurs"
                  mode="within-body">
        <xsl:attribute name="max" select="."/>
    </xsl:template>

    <!-- Convert minimal `xf:select1` to `fr:dropdown-select1` -->
    <xsl:template match="xf:select1[@appearance = 'minimal']"
                  mode="within-body">
        <fr:dropdown-select1>
            <xsl:apply-templates select="@* except @appearance | node() except xf:item[xf:value = '']" mode="#current"/>
        </fr:dropdown-select1>
    </xsl:template>

    <!-- Convert `xf:input` of type date to `fr:date` -->
    <xsl:template
        mode="within-body"
        match="
            xf:input[
                exists(
                    for $bind-id in @bind return
                    $model//xf:bind[
                        @id   = $bind-id and
                        @type = ('xf:date', 'xs:date')
                    ]
                )
            ]">
        <fr:date>
            <xsl:apply-templates select="@* | node()" mode="#current"/>
        </fr:date>
    </xsl:template>

    <!-- Convert `xf:input` of type time to `fr:time` -->
    <xsl:template
        mode="within-body"
        match="
            xf:input[
                exists(
                    for $bind-id in @bind return
                    $model//xf:bind[
                        @id   = $bind-id and
                        @type = ('xf:time', 'xs:time')
                    ]
                )
            ]">
        <fr:time>
            <xsl:apply-templates select="@* | node()" mode="#current"/>
        </fr:time>
    </xsl:template>

    <!-- Convert `xf:output[@mediatype = 'image/*']` to `fr:image` -->
    <xsl:template match="xf:output[@mediatype = 'image/*']"
                  mode="within-body">
        <fr:image>
            <xsl:apply-templates select="@* except @mediatype | node()" mode="#current"/>
        </fr:image>
    </xsl:template>

    <!-- Convert Autocomplete to Dynamic Dropdown with Search -->
    <xsl:template match="fr:autocomplete"
                  mode="within-body">
        <fr:databound-select1 appearance="search"
                              service-performs-search="true"
                              service-supports-paging="false"
                              is-last-page="">
            <xsl:apply-templates select="@* except (@appearance, @labelref) | node()" mode="#current"/>
        </fr:databound-select1>
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

    <!-- Remove service instances, comment, and newlines in the middle -->
    <xsl:template match="xf:model/node()[generate-id() = $service-nodes-all-ids]" mode="within-model"/>

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
                            'source-path',
                            'control-value',
                            'path',
                            'parameter-name',
                            'parameter',
                            'response-items',
                            'item-label',
                            'item-value',
                            'item-hint',
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
                                'item-value',
                                'item-hint'
                            )
                        ]
                    else
                        ()"/>

            <xsl:apply-templates
                select="$vars1 | $vars2"
                mode="#current"/>

        </xsl:copy>
    </xsl:template>

    <!-- Migrate email definition to support multiple emails -->
    <!--
    <xsl:template
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
        match="xf:instance[@id = 'fr-form-metadata']/metadata/email[
            frf:isLegacy2021Metadata(.)
        ]"
        mode="within-model">
        <xsl:copy>
           XXX
        </xsl:copy>
    </xsl:template>
     -->

</xsl:stylesheet>