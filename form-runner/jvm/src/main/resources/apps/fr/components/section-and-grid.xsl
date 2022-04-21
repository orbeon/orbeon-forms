<!--
  Copyright (C) 2010 Orbeon, Inc.

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
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:variable name="starting-section-level" select="2"/>

    <!-- 2021-10-07: Not quite true, at least not for all cases. We could easily implement something like `keep-if-param-non-blank`.
         This needs to be done at the XBL processing level, since the components use XSLT to produce different content, at least
         with `collapsible`. So for example `keep-if-param-is` and `keep-if-param-is-not`. For `animate` this creates and attribute
         and we'd need some extra support. Other changes are probably not possible via parameters. -->
    <xsl:template match="fr:section" mode="within-controls">

        <xsl:param name="section-level"                tunnel="yes" select="1"/>
        <xsl:param name="library-name" as="xs:string?" tunnel="yes"/>

        <xsl:copy>
            <xsl:if test="empty(@collapse) and empty(@collapsible)">
                <xsl:attribute name="collapsible" select="$is-section-collapsible"/>
            </xsl:if>
            <xsl:if test="empty(@animate) ">
                <xsl:attribute name="animate"  select="$is-animate-sections"/>
            </xsl:if>
            <!-- Set repeat appearance if available and needed -->
            <xsl:if
                test="
                    frf:isRepeat(.)             and
                    empty(@appearance)          and
                    exists($section-appearance) and
                    $section-appearance != 'full'">
                <xsl:attribute name="appearance" select="$section-appearance"/>
            </xsl:if>
            <xsl:if
                test="
                    frf:isRepeat(.)             and
                    empty(@insert)              and
                    exists($section-insert)     and
                    $section-insert != 'index'">
                <xsl:attribute name="insert" select="$section-insert"/>
            </xsl:if>

            <!-- For https://github.com/orbeon/orbeon-forms/issues/3011 -->
            <xsl:attribute name="level" select="$section-level"/>
            <xsl:choose>
                <xsl:when test="empty(ancestor::xbl:*)">
                    <xsl:attribute name="base-level" select="$starting-section-level - 1"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:attribute name="xbl:attr" select="'base-level'"/>
                </xsl:otherwise>
            </xsl:choose>

            <xsl:if test="$sync-actions/@right = frf:controlNameFromId(@id)">
                <xsl:attribute name="readonly">true</xsl:attribute>
            </xsl:if>

            <xsl:if test="$use-view-appearance and $view-appearance = 'wizard' and not($mode = ('view', 'pdf', 'test-pdf', 'email', 'controls') (: fr:is-readonly-mode() :))">
                <xsl:copy-of select="@page-size"/>
            </xsl:if>

            <xsl:for-each select="@min | @max | @freeze | @remove-constraint">
                <xsl:attribute name="{name(.)}" select="frf:replaceVarReferencesWithFunctionCalls(., ., true(), $library-name)"/>
            </xsl:for-each>

            <xsl:apply-templates select="@* except (@page-size | @min | @max | @freeze | @remove-constraint)" mode="#current"/>
            <xsl:apply-templates select="node()" mode="#current">
                <xsl:with-param name="section-level" select="$section-level + 1" tunnel="yes"/>
            </xsl:apply-templates>

        </xsl:copy>
    </xsl:template>

    <!-- For https://github.com/orbeon/orbeon-forms/issues/3011 -->
    <xsl:template match="fr:section/*[frf:isSectionTemplateContent(.)]" mode="within-controls">
        <xsl:param name="section-level" tunnel="yes"/><!-- must be nested within `fr:section` -->
        <xsl:copy>
            <xsl:apply-templates select="@*" mode="#current"/>
            <xsl:attribute name="base-level" select="$section-level - 1 + $starting-section-level - 1"/>
            <xsl:apply-templates select="node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="fr:grid" mode="within-controls within-dialogs">
        <xsl:param name="library-name" as="xs:string?" tunnel="yes"/>
        <xsl:copy>

            <!-- Annotate `<fr:grid>` element with the `markup` attribute, based on property using app/name/mode,
                 as XSLT inside XBL doesn't have access to the app/form/mode. Instead, we could pass the app/form/mode
                 and let the XSLT inside the XBL component check the property, if doing this, changing the property
                 and reloading the form doesn't use the new value of the property. -->
            <xsl:variable
                name="markup-property"
                select="
                    if ($mode = 'pdf')
                    then 'html-table'
                    else p:property(string-join(('oxf.xforms.xbl.fr.grid.markup', $app, $form), '.'))
                "/>

            <xsl:attribute name="markup" select="$markup-property"/>

            <!-- Set repeat appearance if available and needed -->
            <xsl:if test="frf:isRepeat(.)">
                <xsl:if
                    test="
                        empty(@appearance)       and
                        exists($grid-appearance) and
                        $grid-appearance != 'full'">
                    <xsl:attribute name="appearance" select="$grid-appearance"/>
                </xsl:if>
                <xsl:if
                    test="
                        frf:isRepeat(.)          and
                        empty(@insert)           and
                        exists($grid-insert)     and
                        $grid-insert != 'index'">
                    <xsl:attribute name="insert" select="$grid-insert"/>
                </xsl:if>
            </xsl:if>

            <xsl:for-each select="@min | @max | @freeze | @remove-constraint">
                <xsl:attribute name="{name(.)}" select="frf:replaceVarReferencesWithFunctionCalls(., ., true(), $library-name)"/>
            </xsl:for-each>

            <xsl:apply-templates select="@* except (@min | @max | @freeze | @remove-constraint) | node()" mode="#current"/>

        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
