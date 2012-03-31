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
    <p:param type="input" name="bindings"/>
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
                            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
                            xmlns:dataModel="java:org.orbeon.oxf.fb.DataModel">

                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <!-- Duplicated from model.xml, but we have to right now as the form hasn't been loaded yet at this point -->
                <xsl:variable name="is-custom-instance"
                              select="/*/xhtml:head/xforms:model[@id = 'fr-form-model']/xforms:instance[@id = 'fr-form-metadata']/*/form-instance-mode = 'custom'"/>

                <!-- Modify binds to point to a readonly node if the binding is empty -->
                <xsl:template match="/*/xhtml:head/xforms:model[@id = 'fr-form-model']/xforms:instance[@id = 'fr-form-instance']">
                    <xsl:next-match/>
                    <xforms:instance id="fb-readonly" xxforms:readonly="true"><readonly/></xforms:instance>
                </xsl:template>
                <xsl:template match="xforms:bind/@ref[$is-custom-instance] | xforms:bind/@nodeset[$is-custom-instance]">
                    <xsl:attribute name="{name()}" select="dataModel:annotatedBindRef(.)"/>
                </xsl:template>

                <!-- Temporarily mark read-only instances as read-write -->
                <xsl:template match="xforms:instance[@xxforms:readonly = 'true']">
                    <xsl:copy>
                        <xsl:attribute name="fb:readonly" select="'true'"/><!-- so we remember to set the value back -->
                        <xsl:apply-templates select="@* except @xxforms:readonly | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- fr:view -> fb:view -->
                <xsl:template match="xhtml:body//fr:view">
                    <xsl:element name="fb:{local-name()}">
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>

                <!-- Add @edit-ref -->
                <xsl:template match="xhtml:body//fr:section">
                    <xsl:element name="fb:{local-name()}">
                        <xsl:attribute name="edit-ref"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>

                <xsl:template match="xhtml:body//fr:grid">
                    <xsl:copy>
                        <xsl:attribute name="edit-ref"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Convert MIP names -->
                <xsl:template match="xforms:bind/@relevant | xforms:bind/@readonly | xforms:bind/@required | xforms:bind/@constraint | xforms:bind/@calculate | xforms:bind/@xxforms:default">
                    <xsl:attribute name="fb:{local-name()}" select="."/>
                </xsl:template>

                <!-- Saxon serialization adds an extra meta element, make sure to remove it -->
                <xsl:template match="xhtml:head/meta[@http-equiv = 'Content-Type']"/>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="annotated"/>
    </p:processor>

    <!-- Make sure XBL bindings for section templates are present -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="add-template-bindings.xpl"/>
        <p:input name="data" href="#annotated"/>
        <p:input name="bindings" href="#bindings"/>
        <p:output name="data" ref="data"/>
    </p:processor>


</p:config>