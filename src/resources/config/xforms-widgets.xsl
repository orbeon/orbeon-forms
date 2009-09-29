<!--
  Copyright (C) 2009 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms" xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:widget="http://orbeon.org/oxf/xml/widget"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner" xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:xi="http://www.w3.org/2001/XInclude"
    xmlns:xxi="http://orbeon.org/oxf/xml/xinclude" xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <xsl:variable name="has-widgets" as="xs:boolean" select="exists(//widget:*)"/>
    <xsl:key name="xbl:bindings" match="xbl:binding" use="translate(@element, '|', ':')"/>

    <xsl:template match="@*|node()" priority="-100">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/xhtml:html/xhtml:head">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates select="node()"/>

            <!-- Include XBL components -->
            <xi:include href="oxf:/config/xforms-widgets.xbl" xxi:omit-xml-base="true"/>

            <xsl:if test="pipeline:property('oxf.epilogue.xforms.widgets.auto-include-fr-widgets')">
                <!-- Include fr:* widgets -->
                <xsl:variable name="widgets-to-exclude"
                              select="tokenize(pipeline:property('oxf.epilogue.xforms.widgets.fr-elements-to-skip'), '\s+')"/>
                <xsl:for-each-group select="//fr:*|//widget:table" group-by="name()">
                    <xsl:if test="not( key('xbl:bindings', name())) and not( name() = $widgets-to-exclude )">
                        <!-- 
                        
                        Test if the widget isn't defined locally (either directly or as a result on an xi:include and if 
                        it's not in the list of FR elements that are not (yet?) implemented as XBL widgets.
                        
                        Note that this test is weak because it relies on the namespace prefix of the bound element 
                    
                        -->
                        <!-- NOTE: use XInclude to allow caching. doc() would disable caching here. -->
                        <xi:include href="oxf:/xbl/orbeon/{local-name()}/{local-name()}.xbl" xxi:omit-xml-base="true"/>

                    </xsl:if>
                </xsl:for-each-group>
            </xsl:if>

            <xsl:if test="$has-widgets or pipeline:property('oxf.epilogue.xforms.inspector')">
                <!-- NOTE: Would be nice to do this with the xbl:style element -->
                <xhtml:link rel="stylesheet" href="/config/theme/xforms-widgets.css" type="text/css" media="all"/>
            </xsl:if>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="widget:tabs">
        <xsl:variable name="tabs-element" select="."/>
        <!-- as="element()"  -->
        <xsl:variable name="tabs" select="widget:tab"/>
        <!-- as="element()*"  -->
        <xhtml:table class="widget-tabs" cellpadding="0" cellspacing="0" border="0">
            <xsl:copy-of select="@*"/>
            <xhtml:tr>
                <xhtml:td class="widget-tab-spacer-side"/>
                <!-- Tabs at the top -->
                <xsl:variable name="selected-tab-specified" select="count(widget:tab[@selected = 'true']) = 1"/>
                <!-- as="xs:boolean"  -->
                <xsl:for-each select="$tabs">
                    <xsl:variable name="tab-id" select="@id"/>
                    <!-- as="xs:string"  -->
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
                                    <xforms:trigger appearance="minimal" id="{@id}-trigger">
                                        <xsl:choose>
                                            <xsl:when test="widget:label/@ref">
                                                <xforms:label ref="{widget:label/@ref}"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xforms:label>
                                                    <xsl:copy-of select="widget:label/node()"/>
                                                </xforms:label>
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
                                            <xsl:copy-of select="widget:label/node()"/>
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

    <!-- Support for legacy image appearance -->
    <xsl:template match="xforms:trigger[@appearance = 'xxforms:image'] | xforms:submit[@appearance = 'xxforms:image']">
        <xsl:copy>
            <!-- Copy all attributes but replace the appearance with "minimal" -->
            <xsl:copy-of select="@* except @appearance"/>
            <xsl:attribute name="appearance" select="'minimal'"/>
            <!-- Create label with embedded image -->
            <xforms:label>
                <xhtml:img>
                    <xsl:copy-of select="xxforms:img/@*"/>
                </xhtml:img>
            </xforms:label>
            <!-- Process the rest of the stuff -->
            <xsl:apply-templates select="node() except (xforms:label, xxforms:img)"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="xhtml:body[pipeline:property('oxf.epilogue.xforms.inspector')]">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
            <widget:xforms-instance-inspector id="orbeon-xforms-inspector" xmlns:widget="http://orbeon.org/oxf/xml/widget"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
