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
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Generate exception document -->
    <p:processor name="oxf:exception">
        <p:output name="data" id="exception"/>
    </p:processor>

    <!-- Apply stylesheet -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#exception"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0" >
                <xsl:import href="oxf:/oxf/xslt/utils/utils.xsl"/>
                <xsl:template match="/">
                    <xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">
                        <xhtml:head>
                            <xhtml:title>Presentation Server - Error Page</xhtml:title>
                        </xhtml:head>
                        <xhtml:body>
                            <xhtml:h1>An error occured</xhtml:h1>

                            <xhtml:table class="gridtable">
                                <xhtml:tr>
                                    <xhtml:th>Type</xhtml:th>
                                    <xhtml:td>
                                        <xsl:value-of select="/exceptions/exception/type"/>
                                    </xhtml:td>
                                </xhtml:tr>
                                <xhtml:tr>
                                    <xhtml:th>Message</xhtml:th>
                                    <xhtml:td>
                                        <xsl:call-template name="htmlize-line-breaks">
                                            <xsl:with-param name="text" select="replace(string(/exceptions/exception/message), ' ', '&#160;')"/>
                                        </xsl:call-template>
                                    </xhtml:td>
                                </xhtml:tr>
                                <xhtml:tr>
                                    <xhtml:th>Location</xhtml:th>
                                    <xhtml:td>
                                        <xsl:value-of select="/exceptions/exception/system-id"/>
                                    </xhtml:td>
                                </xhtml:tr>
                                <xhtml:tr>
                                    <xhtml:th>Line</xhtml:th>
                                    <xhtml:td>
                                        <xsl:choose>
                                            <xsl:when test="string(number(/exceptions/exception/line)) != 'NaN' and /exceptions/exception/line > 0">
                                                <xsl:value-of select="/exceptions/exception/line"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                N/A
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xhtml:td>
                                </xhtml:tr>
                                <xhtml:tr>
                                    <xhtml:th>Column</xhtml:th>
                                    <xhtml:td>
                                        <xsl:choose>
                                            <xsl:when test="string(number(/exceptions/exception/column)) != 'NaN' and /exceptions/exception/column > 0">
                                                <xsl:value-of select="/exceptions/exception/column"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                N/A
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xhtml:td>
                                </xhtml:tr>
                                <xhtml:tr>
                                    <xhtml:th valign="top">Stack Trace</xhtml:th>
                                    <xhtml:td>
                                        <xsl:choose>
                                            <xsl:when test="/exceptions/exception/stack-trace-elements">
                                                <xhtml:table class="gridtable" width="100%">
                                                    <xhtml:tr>
                                                        <xhtml:th>Class Name</xhtml:th>
                                                        <xhtml:th>Method Name</xhtml:th>
                                                        <xhtml:th>File Name</xhtml:th>
                                                        <xhtml:th>Line Number</xhtml:th>
                                                    </xhtml:tr>
                                                    <xsl:for-each select="/exceptions/exception/stack-trace-elements/element">
                                                        <xhtml:tr>
                                                            <xhtml:td><xsl:value-of select="class-name"/></xhtml:td>
                                                            <xhtml:td><xsl:value-of select="method-name"/></xhtml:td>
                                                            <xhtml:td><xsl:value-of select="file-name"/></xhtml:td>
                                                            <xhtml:td>
                                                                <xsl:choose>
                                                                    <xsl:when test="string(number(line-number)) != 'NaN' and line-number > 0">
                                                                        <xsl:value-of select="line-number"/>
                                                                    </xsl:when>
                                                                    <xsl:otherwise>
                                                                        N/A
                                                                    </xsl:otherwise>
                                                                </xsl:choose>
                                                            </xhtml:td>
                                                        </xhtml:tr>
                                                    </xsl:for-each>
                                                </xhtml:table>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <code>
                                                    <xsl:value-of select="/exceptions/exception/stack-trace"/>
                                                </code>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xhtml:td>
                                </xhtml:tr>
                            </xhtml:table>

                        </xhtml:body>
                    </xhtml:html>
                </xsl:template>
                <xsl:template name="htmlize-line-breaks">
                    <xsl:param name="text"/>
                    <xsl:choose>
                        <xsl:when test="contains($text, '&#10;')">
                            <xsl:value-of select="substring-before($text, '&#10;')"/>
                            <br/>
                            <xsl:call-template name="htmlize-line-breaks">
                                <xsl:with-param name="text" select="substring-after($text, '&#10;')"/>
                            </xsl:call-template>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="$text"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="document"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#document"/>
        <p:input name="config" href="oxf:/oxf-theme/theme.xsl"/>
        <p:output name="data" id="themed"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#themed"/>
        <p:input name="config" href="oxf:/oxf/pfc/oxf-rewrite.xsl"/>
        <p:output name="data" id="html"/>
    </p:processor>

    <p:processor name="oxf:html-serializer">
        <p:input name="config">
            <config>
                <status-code>500</status-code>
            </config>
        </p:input>
        <p:input name="data" href="#html"/>
    </p:processor>

</p:config>