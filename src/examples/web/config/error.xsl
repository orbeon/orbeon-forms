<!--
    Copyright (C) 2005 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<!--

  This stylesheet is called from error.xpl and is used to format OPS and Java stack traces.
  Developers can customize this at will.

-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:xhtml="http://www.w3.org/1999/xhtml"
                xmlns:saxon="http://saxon.sf.net/"
                xmlns:f="http://orbeon.org/oxf/xml/formatting"
                xmlns="http://www.w3.org/1999/xhtml">

    <xsl:import href="oxf:/oxf/xslt/utils/utils.xsl"/>

    <xsl:variable name="servlet-classes" as="xs:string+" select="('org.orbeon.oxf.servlet.OPSServlet', 'org.orbeon.oxf.servlet.OXFServlet')"/>
    <xsl:variable name="portlet-classes" as="xs:string+" select="('org.orbeon.oxf.portlet.OPSPortlet', 'org.orbeon.oxf.portlet.OPSPortlet')"/>

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
                <div class="maincontent">
                    <h1>Orbeon PresentationServer (OPS) - Error Page</h1>
                    <h2>Error Message</h2>
                    <p>
                        The following error has occurred:
                    </p>
                    <div class="frame warning">
                        <div class="label">Error Message</div>
                        <div class="content">
                            <p>
                                <xsl:variable name="message" as="xs:string" select="/exceptions/exception[last()]/message"/>
                                <xsl:choose>
                                    <xsl:when test="normalize-space($message) != ''">
                                        <xsl:choose>
                                            <xsl:when test="starts-with($message, 'Condition failed for every branch of choose') and contains($message, '/request/request-path,')">
                                                <!-- Handle specific message for PFC -->
                                                <xsl:text>Requested path doesn't match any existing page flow entry:</xsl:text>
                                                <xsl:variable name="parts1" select="tokenize(substring-after($message, '('), '\)[^\)]+\(')" as="xs:string*"/>
                                                <xsl:variable name="parts2" select="for $i in $parts1 return concat(if (contains($i, ',')) then 'Suffix: ' else 'Path: ', substring-before(substring-after($i, ''''), ''''))"/>
                                                <ul>
                                                    <xsl:for-each select="$parts2">
                                                        <li>
                                                            <xsl:value-of select="."/>
                                                        </li>
                                                    </xsl:for-each>
                                                </ul>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <!-- Handle any other message -->
                                                <xsl:value-of select="$message"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <i>[No error message provided.]</i>
                                    </xsl:otherwise>
                                </xsl:choose>
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
                            <th>Description</th>
                            <th>XML Element</th>
                        </tr>
                        <!-- Group so that if by any chance multiple location data for the same point occur, we show only one -->
                        <xsl:choose>
                            <xsl:when test="/exceptions/exception[location][1]/location[line castable as xs:positiveInteger and not(ends-with(system-id, '.java'))]">
                                <xsl:for-each-group select="/exceptions/exception[location][1]/location[line castable as xs:positiveInteger and not(ends-with(system-id, '.java'))]"
                                        group-by="concat(system-id, '-', line, '-', column)">
                                    <tr>
                                        <td><xsl:value-of select="system-id"/></td>
                                        <td><xsl:value-of select="if (line castable as xs:positiveInteger) then line else 'N/A'"/></td>
                                        <td><xsl:value-of select="if (column castable as xs:positiveInteger) then column else 'N/A'"/></td>
                                        <td>
                                            <xsl:for-each select="current-group()[description != '']">
                                                <xsl:if test="position() > 1">
                                                    <br/>
                                                </xsl:if>
                                                <xsl:value-of select="description"/>
                                            </xsl:for-each>
                                            <xsl:if test="current-group()[parameters/parameter]">
                                                <span style="font-size: smaller">
                                                    <xsl:text> (</xsl:text>
                                                        <xsl:for-each select="current-group()/parameters/parameter[value != '']">
                                                            <xsl:if test="position() > 1">
                                                                <xsl:text>, </xsl:text>
                                                            </xsl:if>
                                                            <xsl:value-of select="concat(name, '=''', value, '''')"/>
                                                        </xsl:for-each>
                                                    <xsl:text>)</xsl:text>
                                                </span>
                                            </xsl:if>
                                        </td>
                                        <td>
                                            <xsl:for-each select="current-group()[element != '']">
                                                <xsl:if test="position() > 1">
                                                    <br/>
                                                </xsl:if>
                                                <xsl:variable name="element" as="element()">
                                                    <xsl:copy-of select="saxon:parse(element)/*"/>
                                                </xsl:variable>
                                                <xsl:variable name="just-element" as="element()">
                                                    <xsl:for-each select="$element">
                                                        <xsl:copy>
                                                            <xsl:copy-of select="@*"/>
                                                            <xsl:if test="*">
                                                                <xsl:text>...</xsl:text>
                                                            </xsl:if>
                                                        </xsl:copy>
                                                    </xsl:for-each>
                                                </xsl:variable>
                                                <!-- NOTE: use $just-element to show the enclosing element, and $element to show the element with content -->
                                                <f:xml-source show-namespaces="false">
                                                    <xsl:copy-of select="$just-element"/>
                                                </f:xml-source>
                                            </xsl:for-each>
                                        </td>
                                    </tr>
                                </xsl:for-each-group>
                            </xsl:when>
                            <xsl:otherwise>
                                <tr>
                                    <td colspan="5">
                                        <i>There is no OPS call stack available for this error.</i>
                                    </td>
                                </tr>
                            </xsl:otherwise>
                        </xsl:choose>
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
                                            <xsl:value-of select="if (line castable as xs:positiveInteger) then line else 'N/A'"/>
                                        </td>
                                    </tr>
                                    <tr style="{$exception-style}">
                                        <th>Column</th>
                                        <td>
                                            <xsl:value-of select="if (column castable as xs:positiveInteger) then column else 'N/A'"/>
                                        </td>
                                    </tr>
                                </xsl:for-each>

                                <xsl:variable name="has-portlet-servlet" as="xs:boolean"
                                        select="stack-trace-elements/element/class-name = $servlet-classes and stack-trace-elements/element/class-name = $portlet-classes"/>

                                <xsl:variable name="portlet-stack-trace" as="element()*"
                                              select="if ($has-portlet-servlet) then stack-trace-elements/element[class-name = $portlet-classes]/(., preceding-sibling::element) else ()"/>

                                <xsl:variable name="servlet-stack-trace" as="element()*"
                                              select="if ($has-portlet-servlet) then stack-trace-elements/element[class-name = $portlet-classes]/following-sibling::element else stack-trace-elements/element"/>

                                <xsl:if test="$has-portlet-servlet">
                                    <xsl:for-each-group select="$portlet-stack-trace" group-ending-with="element[class-name = $portlet-classes]">
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
                                <xsl:for-each-group select="$servlet-stack-trace" group-ending-with="element[class-name = $servlet-classes]">
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
                        <td style="color: {if (contains(class-name, 'org.orbeon') and not(contains(class-name, 'org.orbeon.saxon'))) then 'green' else 'black'}">
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
                        <td style="color: {if (contains(class-name, 'org.orbeon') and not(contains(class-name, 'org.orbeon.saxon'))) then 'green' else 'black'}">
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
