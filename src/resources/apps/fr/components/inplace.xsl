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
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <xsl:template match="xforms:input[@appearance = 'fr:in-place']">
        <xsl:if test="normalize-space(@id) = ''">
            <xsl:message terminate="yes">"id" attribute is mandatory</xsl:message>
        </xsl:if>
        <xhtml:span id="{@id}" class="fr-inplace-input">
            <xforms:switch>
                <!-- Place @ref/@bind and alert in order to obtain validity MIP -->
                <!-- We handle validity at the switch level so we have only one alert in use for view/edit -->
                <xsl:apply-templates select="@class | @ref | @bind | xforms:alert"/>
                <!-- Place hidden label for error summary -->
                <xsl:for-each select="xforms:label[1]">
                    <xforms:label>
                        <xsl:attribute name="class" select="string-join(('fr-hidden', @class), ' ')"/>
                        <xsl:apply-templates select="@* except @class|node()"/>
                    </xforms:label>
                </xsl:for-each>

                <xforms:case id="fr-inplace-{@id}-view">
                    <!-- View mode -->
                    <xhtml:span class="fr-inplace-view">
                        <xhtml:span class="fr-inplace-content">
                            <xforms:output>
                                <xsl:if test="@tabindex | @navindex">
                                    <xsl:attribute name="navindex" select="(@navindex, @tabindex)[1]"/>
                                </xsl:if>
                                <!-- Handle inline hint-->
                                <xsl:call-template name="fr-handle-inplace-hint"/>
                                <!-- React to user click -->
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:toggle case="fr-inplace-{@id}-edit"/>
                                    <xforms:setfocus control="fr-inplace-{@id}-input"/>
                                </xforms:action>
                            </xforms:output>
                        </xhtml:span>
                    </xhtml:span>
                </xforms:case>
                <xforms:case id="fr-inplace-{@id}-edit">
                    <!-- Edit mode -->
                    <xhtml:span class="fr-inplace-edit">
                        <xhtml:span class="fr-inplace-content">
                            <xforms:input id="fr-inplace-{@id}-input" ref=".">
                                <xsl:if test="@tabindex | @navindex">
                                    <xsl:attribute name="navindex" select="(@navindex, @tabindex)[1]"/>
                                </xsl:if>
                                <xsl:apply-templates select="@incremental | @xxforms:autocomplete"/>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:input>
                            <xhtml:span class="fr-inplace-buttons">
                                <xforms:trigger class="fr-inplace-rename">
                                    <xforms:label>Apply <xsl:value-of select="lower-case(xforms:label)"/></xforms:label>
                                    <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                                </xforms:trigger>
                                <!-- Cancel not supported for now -->
                                <!--or-->
                                <!--<xforms:trigger appearance="minimal" class="fr-inplace-cancel">-->
                                    <!--<xforms:label>Cancel</xforms:label>-->
                                    <!--<xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>-->
                                <!--</xforms:trigger>-->
                            </xhtml:span>
                        </xhtml:span>
                    </xhtml:span>
                </xforms:case>
                <!-- Copy other children elements, including event handlers -->
                <xsl:apply-templates select="* except (xforms:label, xforms:hint, fr:buttons)"/>
            </xforms:switch>
        </xhtml:span>
    </xsl:template>

    <xsl:template match="xhtml:body//xforms:textarea[@appearance = 'fr:in-place']">
        <xforms:switch id="{@id}">
            <xsl:attribute name="class" select="string-join(('fr-inplace-textarea', @class), ' ')"/>
            <xforms:case id="fr-inplace-{@id}-view">
                <xhtml:div class="fr-inplace-view">
                    <xhtml:span class="fr-inplace-content">
                        <xforms:output>
                            <!-- Handle inline hint-->
                            <xsl:call-template name="fr-handle-inplace-hint"/>
                            <!-- Place @ref/@bind and alert in order to obtain validity MIP -->
                            <xsl:apply-templates select="@ref | @bind | xforms:alert"/>
                            <!-- Keep label as is -->
                            <xsl:apply-templates select="xforms:label"/>
                            <!-- React to user click -->
                            <xforms:action ev:event="DOMActivate">
                                <xforms:toggle case="fr-inplace-{@id}-edit"/>
                                <xforms:setfocus control="fr-inplace-{@id}-textarea"/>
                            </xforms:action>
                        </xforms:output>
                    </xhtml:span>
                </xhtml:div>
            </xforms:case>
            <xforms:case id="fr-inplace-{@id}-edit">
                <xhtml:div class="fr-inplace-edit">
                    <xhtml:span class="fr-inplace-content">
                        <xforms:textarea id="fr-inplace-{@id}-textarea" appearance="xxforms:autosize">
                            <xsl:apply-templates select="@ref | @bind | @incremental | xforms:label | xforms:alert"/>
                            <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                        </xforms:textarea>
                        <xhtml:span class="fr-inplace-buttons">
                            <xforms:trigger class="fr-inplace-rename">
                                <!-- TODO: i18n -->
                                <xforms:label>Apply <xsl:value-of select="lower-case(xforms:label)"/></xforms:label>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:trigger>
                            <!-- Cancel not supported for now -->
                            <!--or-->
                            <!--<xforms:trigger appearance="minimal" class="fr-inplace-cancel">-->
                                <!--<xforms:label>Cancel</xforms:label>-->
                                <!--<xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>-->
                            <!--</xforms:trigger>-->
                        </xhtml:span>
                    </xhtml:span>
                </xhtml:div>
            </xforms:case>
        </xforms:switch>
    </xsl:template>

    <xsl:template name="fr-handle-inplace-hint">
        <xsl:choose>
            <xsl:when test="@ref">
                <xsl:choose>
                    <xsl:when test="xforms:hint">
                        <xsl:attribute name="value"
                                       select="concat('for $value in ', @ref, '
                                                       return if (normalize-space($value) = '''')
                                                              then concat(''['', ', if (xforms:hint/@ref) then xforms:hint/@ref else concat('''', xforms:hint, ''''), ', '']'')
                                                              else $value')"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="value" select="@ref"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>
            <xsl:when test="@bind">
                <xsl:choose>
                    <xsl:when test="xforms:hint">
                        <xsl:attribute name="value"
                                       select="concat('for $value in xxforms:bind(''', @bind, ''')
                                                       return if (normalize-space($value) = '''')
                                                              then concat(''['', ', if (xforms:hint/@ref) then xforms:hint/@ref else concat('''', xforms:hint, ''''), ', '']'')
                                                              else $value')"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="@bind"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
