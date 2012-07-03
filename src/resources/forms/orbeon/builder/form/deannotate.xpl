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
                            xmlns:xhtml="http://www.w3.org/1999/xhtml"
                            xmlns:xforms="http://www.w3.org/2002/xforms"
                            xmlns:ev="http://www.w3.org/2001/xml-events"
                            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
                            xmlns:xbl="http://www.w3.org/ns/xbl"
                            xmlns:dataModel="java:org.orbeon.oxf.fb.DataModel">

                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

                <xsl:variable name="metadata"
                              select="/*/xhtml:head/xforms:model[@id = 'fr-form-model']/xforms:instance[@id = 'fr-form-metadata']/*"/>

                <xsl:variable name="is-custom-instance"
                              select="$metadata/form-instance-mode = 'custom'"/>

                <!-- HTML title -->
                <xsl:template match="xhtml:head/xhtml:title">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <!-- Use the first title found -->
                        <xsl:value-of select="$metadata/title[1]"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Restore read-only instances -->
                <xsl:template match="xforms:instance[@fb:readonly = 'true']">
                    <xsl:copy>
                        <xsl:attribute name="xxforms:readonly" select="'true'"/>
                        <xsl:apply-templates select="@* except @fb:readonly | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Restore namespace on observers on FB actions  -->
                <xsl:template match="xforms:action[ends-with(@id, '-binding')]">
                    <xsl:copy>
                        <xsl:apply-templates select="@* | node()" mode="update-observers"/>
                    </xsl:copy>
                </xsl:template>

                <xsl:template match="@fb:observer" mode="update-observers">
                    <xsl:attribute name="ev:{local-name()}" select="."/>
                </xsl:template>

                <!-- xf:group → fr:view -->
                <xsl:template match="xforms:group[tokenize(@class, '\s+') = 'fb-view']">
                    <fr:view>
                        <fr:body>
                            <xsl:apply-templates select="xforms:group[tokenize(@class, '\s+') = 'fb-body']/node()"/>
                        </fr:body>
                    </fr:view>
                </xsl:template>

                <!-- Remove @edit-ref and @xxf:update, fb:view → fr:view, fb:section → fr:section -->
                <xsl:template match="xhtml:body//fb:view | xhtml:body//fb:section | xhtml:body//fr:grid">
                    <xsl:element name="fr:{local-name()}">
                        <xsl:apply-templates select="@* except (@edit-ref, @xxforms:update) | node()"/>
                    </xsl:element>
                </xsl:template>

                <!-- Restore binds pointing via deAnnotatedBindRef() -->
                <xsl:template match="xforms:bind/@ref[$is-custom-instance] | xforms:bind/@nodeset[$is-custom-instance]">
                    <xsl:attribute name="{name()}" select="dataModel:deAnnotatedBindRef(.)"/>
                </xsl:template>

                <!-- Convert MIP names -->
                <xsl:template match="xforms:bind/@fb:relevant | xforms:bind/@fb:readonly | xforms:bind/@fb:required | xforms:bind/@fb:constraint | xforms:bind/@fb:calculate">
                    <xsl:attribute name="{local-name()}" select="."/>
                </xsl:template>
                <xsl:template match="xforms:bind/@fb:default">
                    <xsl:attribute name="xxforms:{local-name()}" select="."/>
                </xsl:template>

                <!-- Remove automatic grid and td ids -->
                <xsl:template match="xhtml:body//fr:grid/@id[starts-with(., 'tmp-') and ends-with(., '-tmp')]"/>
                <xsl:template match="xhtml:body//fr:grid//*:td/@id[starts-with(., 'tmp-') and ends-with(., '-tmp')]"/>

                <!-- Remove xbl:xbl containing section templates bindings -->
                <xsl:template match="xbl:xbl[xbl:binding[tokenize(@class, '\s+') = 'fr-section-component']]"/>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>