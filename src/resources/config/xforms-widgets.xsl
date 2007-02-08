<!--
    Copyright (C) 2004 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:widget="http://orbeon.org/oxf/xml/widget"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <xsl:variable name="has-xforms-instance-inspector" as="xs:boolean" select="exists(//widget:xforms-instance-inspector)"/>

    <xsl:template match="@*|node()" priority="-100">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="xhtml:head">
        <xsl:copy>
            <xsl:copy-of select="@* | node()"/>
            <xsl:if test="$has-xforms-instance-inspector">
                <xsl:call-template name="widget:xforms-instance-inspector-model"/>
                <xhtml:script language="Javascript" type="text/javascript">
                    window.onload = sourceInit;
                    window.onresize = sourceResize;

                    function sourceInit() {
                        sourceResize();
                    }

                    function sourceResize() {
                        var divElement = document.getElementById('widgets-xforms-instance-inspector');
                        var height = document.body.clientHeight - findTopPosition(divElement) - 18;
                        divElement.style.height = (height &lt; 100 ? 100 : height) + "px";
                    }

                    function findTopPosition(element) {
                        var curtop = 0;
                        if (element.offsetParent) {
                            while (element.offsetParent) {
                                curtop += element.offsetTop
                                element = element.offsetParent;
                            }
                        } else if (element.y) {
                            curtop += element.y;
                        }
                        return curtop;
                    }
                </xhtml:script>
            </xsl:if>
            <xhtml:link rel="stylesheet" href="/config/theme/xforms-widgets.css" type="text/css"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="widget:tabs">
        <xsl:variable name="tabs-element" select="."/><!-- as="element()"  -->
        <xsl:variable name="tabs" select="widget:tab"/><!-- as="element()*"  -->
        <xhtml:table class="widget-tabs" cellpadding="0" cellspacing="0" border="0">
            <xsl:copy-of select="@*"/>
            <xhtml:tr>
                <xhtml:td class="widget-tab-spacer-side"/>
                <!-- Tabs at the top -->
                <xsl:variable name="selected-tab-specified" select="count(widget:tab[@selected = 'true']) = 1"/><!-- as="xs:boolean"  -->
                <xsl:for-each select="$tabs">
                    <xsl:variable name="tab-id" select="@id"/><!-- as="xs:string"  -->
                    <xsl:if test="position() > 1">
                        <xhtml:td class="widget-tab-spacer-between"/>
                    </xsl:if>
                    <xhtml:td class="widget-tab">
                        <xforms:switch>
                            <xsl:if test="$tabs-element/@ref">
                                <xsl:attribute name="ref" select="$tabs-element/@ref"/>
                            </xsl:if>
                            <!-- Case where this tab is inactive -->
                            <xforms:case id="{$tab-id}-inactive">
                                <xhtml:div class="widget-tab-inactive">
                                    <xforms:trigger appearance="xxforms:link" id="{@id}-trigger">
                                        <xsl:choose>
                                            <xsl:when test="widget:label/@ref">
                                                <xforms:label ref="{widget:label/@ref}"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xforms:label><xsl:value-of select="widget:label"/></xforms:label>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                        <xforms:action ev:event="DOMActivate">
                                            <xforms:toggle case="{$tab-id}"/>
                                            <xforms:toggle case="{$tab-id}-active"/>
                                            <xsl:for-each select="$tabs[@id != $tab-id]">
                                                <xforms:toggle case="{@id}-inactive"/>
                                            </xsl:for-each>
                                        </xforms:action>
                                    </xforms:trigger>
                                </xhtml:div>
                            </xforms:case>
                            <!-- Case where this tab is active -->
                            <xforms:case id="{$tab-id}-active">
                                <xsl:if test="(not($selected-tab-specified) and position() = 1) or @selected = 'true'">
                                    <xsl:attribute name="selected">true</xsl:attribute>
                                </xsl:if>
                                <xhtml:div class="widget-tab-active">
                                    <xsl:choose>
                                        <xsl:when test="widget:label/@ref">
                                            <xforms:output ref="{widget:label/@ref}"/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:value-of select="widget:label"/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xhtml:div>
                            </xforms:case>
                        </xforms:switch>
                    </xhtml:td>
                </xsl:for-each>
                <xhtml:td class="widget-tab-spacer-side"/>
            </xhtml:tr>
            <!-- Main area with the switch -->
            <xhtml:tr>
                <xhtml:td class="widget-tabs-panel" colspan="{count(widget:tab) * 4 + 1}">
                    <xforms:switch>
                        <xsl:if test="$tabs-element/@ref">
                            <xsl:attribute name="ref" select="$tabs-element/@ref"/>
                        </xsl:if>
                        <xsl:for-each select="widget:tab">
                            <xforms:case>
                                <xsl:copy-of select="@*"/>
                                <xhtml:div class="widget-tab-panel">
                                    <xsl:apply-templates select="node()"/>
                                </xhtml:div>
                            </xforms:case>
                        </xsl:for-each>
                    </xforms:switch>
                </xhtml:td>
            </xhtml:tr>
        </xhtml:table>
    </xsl:template>

    <xsl:template match="widget:label"/>

    <xsl:template match="widget:xforms-instance-inspector[$has-xforms-instance-inspector]">

        <xforms:group model="orbeon-xforms-instance-inspector-model" class="widgets-xforms-instance-inspector">

            <xhtml:h2>Orbeon Forms XForms Instance Inspector</xhtml:h2>

            <xhtml:table>

                <xhtml:tr>
                    <xhtml:td>
                        <xforms:group ref=".[count(instance('orbeon-xforms-instance-inspector-itemset')/model) gt 1]">
                            <xforms:select1 ref="instance('orbeon-xforms-instance-inspector-instance')/current-model">
                                <xforms:label>Model:</xforms:label>
                                <xforms:itemset nodeset="instance('orbeon-xforms-instance-inspector-itemset')/model">
                                    <xforms:label ref="@id"/>
                                    <xforms:value ref="@id"/>
                                </xforms:itemset>
                                <xforms:setvalue ev:event="xforms-value-changed" ref="instance('orbeon-xforms-instance-inspector-instance')/current-instance"/>
                            </xforms:select1>
                        </xforms:group>
                        <xforms:group ref=".[count(instance('orbeon-xforms-instance-inspector-itemset')/model) = 1]">
                            <xforms:output value="instance('orbeon-xforms-instance-inspector-instance')/current-model">
                                <xforms:label>Model:</xforms:label>
                            </xforms:output>
                        </xforms:group>
                        <xhtml:span>&#160;</xhtml:span>
                        <xforms:group ref=".[count(instance('orbeon-xforms-instance-inspector-itemset')/model[@id = instance('orbeon-xforms-instance-inspector-instance')/current-model]/instance) gt 1]">
                            <xforms:select1 ref="instance('orbeon-xforms-instance-inspector-instance')/current-instance">
                                <xforms:label>Instance:</xforms:label>
                                <xforms:itemset nodeset="instance('orbeon-xforms-instance-inspector-itemset')/model[@id = instance('orbeon-xforms-instance-inspector-instance')/current-model]/instance">
                                    <xforms:label ref="@id"/>
                                    <xforms:value ref="@id"/>
                                </xforms:itemset>
                            </xforms:select1>
                        </xforms:group>
                        <xforms:group ref=".[count(instance('orbeon-xforms-instance-inspector-itemset')/model[@id = instance('orbeon-xforms-instance-inspector-instance')/current-model]/instance) = 1]">
                            <xforms:output ref="instance('orbeon-xforms-instance-inspector-instance')/current-instance">
                                <xforms:label>Instance:</xforms:label>
                            </xforms:output>
                        </xforms:group>
                        <xforms:select1 appearance="full" ref="instance('orbeon-xforms-instance-inspector-instance')/mode">
                            <xforms:label>Mode:</xforms:label>
                            <xforms:item>
                                <xforms:label>Formatted</xforms:label>
                                <xforms:value>formatted</xforms:value>
                            </xforms:item>
                            <xforms:item>
                                <xforms:label>Plain</xforms:label>
                                <xforms:value>plain</xforms:value>
                            </xforms:item>
                        </xforms:select1>
                    </xhtml:td>
                </xhtml:tr>
            </xhtml:table>

            <xhtml:div class="widgets-xforms-instance-inspector-source" id="widgets-xforms-instance-inspector">
                <xforms:group ref="xxforms:instance(instance('orbeon-xforms-instance-inspector-instance')/current-instance)">
                    <xforms:group ref=".[instance('orbeon-xforms-instance-inspector-instance')/mode = 'formatted']">
                        <xforms:output mediatype="text/html"
                                      value="xxforms:serialize(xxforms:call-xpl('oxf:/ops/utils/formatting/format.xpl', 'data', ., 'data')/*, 'html')"/>
                    </xforms:group>
                    <xforms:group ref=".[instance('orbeon-xforms-instance-inspector-instance')/mode = 'plain']">
                        <xforms:output mediatype="text/html"
                                       value="replace(replace(replace(replace(xxforms:serialize(., 'xml'), '&amp;', '&amp;amp;'), '&lt;', '&amp;lt;'), '&#x0a;', '&lt;br>'), ' ', '&#160;')"/>
                    </xforms:group>
                </xforms:group>
            </xhtml:div>

        </xforms:group>

    </xsl:template>

    <xsl:template name="widget:xforms-instance-inspector-model">
        <xforms:model id="orbeon-xforms-instance-inspector-model">

            <xforms:action ev:event="xforms-ready">
                <!-- Initialize itemset -->
                <xforms:action while="count(instance('orbeon-xforms-instance-inspector-itemset')/model) != count(xxforms:list-models())">
                    <xforms:insert context="instance('orbeon-xforms-instance-inspector-itemset')" nodeset="model"
                                   origin="instance('orbeon-xforms-instance-inspector-model-template')"/>
                    <xforms:setvalue ref="instance('orbeon-xforms-instance-inspector-itemset')/model[last()]/@id"
                                     value="xxforms:list-models()[count(instance('orbeon-xforms-instance-inspector-itemset')/model)]"/>
                    <xforms:action while="count(instance('orbeon-xforms-instance-inspector-itemset')/model[last()]/instance)
                                            != count(xxforms:list-instances(instance('orbeon-xforms-instance-inspector-itemset')/model[last()]/@id))">
                        <xforms:insert context="instance('orbeon-xforms-instance-inspector-itemset')/model[last()]" nodeset="instance"
                                       origin="instance('orbeon-xforms-instance-inspector-instance-template')"/>
                        <xforms:setvalue ref="instance('orbeon-xforms-instance-inspector-itemset')/model[last()]/instance[last()]/@id"
                                         value="xxforms:list-instances(instance('orbeon-xforms-instance-inspector-itemset')/model[last()]/@id)[count(instance('orbeon-xforms-instance-inspector-itemset')/model[last()]/instance)]"/>
                    </xforms:action>
                </xforms:action>
                <xforms:delete context="instance('orbeon-xforms-instance-inspector-itemset')" nodeset="model[starts-with(@id, 'orbeon-')]"/>
                <xforms:setvalue ref="instance('orbeon-xforms-instance-inspector-instance')/current-model" value="instance('orbeon-xforms-instance-inspector-itemset')/model[1]/@id"/>
            </xforms:action>
            <xforms:instance id="orbeon-xforms-instance-inspector-itemset">
                <models xmlns=""/>
            </xforms:instance>
            <xforms:instance id="orbeon-xforms-instance-inspector-model-template" xxforms:readonly="true">
                <model xmlns="" id=""/>
            </xforms:instance>
            <xforms:instance id="orbeon-xforms-instance-inspector-instance-template" xxforms:readonly="true">
                <instance xmlns="" id=""/>
            </xforms:instance>

            <xforms:instance id="orbeon-xforms-instance-inspector-instance">
                <instance xmlns="">
                    <current-model/>
                    <current-instance/>
                    <mode>formatted</mode>
                </instance>
            </xforms:instance>
            <xforms:bind nodeset="instance('orbeon-xforms-instance-inspector-instance')/current-instance" readonly="false()"
                         calculate="if (. = '') then instance('orbeon-xforms-instance-inspector-itemset')/model[@id = instance('orbeon-xforms-instance-inspector-instance')/current-model]/instance[1]/@id else ."/>
        </xforms:model>

    </xsl:template>
    
</xsl:stylesheet>
