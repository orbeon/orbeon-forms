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
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Generate exception document -->
    <p:processor name="oxf:exception">
        <p:output name="data" id="exception"/>
    </p:processor>

    <!-- Format exception page -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#exception"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/utils.xsl"/>
                <xsl:template match="/">
                    <html xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns="http://www.w3.org/1999/xhtml">
                        <head>
                            <title>Orbeon PresentationServer (OPS) - Error Page</title>
                        </head>
                        <body>
                            <h1>Orbeon PresentationServer (OPS) - Error Page</h1>
                            <h2>OPS Call Stack</h2>
                            <table class="gridtable">
                                <tr>
                                    <th>System Id</th>
                                    <th>Line</th>
                                    <th>Column</th>
                                </tr>
                                <xsl:for-each select="/exceptions/exception[location][1]/location[line castable as xs:positiveInteger and not(ends-with(system-id, '.java'))]">
                                    <tr>
                                        <td><xsl:value-of select="system-id"/></td>
                                        <td><xsl:value-of select="line"/></td>
                                        <td><xsl:value-of select="column"/></td>
                                    </tr>
                                </xsl:for-each>
                            </table>
                            <h2>Java Exceptions (<xsl:value-of select="count(/exceptions/exception)"/> total)</h2>
                            <table class="gridtable" width="100%">
                                <xsl:for-each select="/exceptions/exception">
                                    <xsl:sort select="position()" order="descending"/>
                                    <xsl:variable name="exception-position" select="position()"/>
                                    <tr>
                                        <th colspan="2" style="text-align: left">
                                            <span onclick="getElementById('exception-{$exception-position}').style.display = 'table-row-group'">
                                                <img src="/images/plus.gif" border="0" alt="Toggle"/>
                                            </span>
                                            <xsl:text> </xsl:text>
                                            <xsl:value-of select="type"/>
                                        </th>
                                    </tr>
                                    <tbody style="display: {if ($exception-position = 1) then 'table-row-group' else 'none'}" id="exception-{$exception-position}">
                                        <tr>
                                            <th>Exception Class</th>
                                            <td>
                                                <xsl:value-of select="type"/>
                                            </td>
                                        </tr>
                                        <tr>
                                            <th>Message</th>
                                            <td>
                                                <xsl:call-template name="htmlize-line-breaks">
                                                    <xsl:with-param name="text" select="replace(string(message), ' ', '&#160;')"/>
                                                </xsl:call-template>
                                            </td>
                                        </tr>
                                        <xsl:for-each select="location[1]">
                                            <tr>
                                                <th>Location</th>
                                                <td>
                                                    <xsl:value-of select="system-id"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <th>Line</th>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="line castable as xs:positiveInteger">
                                                            <xsl:value-of select="line"/>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            N/A
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                            <tr>
                                                <th>Column</th>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="column castable as xs:positiveInteger">
                                                            <xsl:value-of select="column"/>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            N/A
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                        </xsl:for-each>
                                        <tr>
                                            <th valign="top">Stack Trace<br/>(<xsl:value-of select="count(stack-trace-elements/element)"/> method calls)</th>
                                            <td>
                                                <xsl:choose>
                                                    <xsl:when test="stack-trace-elements">
                                                        <table class="gridtable" width="100%">
                                                            <tr>
                                                                <th>Class Name</th>
                                                                <th>Method Name</th>
                                                                <th>File Name</th>
                                                                <th>Line Number</th>
                                                            </tr>
                                                            <xsl:for-each select="stack-trace-elements/element[position() le 10]">
                                                                <tr>
                                                                    <td style="color: {if (contains(class-name, 'org.orbeon')) then 'green' else 'black'}">
                                                                        <xsl:value-of select="class-name"/>
                                                                    </td>
                                                                    <td><xsl:value-of select="method-name"/></td>
                                                                    <td><xsl:value-of select="file-name"/></td>
                                                                    <td>
                                                                        <xsl:choose>
                                                                            <xsl:when test="line-number castable as xs:positiveInteger">
                                                                                <xsl:value-of select="line-number"/>
                                                                            </xsl:when>
                                                                            <xsl:otherwise>
                                                                                N/A
                                                                            </xsl:otherwise>
                                                                        </xsl:choose>
                                                                    </td>
                                                                </tr>
                                                            </xsl:for-each>
                                                            <tr>
                                                                <td colspan="4">
                                                                    <span onclick="getElementById('trace-{$exception-position}').style.display = 'table-row-group'">
                                                                        <img src="/images/plus.gif" border="0" alt="Toggle"/> More...
                                                                    </span>
                                                                </td>
                                                            </tr>
                                                            <tbody style="display: none" id="trace-{$exception-position}">
                                                                <xsl:for-each select="stack-trace-elements/element[position() gt 10]">
                                                                <!--<tbody style="visibility: collapse" id="trace">-->
                                                                    <tr>
                                                                        <td style="color: {if (contains(class-name, 'org.orbeon')) then 'green' else 'black'}">
                                                                            <xsl:value-of select="class-name"/>
                                                                        </td>
                                                                        <td><xsl:value-of select="method-name"/></td>
                                                                        <td><xsl:value-of select="file-name"/></td>
                                                                        <td>
                                                                            <xsl:choose>
                                                                                <xsl:when test="line-number castable as xs:positiveInteger">
                                                                                    <xsl:value-of select="line-number"/>
                                                                                </xsl:when>
                                                                                <xsl:otherwise>
                                                                                    N/A
                                                                                </xsl:otherwise>
                                                                            </xsl:choose>
                                                                        </td>
                                                                    </tr>
                                                                </xsl:for-each>
                                                            </tbody>
                                                        </table>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <code>
                                                            <xsl:value-of select="stack-trace"/>
                                                        </code>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </td>
                                        </tr>
                                    </tbody>
                                </xsl:for-each>
                            </table>
                        </body>
                    </html>
                </xsl:template>
                <xsl:template name="htmlize-line-breaks">
                    <xsl:param name="text"/>
                    <xsl:choose>
                        <xsl:when test="contains($text, '&#13;')">
                            <xsl:value-of select="substring-before($text, '&#13;')"/>
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

    <!-- Get some request information -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/container-type</include>
                <include>/request/request-path</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Apply theme -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#document"/>
        <p:input name="request" href="#request"/>
        <p:input name="config" href="oxf:/config/theme/theme.xsl"/>
        <p:output name="data" id="themed"/>
    </p:processor>

    <!-- Rewrite all URLs in XHTML documents -->
    <p:processor name="oxf:xhtml-rewrite">
        <p:input name="rewrite-in" href="#themed"/>
        <p:output name="rewrite-out" id="rewritten-data"/>
    </p:processor>

    <!-- Convert to HTML -->
    <p:processor name="oxf:qname-converter">
        <p:input name="config">
            <config>
                <match>
                    <uri>http://www.w3.org/1999/xhtml</uri>
                </match>
                <replace>
                    <uri></uri>
                    <prefix></prefix>
                </replace>
            </config>
        </p:input>
        <p:input name="data" href="#rewritten-data"/>
        <p:output name="data" id="html-data"/>
    </p:processor>

    <p:processor name="oxf:html-converter">
        <p:input name="config">
            <config>
                <public-doctype>-//W3C//DTD HTML 4.01 Transitional//EN</public-doctype>
                <version>4.01</version>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#html-data"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <!-- Serialize -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <status-code>500</status-code>
                <header>
                    <name>Cache-Control</name>
                    <value>post-check=0, pre-check=0</value>
                </header>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>
