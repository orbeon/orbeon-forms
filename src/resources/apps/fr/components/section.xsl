<?xml version="1.0" encoding="UTF-8"?>
<!--
    Copyright (C) 2008 Orbeon, Inc.

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
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:exforms="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:pipeline="org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <xsl:template match="xhtml:body//fr:section">
        <xsl:variable name="open" as="xs:boolean" select="if (doc('input:instance')/*/mode = 'print') then true() else if (@open = 'false') then false() else true()"/>
        <xsl:variable name="section-id" as="xs:string" select="@id"/>


        <xsl:variable name="ancestor-sections" as="xs:integer" select="count(ancestor::fr:section)"/>
        <xforms:group id="{$section-id}">
            <!-- Support single-node bindings and contex -->
            <xsl:copy-of select="@ref | @bind | @context"/>

            <xsl:attribute name="class" select="string-join(('fr-section-container', @class), ' ')"/>
            <xforms:switch id="switch-{$section-id}" xxforms:readonly-appearance="dynamic">
                <xforms:case id="case-{$section-id}-closed" selected="{if (not($open)) then 'true' else 'false'}">
                    <xhtml:div>
                        <xsl:element name="{if ($ancestor-sections = 0) then 'xhtml:h2' else 'xhtml:h3'}">
                            <xsl:attribute name="class" select="'fr-section-title'"/>

                            <xsl:variable name="action" as="element(xforms:action)">
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')/id">
                                        <xsl:value-of select="$section-id"/>
                                    </xforms:setvalue>
                                    <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')/repeat-indexes" value="event('xxforms:repeat-indexes')"/>
                                    <xforms:dispatch target="fr-sections-model" name="fr-expand"/>
                                </xforms:action>
                            </xsl:variable>

                            <xforms:group appearance="xxforms:internal">
                                <!-- "+" trigger -->
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <!-- TODO: i18n of title -->
                                        <xhtml:img width="12" height="12" src="/apps/fr/style/plus.png" alt="Open section" title="Open section" class="fr-open-close"/>
                                    </xforms:label>
                                    <xsl:if test="@editable = 'true'">
                                        <xsl:apply-templates select="$action"/>
                                    </xsl:if>
                                </xforms:trigger>
                                <!-- Display label, editable or not -->
                                <xsl:choose>
                                    <xsl:when test="@editable = 'true'">
                                        <xsl:variable name="input" as="element(xforms:input)">
                                            <xforms:input id="{$section-id}-input-closed" ref="{xforms:label/@ref}" appearance="fr:in-place">
                                                <xsl:apply-templates select="xforms:hint"/>
                                                <xsl:apply-templates select="fr:buttons"/>
                                            </xforms:input>
                                        </xsl:variable>
                                        <xsl:apply-templates select="$input"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xforms:trigger appearance="minimal">
                                            <xsl:apply-templates select="xforms:label"/>
                                        </xforms:trigger>
                                    </xsl:otherwise>
                                </xsl:choose>
                                <xsl:if test="not(@editable = 'true')">
                                    <xsl:apply-templates select="$action"/>
                                </xsl:if>
                            </xforms:group>
                        </xsl:element>
                    </xhtml:div>
                </xforms:case>
                <xforms:case id="case-{$section-id}-open" selected="{if ($open) then 'true' else 'false'}">
                    <xhtml:div>
                        <xsl:element name="{if ($ancestor-sections = 0) then 'xhtml:h2' else 'xhtml:h3'}">
                            <xsl:attribute name="class" select="'fr-section-title'"/>

                            <xsl:variable name="action" as="element(xforms:action)">
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')/id">
                                        <xsl:value-of select="$section-id"/>
                                    </xforms:setvalue>
                                    <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')/repeat-indexes" value="event('xxforms:repeat-indexes')"/>
                                    <xforms:dispatch target="fr-sections-model" name="fr-collapse"/>
                                </xforms:action>
                            </xsl:variable>

                            <xforms:group appearance="xxforms:internal">
                                <!-- "-" trigger -->
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <!-- TODO: i18n of title -->
                                        <xhtml:img width="12" height="12" src="/apps/fr/style/minus.png" alt="Close section" title="Close section" class="fr-open-close"/>
                                    </xforms:label>
                                    <xsl:if test="@editable = 'true'">
                                        <xsl:apply-templates select="$action"/>
                                    </xsl:if>
                                </xforms:trigger>
                                <!-- Display label, editable or not -->
                                <xsl:choose>
                                    <xsl:when test="@editable = 'true'">
                                        <xsl:variable name="input" as="element(xforms:input)">
                                            <xforms:input id="{$section-id}-input-open" ref="{xforms:label/@ref}" appearance="fr:in-place">
                                                <xsl:apply-templates select="xforms:hint"/>
                                                <xsl:apply-templates select="fr:buttons"/>
                                            </xforms:input>
                                        </xsl:variable>
                                        <xsl:apply-templates select="$input"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xforms:trigger appearance="minimal">
                                            <xsl:apply-templates select="xforms:label"/>
                                        </xforms:trigger>
                                    </xsl:otherwise>
                                </xsl:choose>
                                <xsl:if test="not(@editable = 'true')">
                                    <xsl:apply-templates select="$action"/>
                                </xsl:if>
                            </xforms:group>
                        </xsl:element>
                        <xhtml:div class="fr-collapsible">
                            <!-- Section content except label, event handlers, and buttons -->
                            <xsl:apply-templates select="* except (xforms:label, *[@ev:*], fr:buttons)"/>
                        </xhtml:div>
                    </xhtml:div>
                </xforms:case>
            </xforms:switch>
            <!-- Event handlers children of fr:seciton -->
            <xsl:apply-templates select="*[@ev:*]"/>
        </xforms:group>
    </xsl:template>
</xsl:stylesheet>