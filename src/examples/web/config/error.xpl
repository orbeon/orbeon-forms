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
            <xsl:stylesheet version="2.0" xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns="http://www.w3.org/1999/xhtml">
                <xsl:import href="oxf:/oxf/xslt/utils/utils.xsl"/>

                <xsl:variable name="servlet-class" as="xs:string" select="'org.orbeon.oxf.servlet.OXFServlet'"/>
                <xsl:variable name="portlet-class" as="xs:string" select="'org.orbeon.oxf.portlet.OXFPortlet'"/>

                <xsl:template match="/">
                    <html>
                        <head>
                            <title>Orbeon PresentationServer (OPS) - Error Page</title>
                        </head>
                        <body>
                            <script>
                                function hideShowTBody(id) {
                                    var tbody = document.getElementById(id);
                                    for (var i = 0; tbody.rows.length > i; i++) {
                                        var row = tbody.rows[i];
                                        if (row.style.display == 'none') row.style.display = '';
                                        else row.style.display = 'none';
                                    }
                                }
                            </script>
                            <div class="maincontent" style="border: none">
                            <h1>Orbeon PresentationServer (OPS) - Error Page</h1>
                            <h2>Error Message</h2>
                            <p>
                                The following error has occurred:
                            </p>
                            <div class="frame warning">
                                <div class="label">Error Message</div>
                                <div class="content">
                                    <p>
                                        <xsl:value-of select="/exceptions/exception[1]/message"/>
                                    </p>
                                </div>
                           </div>
                            <h2>OPS Call Stack</h2>
                            <p>
                                The OPS Call Stack helps you determine what sequence of OPS
                                operations have caused the error.
                            </p>
                            <table class="gridtable">
                                <tr>
                                    <th>Resource URL</th>
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
                            <p>
                                Java Exceptions are the native mechanism by which OPS reports
                                errors. More than one exception may be provided below but usually
                                the first exception along with the OPS Stack Trace above provide
                                enough information to track down an issue.
                            </p>
                            <table class="gridtable" width="100%">
                                <xsl:for-each select="/exceptions/exception">
                                    <xsl:sort select="position()" order="descending"/>
                                    <xsl:variable name="exception-position" select="position()"/>
                                    <tr>
                                        <th colspan="2" style="text-align: left">
                                            <span onclick="hideShowTBody('exception-{$exception-position}')">
                                                <img src="/images/plus.gif" border="0" alt="Toggle"/>
                                            </span>
                                            <xsl:text> </xsl:text>
                                            <xsl:value-of select="type"/>
                                        </th>
                                    </tr>
                                    <xsl:variable name="exception-style" select="concat('display: ', if ($exception-position = 1) then '' else 'none')"/>
                                    <tbody id="exception-{$exception-position}">
                                        <tr style="{$exception-style}">
                                            <th>Exception Class</th>
                                            <td>
                                                <xsl:value-of select="type"/>
                                            </td>
                                        </tr>
                                        <tr style="{$exception-style}">
                                            <th>Message</th>
                                            <td style="color: red">
                                                <xsl:call-template name="htmlize-line-breaks">
                                                    <xsl:with-param name="text" select="replace(string(message), ' ', '&#160;')"/>
                                                </xsl:call-template>
                                            </td>
                                        </tr>
                                        <xsl:for-each select="location[1]">
                                            <tr style="{$exception-style}">
                                                <th>Resource URL</th>
                                                <td>
                                                    <xsl:value-of select="system-id"/>
                                                </td>
                                            </tr>
                                            <tr style="{$exception-style}">
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
                                            <tr style="{$exception-style}">
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

                                        <xsl:variable name="has-portlet-servlet" as="xs:boolean"
                                                select="stack-trace-elements/element/class-name = $servlet-class and stack-trace-elements/element/class-name = $portlet-class"/>

                                        <xsl:variable name="portlet-stack-trace" as="element()*"
                                                      select="if ($has-portlet-servlet) then stack-trace-elements/element[class-name = $portlet-class]/(., preceding-sibling::element) else ()"/>

                                        <xsl:variable name="servlet-stack-trace" as="element()*"
                                                      select="if ($has-portlet-servlet) then stack-trace-elements/element[class-name = $portlet-class]/following-sibling::element else stack-trace-elements/element"/>

                                        <xsl:if test="$has-portlet-servlet">
                                            <xsl:for-each-group select="$portlet-stack-trace" group-ending-with="element[class-name = $portlet-class]">
                                                <tr style="{$exception-style}">
                                                    <th valign="top">Portlet Stack Trace<br/>(<xsl:value-of select="count(current-group())"/> method calls)</th>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="current-group()">
                                                                <xsl:call-template name="display-stack-trace">
                                                                    <xsl:with-param name="elements" select="current-group()"/>
                                                                    <xsl:with-param name="trace-id" select="concat($exception-position, '-portlet-', position())"/>
                                                                </xsl:call-template>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <code>
                                                                    <xsl:value-of select="stack-trace"/>
                                                                </code>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                </tr>
                                            </xsl:for-each-group>
                                        </xsl:if>
                                        <xsl:for-each-group select="$servlet-stack-trace" group-ending-with="element[class-name = $servlet-class]">
                                            <tr style="{$exception-style}">
                                                <th valign="top">Servlet Stack Trace<br/>(<xsl:value-of select="count(current-group())"/> method calls)</th>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="current-group()">
                                                            <xsl:call-template name="display-stack-trace">
                                                                <xsl:with-param name="elements" select="current-group()"/>
                                                                <xsl:with-param name="trace-id" select="concat($exception-position, '-servlet-', position())"/>
                                                            </xsl:call-template>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <code>
                                                                <xsl:value-of select="stack-trace"/>
                                                            </code>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                        </xsl:for-each-group>
                                    </tbody>
                                </xsl:for-each>
                            </table>
                            </div>
                        </body>
                    </html>
                </xsl:template>
                <xsl:template name="display-stack-trace">
                    <xsl:param name="elements" as="element()*"/>
                    <xsl:param name="trace-id" as="xs:string"/>
                    <table class="gridtable" width="100%">
                        <thead>
                            <tr>
                                <th>Class Name</th>
                                <th>Method Name</th>
                                <th>File Name</th>
                                <th>Line Number</th>
                            </tr>
                        </thead>
                        <tbody>
                            <xsl:for-each select="$elements[position() le 10]">
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
                                    <span onclick="hideShowTBody('trace-{$trace-id}')">
                                        <img src="/images/plus.gif" border="0" alt="Toggle"/> More...
                                    </span>
                                </td>
                            </tr>
                        </tbody>
                        <tbody id="trace-{$trace-id}">
                            <xsl:for-each select="$elements[position() gt 10]">
                                <tr style="display: none">
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
