<!--
  Copyright (C) 2013 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:exf="http://www.exforms.org/exf/1-0"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!--
        Update actions so that the implementation of the actions is up to date. Forms generated with Form Builder
        prior to this change embed a specific implementation of the actions. Forms generated after this change
        do not contain an implementation of the actions and only the parameters are present. See also:

         - annotate.xpl
         - form-to-xbl.xsl
         - https://github.com/orbeon/orbeon-forms/issues/1019
         - https://github.com/orbeon/orbeon-forms/issues/1190
         - https://github.com/orbeon/orbeon-forms/issues/1465
         - https://github.com/orbeon/orbeon-forms/issues/1105
     -->

    <!-- Gather top-level model as well as models within section templates -->
    <xsl:variable
        name="action-models-ids"
        select="$fr-form-model-id, /xh:html/xh:head/xbl:xbl/xbl:binding/xbl:implementation/xf:model/generate-id()"/>

    <!-- Store the absolute id of the source in the request -->
    <xsl:template match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $action-models-ids]/xf:action[ends-with(@id, '-binding')
                ]/
                xf:action[
                    p:split((@event, @ev:event)[1]) = ('xforms-value-changed', 'xforms-enabled', 'DOMActivate', 'xforms-ready', 'xforms-model-construct-done')
                ]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:variable name="action" select="."/>
            <xsl:variable name="var"    select="$action/xf:var[@name = 'iterate-control-name']"/>
            <xsl:choose>
                <xsl:when test="exists($var)">
                    <xsl:apply-templates select="$var"/>
                    <xf:action iterate="frf:findRepeatedControlsForTarget(event('xxf:absolute-targetid'), $iterate-control-name)">
                        <xf:action type="xpath">xxf:set-request-attribute('fr-action-source', string(.))</xf:action>
                        <xsl:apply-templates select="$action/node() except $var"/>
                    </xf:action>
                </xsl:when>
                <xsl:otherwise>
                    <xf:action type="xpath">xxf:set-request-attribute('fr-action-source', event('xxf:absolute-targetid'))</xf:action>
                    <xsl:apply-templates select="$action/node()"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:copy>
    </xsl:template>

    <!-- When setting or getting service values, resolve the target nodes relative to the source id -->
    <xsl:function name="fr:resolve-targets" as="xs:string">
        <xsl:text>frf:resolveTargetRelativeToActionSource(xxf:get-request-attribute('fr-action-source'), $control-name)</xsl:text>
    </xsl:function>

    <xsl:template match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $action-models-ids
                ]/
                xf:action[
                    ends-with(@id, '-binding')
                ]//
                xf:action[
                    p:has-class('fr-set-service-value-action')
                ]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- Keep parameters but override implementation  -->
            <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'path')]"/>
            <!-- Set value -->
            <xf:setvalue ref="$path" value="{fr:resolve-targets()}"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $action-models-ids
                ]/
                xf:action[
                    ends-with(@id, '-binding')
                ]//
                xf:action[
                    p:has-class('fr-set-database-service-value-action')
                ]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- Keep parameters but override implementation  -->
            <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'parameter')]"/>
            <!-- Set value and escape single quotes -->
            <xf:setvalue
                xmlns:sql="http://orbeon.org/oxf/xml/sql"
                ref="/sql:config/sql:query/sql:param[xs:integer($parameter)]/(@value | @select)[1]"
                value="concat('''', replace(string({fr:resolve-targets()}), '''', ''''''), '''')"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $action-models-ids
                ]/
                xf:action[
                    ends-with(@id, '-binding')
                ]//
                xf:action[
                    p:has-class('fr-set-control-value-action')
                ]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- Keep parameters but override implementation  -->
            <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'control-value')]"/>
            <!-- Set values (we choose to set all targets returned) -->
            <xf:setvalue iterate="{fr:resolve-targets()}" ref="." value="$control-value"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $action-models-ids
                ]/
                xf:action[
                    ends-with(@id, '-binding')
                ]//
                xf:action[
                    p:has-class('fr-itemset-action')
                ]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- Keep parameters but override implementation  -->
            <xsl:apply-templates select="(*:variable | *:var)[@name = 'control-name']"/>
            <xsl:variable name="resource-items-value" select="(*:variable | *:var)[@name = 'response-items']/@value"/>

            <!-- Set itemset -->
            <xf:action iterate="xxf:instance('fr-form-resources')/resource">
                <xf:var name="fr-lang" value="@xml:lang"/>
                <xf:var name="response-items" context="instance('fr-service-response-instance')" value="{$resource-items-value}"/>
                <xf:var name="resource-holder" value="*[name() = $control-name]"/>
                <xf:delete ref="$resource-holder/item"/>
                <xf:action iterate="$response-items">
                    <xf:var name="item-label" value="{.//(*:variable | *:var)[@name = ('item-label')]/(@value | @select)[1]}"/>
                    <xf:var name="item-value" value="{.//(*:variable | *:var)[@name = ('item-value')]/(@value | @select)[1]}"/>
                    <xf:insert
                        context="$resource-holder"
                        ref="*"
                        origin="xxf:element('item', (xxf:element('label', xs:string($item-label)), xxf:element('value', xs:string($item-value))))"/>
                </xf:action>
            </xf:action>

            <!-- Filter item values that are out of range -->
            <!-- See: https://github.com/orbeon/orbeon-forms/issues/1019 -->
            <!-- NOTE: We guess whether the control is a select or select1 based on the element name. One exception is
                 autocomplete, which is also a single selection control. -->
            <xf:var name="element-name" value="local-name(xxf:control-element(concat($control-name, '-control')))"/>
            <xf:var name="possible-values"
                    value="xxf:instance('fr-form-resources')/resource[1]/*[name() = $control-name]/item/value/string()"/>
            <xf:action if="frf:isMultipleSelectionControl($element-name)">
                <xf:action iterate="{fr:resolve-targets()}">
                    <xf:var name="bind" value="."/>
                    <xf:setvalue
                        ref="$bind"
                        value="string-join(xxf:split($bind)[. = $possible-values], ' ')"/>
                </xf:action>
            </xf:action>
            <xf:action if="frf:isSingleSelectionControl($element-name) or $element-name = 'autocomplete'">
                <xf:action iterate="{fr:resolve-targets()}">
                    <xf:var name="bind" value="."/>
                    <xf:setvalue
                        if="not(string($bind) = $possible-values)"
                        ref="$bind"/>
                </xf:action>
            </xf:action>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
