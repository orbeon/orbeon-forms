<!--
  Copyright (C) 2011 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="data"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#data"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                            xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                            xmlns:xh="http://www.w3.org/1999/xhtml"
                            xmlns:xf="http://www.w3.org/2002/xforms"
                            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                            xmlns:xbl="http://www.w3.org/ns/xbl">

                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

                <xsl:variable name="metadata"
                              select="/*/xh:head/xf:model[@id = 'fr-form-model']/xf:instance[@id = 'fr-form-metadata']/*"/>

                <!-- HTML title -->
                <xsl:template match="xh:head/xh:title">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <!-- Use the first title found -->
                        <xsl:value-of select="$metadata/title[1]"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Restore read-only instances -->
                <xsl:template match="xf:instance[@fb:readonly = 'true']">
                    <xsl:copy>
                        <xsl:attribute name="xxf:readonly" select="'true'"/>
                        <xsl:apply-templates select="@* except @fb:readonly | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Restore namespace on actions and services -->
                <xsl:template match="xf:model/fb:*[p:classes() = ('fr-service', 'fr-database-service')] | xf:model/fb:action[ends-with(@id, '-binding')]">
                    <xsl:element name="xf:{local-name()}">
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>

                <!-- Restore event handlers -->
                <xsl:template match="@fb:event">
                    <xsl:attribute name="{local-name()}" select="."/>
                </xsl:template>

                <xsl:template match="xf:group[@id = 'fb-body']">
                    <fr:body>
                        <xsl:copy-of select="namespace::*"/>
                        <xsl:apply-templates select="node() except *[@class = 'fb-annotation']"/>
                    </fr:body>
                </xsl:template>

                <!-- Remove @edit-ref and @xxf:update, handle section @open, fb:view → fr:view -->
                <xsl:template match="xh:body//fb:view | xh:body//fr:section | xh:body//fr:grid">
                    <xsl:element name="fr:{local-name()}">
                        <!-- Restore `@fb:*` if needed -->
                        <xsl:for-each select="@fb:readonly | @fb:page-size">
                            <xsl:attribute name="{local-name(.)}" select="."/>
                        </xsl:for-each>
                        <xsl:if test="exists(self::fr:section)">
                            <xsl:if test="exists(@fb:open)">
                                <xsl:attribute name="open" select="@fb:open"/>
                            </xsl:if>
                            <xsl:if test="exists(@fb:collapsible)">
                                <xsl:attribute name="collapsible" select="@fb:collapsible"/>
                            </xsl:if>
                        </xsl:if>
                        <!-- Process everything else -->
                        <xsl:apply-templates select="(@* except (@edit-ref | @xxf:update | @open | @fb:open | @fb:readonly | @fb:page-size | @fb:collapsible)) | node()"/>
                    </xsl:element>
                </xsl:template>

                <!-- Convert MIP names (attributes and nested elements) -->
                <xsl:template match="xf:bind/@fb:relevant
                                   | xf:bind/@fb:readonly
                                   | xf:bind/@fb:constraint
                                   | xf:bind/@fb:calculate">
                    <xsl:attribute name="{local-name()}" select="."/>
                </xsl:template>
                <xsl:template match="xf:bind/@fb:default">
                    <xsl:attribute name="xxf:{local-name()}" select="."/>
                </xsl:template>

                <xsl:template match="xf:bind/fb:relevant
                                   | xf:bind/fb:readonly
                                   | xf:bind/fb:constraint
                                   | xf:bind/fb:calculate">
                    <xsl:element name="xf:{local-name()}">
                        <xsl:apply-templates select="@* | node()" mode="#current"/>
                    </xsl:element>
                </xsl:template>
                <xsl:template match="xf:bind/fb:default">
                    <xsl:element name="xxf:{local-name()}">
                        <xsl:apply-templates select="@* | node()" mode="#current"/>
                    </xsl:element>
                </xsl:template>

                <!-- https://github.com/orbeon/orbeon-forms/issues/6648 -->
                <xsl:template match="@fb:class">
                    <xsl:attribute name="{local-name()}" select="."/>
                </xsl:template>

                <!-- Restore xxf:custom-mips if we had added it -->
                <xsl:template match="
                    xh:head/xf:model[
                        @id = 'fr-form-model'
                        and @fb:added-empty-custom-mips = 'true'
                    ]/@xxf:custom-mips"/>
                <xsl:template match="
                    xh:head/xf:model[
                        @id = 'fr-form-model'
                    ]/@fb:added-empty-custom-mips"/>

                <!-- Remove model actions -->
                <xsl:template match="xh:head/xf:model[@id = 'fr-form-model']/*[p:has-class('fb-annotation')]"/>

                <!-- Remove automatic grid and td ids -->
                <!-- NOTE: Grids are now annotated with a `-grid` suffix and those ids won't be removed. -->
                <xsl:template match="xh:body//fr:grid/@id[starts-with(., 'tmp-') and ends-with(., '-tmp')]"/>
                <xsl:template match="xh:body//fr:grid//*:c/@id[starts-with(., 'tmp-') and ends-with(., '-tmp')]"/>

                <!-- Remove xbl:xbl containing section templates bindings -->
                <xsl:template match="xbl:xbl[xbl:binding[p:has-class('fr-section-component')]]"/>

                <!-- Restore fr:buttons -->
                <xsl:template match="xf:group[p:has-class('fr-buttons')]">
                    <fr:buttons>
                        <xsl:apply-templates select="node()"/>
                    </fr:buttons>
                </xsl:template>

                <!-- Restore selection -->
                <xsl:template
                    match="fr:databound-select1[@fb:selection] | fr:databound-select1-search[@fb:selection]">
                    <xsl:copy>
                        <xsl:attribute name="selection" select="@fb:selection"/>
                        <xsl:apply-templates select="@* except (@fb:selection) | node()"/>
                    </xsl:copy>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>