<!--
    Copyright (C) 2005-2007 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:pipeline xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Generate exception document -->
    <p:processor name="oxf:exception">
        <p:output name="data" id="exception"/>
    </p:processor>

    <!-- Format exception -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#exception"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" saxon:allow-all-built-in-types="yes">
                <xsl:import href="oxf:/config/error.xsl"/>
                <xsl:template match="/">
                    <error>
                        <title>
                            <xsl:variable name="message" select="(/exceptions/exception[last()]/message, 'An error has occurred')[normalize-space(.) != ''][1]"/>
                            <xsl:choose>
                                <!-- This is a bit of a hack to display a nicer "session expired" message (see XFormsStateManager) -->
                                <xsl:when test="contains($message, 'Your session has expired')">Your session has expired</xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="$message"/>
                                </xsl:otherwise>
                            </xsl:choose>
                        </title>
                        <body>
                            <xsl:call-template name="format-xforms-error-panel-body">
                                <xsl:with-param name="exceptions" select="/exceptions/exception"/>
                            </xsl:call-template>
                        </body>
                    </error>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="formatted-exception"/>
    </p:processor>

    <!-- Serialize the content of the message to facilitate handling on the client -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#formatted-exception"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" saxon:allow-all-built-in-types="yes">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:import href="oxf:/ops/utils/formatting/formatting.xsl"/>
                <xsl:output method="html" name="html"/>
                <xsl:template match="/error/body">
                    <xsl:copy>
                        <xsl:variable name="xhtml-body" as="element()" select="*[1]"/>
                        <xsl:variable name="html-body" as="element()">
                            <xsl:apply-templates select="$xhtml-body"/>
                        </xsl:variable>
                        <xsl:value-of select="saxon:serialize($html-body, 'html')"/>
                    </xsl:copy>
                </xsl:template>
                <!-- NOTE: We should probably use oxf:qname-converter instead, but it has a bug that prevents this use case to work -->
                <xsl:template match="xhtml:*">
                    <xsl:element name="{local-name()}">
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>
                <xsl:template match="@xhtml:*">
                    <xsl:attribute name="{local-name()}" select="."/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="response-message"/>
    </p:processor>

    <!-- Serialize to XML -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config/>
        </p:input>
        <p:input name="data" href="#response-message"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <!-- Send response -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <status-code>500</status-code>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:pipeline>
