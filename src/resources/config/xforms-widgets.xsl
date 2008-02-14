<!--
    Copyright (C) 2004-2007 Orbeon, Inc.

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

    <xsl:variable name="has-widgets" as="xs:boolean" select="exists(//widget:*)"/>
    <xsl:variable name="has-xforms-instance-inspector" as="xs:boolean" select="exists(//widget:xforms-instance-inspector)"/>

    <xsl:template match="@*|node()" priority="-100">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/xhtml:html/xhtml:head">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates select="node()"/>
            <xsl:if test="$has-xforms-instance-inspector">
                <xsl:call-template name="widget:xforms-instance-inspector-model"/>
                <xhtml:script language="Javascript" type="text/javascript">
//                    YAHOO.util.Event.addListener(window, "load", sourceInit);
//                    YAHOO.util.Event.addListener(window, "resize", sourceResize);
//
//                    function sourceInit() {
//                        sourceResize();
//                    }
//
//                    function sourceResize() {
//                        var divElement = document.getElementById('widgets-xforms-instance-inspector');
//                        var height = document.body.clientHeight - findTopPosition(divElement) - 18;
//                        var adjustedHeight = (height &lt; 100 ? 100 : height);
//
//                        alert("divElement.style.height = " + divElement.style.height);
//
//                        if (divElement.style.height != adjustedHeight + "px")
//                            divElement.style.height = adjustedHeight + "px";
//                    }
//
//                    function findTopPosition(element) {
//                        var curtop = 0;
//                        if (element.offsetParent) {
//                            while (element.offsetParent) {
//                                curtop += element.offsetTop
//                                element = element.offsetParent;
//                            }
//                        } else if (element.y) {
//                            curtop += element.y;
//                        }
//                        return curtop;
//                    }
                </xhtml:script>
            </xsl:if>
            <xsl:if test="$has-widgets">
                <xhtml:link rel="stylesheet" href="/config/theme/xforms-widgets.css" type="text/css"/>
            </xsl:if>
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
                                            <xforms:output value="{widget:label/@ref}"/>
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
            <xforms:group>
                <xhtml:table>
                    <xhtml:tr>
                        <xhtml:td>
                            <xforms:select1 ref="instance('orbeon-xforms-instance-inspector-instance')/function" appearance="full">
                                <xforms:item>
                                    <xforms:label>View instance</xforms:label>
                                    <xforms:value>view-instance</xforms:value>
                                </xforms:item>
                            </xforms:select1>
                        </xhtml:td>
                        <xforms:group appearance="xxforms:internal">
                            <xforms:dispatch ev:event="xforms-value-changed" name="DOMActivate" target="orbeon-xforms-instance-inspector-xpath"/>
                            <xhtml:td>
                                <!-- Model section -->
                                <xforms:group ref=".[count(instance('orbeon-xforms-instance-inspector-itemset')/model) gt 1]">
                                    <xforms:select1 ref="instance('orbeon-xforms-instance-inspector-instance')/current-model">
                                        <xforms:label>Model: </xforms:label>
                                        <xforms:itemset nodeset="instance('orbeon-xforms-instance-inspector-itemset')/model">
                                            <xforms:label ref="@id"/>
                                            <xforms:value ref="@id"/>
                                        </xforms:itemset>
                                        <xforms:action ev:event="xforms-value-changed">
                                            <xforms:setvalue ref="instance('orbeon-xforms-instance-inspector-instance')/current-instance"/>
                                        </xforms:action>
                                    </xforms:select1>
                                </xforms:group>
                                <xforms:group ref=".[count(instance('orbeon-xforms-instance-inspector-itemset')/model) = 1]">
                                    <xforms:output value="instance('orbeon-xforms-instance-inspector-instance')/current-model">
                                        <xforms:label>Model: </xforms:label>
                                    </xforms:output>
                                </xforms:group>
                            </xhtml:td>
                            <xhtml:td>
                                <!-- Instance selection -->
                                <xforms:group ref=".[count(instance('orbeon-xforms-instance-inspector-itemset')/model[@id = instance('orbeon-xforms-instance-inspector-instance')/current-model]/instance) gt 1]">
                                    <xforms:select1 ref="instance('orbeon-xforms-instance-inspector-instance')/current-instance">
                                        <xforms:label>Instance: </xforms:label>
                                        <xforms:itemset nodeset="instance('orbeon-xforms-instance-inspector-itemset')/model[@id = instance('orbeon-xforms-instance-inspector-instance')/current-model]/instance">
                                            <xforms:label ref="@id"/>
                                            <xforms:value ref="@id"/>
                                        </xforms:itemset>
                                    </xforms:select1>
                                </xforms:group>
                                <xforms:group ref=".[count(instance('orbeon-xforms-instance-inspector-itemset')/model[@id = instance('orbeon-xforms-instance-inspector-instance')/current-model]/instance) = 1]">
                                    <xforms:output value="instance('orbeon-xforms-instance-inspector-instance')/current-instance">
                                        <xforms:label>Instance: </xforms:label>
                                    </xforms:output>
                                </xforms:group>
                            </xhtml:td>
                        </xforms:group>
                    </xhtml:tr>
                    <xhtml:tr>
                        <xhtml:td>
                            <xforms:select1 ref="instance('orbeon-xforms-instance-inspector-instance')/function" appearance="full">
                                <xforms:item>
                                    <xforms:label>Evaluate XPath</xforms:label>
                                    <xforms:value>evaluate-xpath</xforms:value>
                                </xforms:item>
                                <xforms:setfocus ev:event="xforms-value-changed" if=". = 'evaluate-xpath'"
                                        control="orbeon-xforms-instance-inspector-xpath"/>
                            </xforms:select1>
                        </xhtml:td>
                        <xforms:group ref="instance('orbeon-xforms-instance-inspector-instance')/xpath" appearance="xxforms:internal">
                            <xhtml:td>
                                <!-- XPath expression and trigger -->
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:delete while="exists(instance('orbeon-xforms-instance-inspector-instance')/xml-output/node())"
                                            nodeset="instance('orbeon-xforms-instance-inspector-instance')/xml-output/node()"/>
                                    <xforms:insert if="instance('orbeon-xforms-instance-inspector-instance')/xpath != ''"
                                            context="instance('orbeon-xforms-instance-inspector-instance')/xml-output"
                                            origin="xxforms:instance(instance('orbeon-xforms-instance-inspector-instance')/current-instance)/
                                                saxon:evaluate(instance('orbeon-xforms-instance-inspector-instance')/xpath)"/>
                                </xforms:action>
                                <xforms:input ref="." id="orbeon-xforms-instance-inspector-xpath">
                                    <xforms:label>XPath expression: </xforms:label>
                                </xforms:input>
                            </xhtml:td>
                            <xhtml:td>
                                <xforms:trigger ref=".">
                                    <xforms:label>Run XPath</xforms:label>
                                    <xforms:help>
                                        <xhtml:ul>
                                            <xhtml:li>
                                                To trigger the evaluation of your expression, either press on the "Run
                                                XPath" button or just enter in the text field.
                                            </xhtml:li>
                                            <xhtml:li>
                                                Your XPath expression is evaluated on the currently selected instance.
                                            </xhtml:li>
                                        </xhtml:ul>
                                    </xforms:help>
                                </xforms:trigger>
                            </xhtml:td>
                        </xforms:group>
                    </xhtml:tr>
                    <xhtml:tr>
                        <xhtml:td/>
                        <xhtml:td>
                            <!-- Mode for formatting -->
                            <xforms:select1 appearance="full" ref="instance('orbeon-xforms-instance-inspector-instance')/mode">
                                <xforms:label>Mode: </xforms:label>
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
            </xforms:group>
            <div class="widgets-xforms-instance-inspector-source" id="widgets-xforms-instance-inspector">
                <xforms:group ref="instance('orbeon-xforms-instance-inspector-instance')">
                    <xforms:group ref="if (function = 'evaluate-xpath') then xml-output else
                            xxforms:instance(instance('orbeon-xforms-instance-inspector-instance')/current-instance)">

                        <!-- Display atomic value -->
                        <xforms:group ref=".[not(self::*)]">
                            <xforms:output value="."/>
                        </xforms:group>
                        <!-- Display element -->
                        <xforms:group ref=".[self::*]">
                            <xforms:group ref=".[instance('orbeon-xforms-instance-inspector-instance')/mode = 'formatted']">
                                <xforms:output mediatype="text/html"
                                        value="xxforms:serialize(xxforms:call-xpl(
                                                concat('oxf:/ops/utils/formatting/format',
                                                if (instance('orbeon-xforms-instance-inspector-instance')/function = 'evaluate-xpath') then '-multiple' else '',
                                                '.xpl'), 'data', ., 'data')/*, 'html')"/>
                            </xforms:group>
                            <xforms:group ref=".[instance('orbeon-xforms-instance-inspector-instance')/mode = 'plain']">
                                <xforms:output mediatype="text/html"
                                               value="replace(replace(replace(replace(xxforms:serialize(., 'xml'),
                                               '&amp;', '&amp;amp;'), '&lt;', '&amp;lt;'), '&#x0a;', '&lt;br>'), ' ', '&#160;')"/>
                            </xforms:group>
                        </xforms:group>
                    </xforms:group>
                </xforms:group>
            </div>
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
                    <function>view-instance</function>
                    <xpath/>
                    <xml-output/>
                    <html-output/>
                </instance>
            </xforms:instance>
            <xforms:bind nodeset="instance('orbeon-xforms-instance-inspector-instance')">
                <xforms:bind nodeset="current-instance" readonly="false()"
                             calculate="if (. = '') then instance('orbeon-xforms-instance-inspector-itemset')
                             /model[@id = instance('orbeon-xforms-instance-inspector-instance')/current-model]/instance[1]/@id else ."/>
                <xforms:bind nodeset="xpath" readonly="../function != 'evaluate-xpath'"/>
            </xforms:bind>
        </xforms:model>

    </xsl:template>

</xsl:stylesheet>
