<!--
  Copyright (C) 2014 Orbeon, Inc.

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

    <p:param type="input"  name="data"/>
    <p:param type="input"  name="bindings"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data"      href="#data"/>
        <p:input name="bindings" href="#bindings"/>
        <p:input name="config">
            <xsl:stylesheet
                    version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:xh="http://www.w3.org/1999/xhtml"
                    xmlns:xf="http://www.w3.org/2002/xforms">

                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <xsl:variable
                    name="model"
                    select="/*/xh:head/xf:model[@id = 'fr-form-model']"/>

                <xsl:variable
                    name="metadata-root-id"
                    select="$model/xf:instance[@id = 'fr-form-metadata']/metadata[1]/generate-id()"/>

                <!-- Add migration information -->
                <xsl:template match="metadata[generate-id() = $metadata-root-id]">
                    <xsl:copy>
                        <xsl:apply-templates select="@* | node() except updated-with-version"/>

                        <!-- We want the `updated-with-version` to be contiguous, so we explicitly apply the existing ones here. -->
                        <xsl:apply-templates select="updated-with-version"/>

                        <xsl:variable
                            xmlns:version="java:org.orbeon.oxf.common.Version"
                            name="current-version"
                            select="version:versionWithEdition()"/>

                        <xsl:if test="not(updated-with-version = $current-version)">
                            <xsl:element name="updated-with-version">
                                <xsl:value-of select="$current-version"/>
                            </xsl:element>
                        </xsl:if>

                        <!-- For example ('4.8.0', '[...json...]', '2019.1.0', '...json...') -->
                        <xsl:variable
                            xmlns:migration="java:org.orbeon.oxf.fb.FormBuilderMigrationXPathApi"
                            name="migration"
                            select="migration:buildGridMigrationMapXPath(/, doc('input:bindings'))"/>

                        <xsl:for-each select="1 to count($migration) idiv 2">
                            <xsl:variable name="i" select="."/>
                            <xsl:element name="migration">
                                <xsl:attribute name="version" select="$migration[$i * 2 - 1]"/>
                                <xsl:value-of select="$migration[$i * 2]"/>
                            </xsl:element>
                        </xsl:for-each>

                    </xsl:copy>
                </xsl:template>

                <!-- Remove existing `migration` element if any-->
                <xsl:template match="metadata[generate-id() = $metadata-root-id]/migration[@version = '4.8.0']"/>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>