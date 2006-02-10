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
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:ui="http://orbeon.org/oxf/xml/examples/ui">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="@ui:*"/>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="instance-no-ui"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance-no-ui"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="/config/theme/formatting.xsl"/>
                <xsl:import href="/config/theme/theme.xsl"/>
                <xsl:output method="html" omit-xml-declaration="yes" name="html"/>
                <xsl:template match="/">
                    <xsl:variable name="formatted">
                        <xsl:apply-templates mode="xml-formatting"/>
                    </xsl:variable>
                    <formatted-instance>
                        <xsl:value-of select="saxon:serialize($formatted, 'html')"/>
                    </formatted-instance>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
