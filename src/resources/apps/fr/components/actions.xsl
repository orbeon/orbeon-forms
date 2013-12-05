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
<xsl:stylesheet version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:exf="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:p="http://www.orbeon.com/oxf/pipeline">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!--
        Update actions so that the implementation of the actions is up to date. Forms generated with Form Builder
        prior to this change embed a specific implementation of the actions. Forms generated after this change
        do not contain an implementation of the actions and only the parameters are present. See also:

         - annotate.xpl
         - form-to-xbl.xsl
         - https://github.com/orbeon/orbeon-forms/issues/1019
     -->

    <!-- Gather top-level model as well as models within section templates -->
    <xsl:variable
        name="action-models-ids"
        select="$fr-form-model-id, /xh:html/xh:head/xbl:xbl/xbl:binding/xbl:implementation/xf:model/generate-id()"/>

    <!-- See also: https://github.com/orbeon/orbeon-forms/issues/1190 -->
    <xsl:function name="fr:instance-path" as="xs:string">
        <xsl:text>(xxf:binding(concat($control-name, '-control')), bind(concat($control-name, '-bind')))[1]</xsl:text>
    </xsl:function>

    <xsl:template match="/xh:html/xh:head//xf:model[generate-id() = $action-models-ids]/xf:action[ends-with(@id, '-binding')]//xf:action[p:has-class('fr-set-service-value-action')]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- Keep parameters but override implementation  -->
            <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'path')]"/>
            <!-- Set value -->
            <xf:setvalue ref="$path" value="{fr:instance-path()}"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/xh:html/xh:head//xf:model[generate-id() = $action-models-ids]/xf:action[ends-with(@id, '-binding')]//xf:action[p:has-class('fr-set-database-service-value-action')]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- Keep parameters but override implementation  -->
            <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'parameter')]"/>
            <!-- Set value and escape single quotes -->
            <xf:setvalue xmlns:sql="http://orbeon.org/oxf/xml/sql"
                         ref="/sql:config/sql:query/sql:param[xs:integer($parameter)]/(@value | @select)[1]"
                         value="concat('''', replace(string({fr:instance-path()}), '''', ''''''), '''')"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/xh:html/xh:head//xf:model[generate-id() = $action-models-ids]/xf:action[ends-with(@id, '-binding')]//xf:action[p:has-class('fr-set-control-value-action')]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- Keep parameters but override implementation  -->
            <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'control-value')]"/>
            <!-- Set value -->
            <xf:setvalue ref="{fr:instance-path()}" value="$control-value"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/xh:html/xh:head//xf:model[generate-id() = $action-models-ids]/xf:action[ends-with(@id, '-binding')]//xf:action[p:has-class('fr-itemset-action')]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- Keep parameters but override implementation  -->
            <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'response-items')]"/>
            <!-- Set itemset -->

            <!-- Find resource container for all languages, as we are not yet able to handle an internationalized
                 service response. See: https://github.com/orbeon/orbeon-forms/issues/691 -->
            <xf:var name="resource-holders"
                    value="xxf:instance('fr-form-resources')/resource/*[name() = $control-name]"/>

            <xf:delete ref="$resource-holders/item"/>

            <!-- First language -->
            <!-- PERF: Optimize this in a single insert. -->
            <xf:action iterate="$response-items">
                <xf:var name="item-label" value="{.//(*:variable | *:var)[@name = ('item-label')]/@value}"/>
                <xf:var name="item-value" value="{.//(*:variable | *:var)[@name = ('item-value')]/@value}"/>
                <xf:insert
                    context="$resource-holders[1]"
                    ref="*"
                    origin="xxf:element('item', (xxf:element('label', xs:string($item-label)), xxf:element('value', xs:string($item-value))))"/>
            </xf:action>

            <!-- Other languages -->
            <xf:action iterate="$resource-holders[position() gt 1]">
                <xf:insert
                    context="."
                    ref="*"
                    origin="$resource-holders[1]/item"/>
            </xf:action>

            <!-- Filter item values that are out of range -->
            <!-- See: https://github.com/orbeon/orbeon-forms/issues/1019 -->
            <!-- NOTE: We guess whether the control is a select or select1 based on the element name. One exception is
                 autocomplete, which is also a single selection control. -->
            <xf:var name="element-name" value="local-name(xxf:control-element(concat($control-name, '-control')))"/>
            <xf:action if="$element-name = 'select' or ends-with($element-name, '-select')">
                <xf:action iterate="bind(concat($control-name, '-bind'))">
                    <xf:var name="bind" value="."/>
                    <xf:setvalue
                        ref="$bind"
                        value="string-join(xxf:split($bind)[. = $resource-holders[1]/item/value/string()], ' ')"/>
                </xf:action>
            </xf:action>
            <xf:action if="$element-name = ('select1', 'autocomplete') or ends-with($element-name, '-select1')">
                <xf:action iterate="bind(concat($control-name, '-bind'))">
                    <xf:var name="bind" value="."/>
                    <xf:setvalue
                        if="not(string($bind) = $resource-holders[1]/item/value)"
                        ref="$bind"/>
                </xf:action>
            </xf:action>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
