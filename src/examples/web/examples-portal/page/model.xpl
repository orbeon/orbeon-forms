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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>
    <p:param name="instance" type="output"/>

    <!-- Create the portal configuration -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <portal-config xsl:version="2.0">
                <xsl:variable name="instance" select="/*" as="element()"/>
                <!-- We configure the portal with an embedded portlet.xml -->
                <xsl:copy-of select="document('../config/portlet.xml')/*"/>
                <!-- Give the portal an id -->
                <portal-id>examples</portal-id>
                <!-- Configure what instances of portlets are present -->
                <xsl:for-each select="('OXFExamplesPortlet', 'OXFExamplesDocumentationPortlet', 'OXFExamplesSourceCodePortlet')">
                    <portlet-instance>
                        <portlet-name><xsl:value-of select="."/></portlet-name>
<!--                        <window-state>-->
<!--                            <xsl:value-of select="if ($instance/visible-portlet = .) then 'normal' else 'minimized'"/>-->
<!--                        </window-state>-->
                    </portlet-instance>
                </xsl:for-each>
            </portal-config>
        </p:input>
        <p:output name="data" id="portal-config"/>
    </p:processor>

    <!-- Remove action from instance -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="/form/action">
                    <xsl:copy/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="instance"/>
    </p:processor>

    <!-- Configure the portal and read the portal status -->
    <p:processor name="oxf:portlet-container">
        <p:input name="portal-config" href="#portal-config"/>
        <p:output name="portal-status" id="portal-status"/>
    </p:processor>

    <!-- Render portlets and aggregate their outputs -->
    <p:for-each href="#portal-status" select="/portal-status/portlet-instance" root="aggregated-portlets" id="aggregated-portlets">

        <!-- Create the portlet configuration -->
        <p:processor name="oxf:xslt">
            <p:input name="data" href="current()"/>
            <p:input name="instance" href="#instance"/>
            <p:input name="config">
                <config xsl:version="2.0">
                    <xsl:variable name="instance" select="doc('oxf:instance')/*" as="element()"/>
                    <xsl:variable name="portlet-instance" select="/*" as="element()"/>

                    <!-- Portal id -->
                    <portal-id>examples</portal-id>

                    <!-- Portlet id -->
                    <xsl:copy-of select="$portlet-instance/portlet-id"/>

                    <!-- Optional Render Parameters -->
                    <xsl:if test="$instance/action = 'show-example'">
                        <render-parameters>
                            <param>
                                <name>oxf.path</name>
                                <value><xsl:value-of select="concat('/', $instance/example-id)"/></value>
                            </param>
                        </render-parameters>
                    </xsl:if>
                    <window-state>
                        <xsl:value-of select="if (/*/portlet-name = $instance/visible-portlet) then 'normal' else 'minimized'"/>
                    </window-state>
                </config>
            </p:input>
            <p:output name="data" id="portlet-include-config"/>
        </p:processor>

        <p:processor name="oxf:portlet-include">
            <p:input name="config" href="#portlet-include-config"/>
            <p:output name="data" ref="aggregated-portlets"/>
        </p:processor>

    </p:for-each>

    <p:processor name="oxf:identity">
        <p:input name="data" href="aggregate('root', #portal-status, #aggregated-portlets)"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
