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
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    exclude-result-prefixes="xforms xs saxon xhtml f">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <xsl:output name="xml" method="xml"/>

    <xsl:template match="widget:tabs">
        <xsl:variable name="tabs" as="element()*" select="widget:tab"/>
        <xhtml:table class="widget-tabs" cellpadding="0" cellspacing="0" border="0">
            <xhtml:tr>
                <xhtml:td class="widget-tab-spacer-side"/>
                <!-- Tabs at the top -->
                <xsl:variable name="selected-tab-specified" as="xs:boolean" select="count(widget:tab[@selected = 'true']) = 1"/>
                <xsl:for-each select="$tabs">
                    <xsl:variable name="tab-id" as="xs:string" select="@id"/>
                    <xsl:if test="position() > 1">
                        <xhtml:td class="widget-tab-spacer-between"/>
                    </xsl:if>
                    <xhtml:td class="widget-tab">
                        <xforms:switch>
                            <!-- Case where this tab is inactive -->
                            <xforms:case id="{$tab-id}-inactive">
                                <xhtml:div class="widget-tab-inactive">
                                    <xforms:trigger appearance="xxforms:link">
                                        <xforms:label><xsl:value-of select="widget:label"/></xforms:label>
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
                                    <xsl:value-of select="widget:label"/>
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
                        <xsl:for-each select="widget:tab">
                            <xforms:case>
                                <xsl:copy-of select="@*"/>
                                <xsl:apply-templates select="node() except widget:label"/>
                            </xforms:case>
                        </xsl:for-each>
                    </xforms:switch>
                </xhtml:td>
            </xhtml:tr>
        </xhtml:table>
    </xsl:template>
    
</xsl:stylesheet>
