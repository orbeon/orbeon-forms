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
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:array="http://www.w3.org/2005/xpath-functions/array"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!-- ======== Migrations only needed at design-time and reverted with `deannotate.xpl` ======== -->

    <xsl:variable name="xbl-ids"  select="/*/xh:head/xbl:xbl/generate-id()"/>
    <xsl:variable name="body"     select="/*/xh:body"/>

    <!-- Whether we have "many" controls -->
    <xsl:variable
        name="many-controls"
        select="count($body//*:td[exists(*)] | $body//*:c[exists(*)]) ge p:property('oxf.fb.section.close')"/>

    <!-- Temporarily mark read-only instances as read-write -->
    <!-- Except `fr-form-metadata`, see https://github.com/orbeon/orbeon-forms/issues/3822 -->
    <xsl:template match="xf:model/xf:instance[not(@id = 'fr-form-metadata')]/@xxf:readonly[. = 'true']" mode="within-model">
        <xsl:attribute name="fb:readonly" select="'true'"/><!-- so we remember to set the value back -->
    </xsl:template>

    <!-- Update namespace on actions and services so that they don't run at design time -->
    <!-- NOTE: We disable all event handlers below so this is probably not needed anymore, but Form Builder
         currently (2015-07-06) depends on the fb:* prefixes on the elements. -->
    <xsl:template
            match="xf:model/xf:*[p:classes() = ('fr-service', 'fr-database-service')] | xf:model/xf:action[ends-with(@id, '-binding')]"
            mode="within-model">
        <xsl:element name="fb:{local-name()}">
            <xsl:apply-templates select="@* | node()" mode="#current"/>
        </xsl:element>
    </xsl:template>

    <!-- Disable all event handlers at design time... -->
    <xsl:template match="@ev:event | @event" mode="#all">
        <xsl:attribute name="fb:{local-name()}" select="."/>
    </xsl:template>
    <!--  ...except those under xbl:xbl which must be preserved -->
    <xsl:template
            match="@event[../@class = 'fr-design-time-preserve']"
            mode="within-xbl">
        <xsl:copy-of select="."/>
    </xsl:template>

    <!-- fr:section → fr:section/(@edit-ref, @xxf:update) -->
    <xsl:template match="fr:section" mode="within-body">
        <xsl:copy>
            <xsl:attribute name="edit-ref"/>
            <xsl:attribute name="xxf:update" select="'full'"/>
            <!-- Save current value of @open as @fb:open -->
            <xsl:if test="@open"><xsl:attribute name="fb:open" select="@open"/></xsl:if>
            <!-- If "many" controls close all sections but the first -->
            <xsl:choose>
                <xsl:when test="$many-controls and empty(preceding::fr:section)">
                    <xsl:attribute name="open" select="'true'"/>
                </xsl:when>
                <xsl:when test="$many-controls and exists(preceding::fr:section)">
                    <xsl:attribute name="open" select="'false'"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates select="@open"/>
                </xsl:otherwise>
            </xsl:choose>

            <xsl:apply-templates select="@* except @open | node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="fr:grid" mode="within-body">
        <xsl:copy>
            <xsl:attribute name="edit-ref"/>
            <xsl:apply-templates select="@* | node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <!-- Convert MIP names (attributes and nested elements) -->
    <!-- NOTE: We leave custom MIPs as they are. The user must not use fb:* custom MIPs. -->
    <xsl:template match="xf:bind/@relevant
                       | xf:bind/@readonly
                       | xf:bind/@constraint
                       | xf:bind/@calculate
                       | xf:bind/@xxf:default"
                  mode="within-model">
        <xsl:attribute name="fb:{local-name()}" select="."/>
    </xsl:template>

    <xsl:template match="xf:bind/xf:relevant
                       | xf:bind/xf:readonly
                       | xf:bind/xf:constraint
                       | xf:bind/xf:calculate
                       | xf:bind/xxf:default"
                  mode="within-model">
        <xsl:element name="fb:{local-name()}">
            <xsl:apply-templates select="@* | node()" mode="#current"/>
        </xsl:element>
    </xsl:template>

    <!-- Prevent fr:buttons from showing/running -->
    <xsl:template match="fr:buttons">
        <xf:group class="fr-buttons" ref="()">
            <xsl:apply-templates select="node()"/>
        </xf:group>
    </xsl:template>

    <xsl:template match="xbl:xbl[generate-id() = $xbl-ids]">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" mode="within-xbl"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>