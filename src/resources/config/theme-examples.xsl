<!--
  Copyright (C) 2009 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:version="java:org.orbeon.oxf.common.Version">

    <!-- XML formatting -->
    <xsl:import href="oxf:/ops/utils/formatting/formatting.xsl"/>

    <!-- This contains some useful request information -->
    <xsl:variable name="request" select="doc('input:request')" as="document-node()"/>

    <!-- List of applications -->
    <xsl:variable name="applications" select="doc('../apps-list.xml')" as="document-node()"/>
    <!-- Current application id -->
    <xsl:variable name="current-application-id" select="tokenize(doc('input:request')/*/request-path, '/')[2]" as="xs:string"/>
    <!-- Source viewer application id if relevant -->
    <xsl:variable name="is-source-viewer" select="$current-application-id = 'source-viewer'" as="xs:boolean"/>
    <xsl:variable name="source-viewer-application-id" select="if ($is-source-viewer) then tokenize(doc('input:request')/*/request-path, '/')[3] else ()" as="xs:string?"/>
    <!-- Try to obtain a meaningful title for the example -->
    <xsl:variable name="title" select="if (/xhtml:html/xhtml:head/xhtml:title != '')
                                       then /xhtml:html/xhtml:head/xhtml:title
                                       else if (/xhtml:html/xhtml:body/xhtml:h1)
                                            then (/xhtml:html/xhtml:body/xhtml:h1)[1]
                                            else '[Untitled]'" as="xs:string"/>
    <!-- Orbeon Forms version -->
    <xsl:variable name="orbeon-forms-version" select="version:getVersion()" as="xs:string"/>

    <!-- - - - - - - Themed page template - - - - - - -->
    <xsl:template match="/">
        <xhtml:html>
            <xsl:apply-templates select="/xhtml:html/@*"/>
            <xhtml:head>
                <!-- Add meta as early as possible -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/xhtml:meta"/>
                <xhtml:title>Orbeon Forms Example Applications - <xsl:value-of select="$title"/></xhtml:title>
                <!-- NOTE: The XForms engine may place additional scripts and stylesheets here as needed -->
                <xhtml:link rel="stylesheet" href="/config/theme/orbeon.css" type="text/css" media="all"/>
                <!-- Handle head elements except scripts -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/(xhtml:link | xhtml:style)"/>
                <!-- Orbeon Forms version -->
                <xhtml:meta name="generator" content="Orbeon Forms {$orbeon-forms-version}"/>
                <!-- Favicon -->
                <xhtml:link rel="shortcut icon" href="/ops/images/orbeon-icon-16.ico"/>
                <xhtml:link rel="icon" href="/ops/images/orbeon-icon-16.png" type="image/png"/>
            </xhtml:head>
            <xhtml:body>
                <!-- Copy body attributes -->
                <xsl:apply-templates select="/xhtml:html/xhtml:body/@*"/>

                <xhtml:table id="main" width="100%" border="0" cellpadding="0" cellspacing="0">
                    <!-- Banner (with search) -->
                    <xhtml:tr>
                        <xhtml:td colspan="2" id="banner">
                            <xhtml:div style="float: left">
                                <xhtml:a href="/" f:url-norewrite="true">
                                    <xhtml:img f:url-norewrite="false" width="212" height="42"
                                               style="border: 0 white; margin-left: 1em; margin-top: 0.2em; margin-bottom: 0.4em"
                                               src="/config/theme/images/orbeon-logo-trimmed-transparent-42.png" alt='home'/>
                                </xhtml:a>
                            </xhtml:div>
                            <xhtml:span style="float: right; margin-right: 1em; margin-top: .2em; white-space: nowrap">
                                <form method="GET" class="blue" style="margin:0.2em; margin-bottom:0"
                                      action="http://www.google.com/custom">
                                    <xhtml:a href="http://www.orbeon.com/" f:url-norewrite="true">Orbeon.com</xhtml:a>
                                    |
                                    <xhtml:a href="/doc/">Documentation</xhtml:a>
                                    |
                                    <xhtml:span style="white-space: nowrap">
                                        Search:
                                        <input type="text" name="q" size="10" maxlength="255" value=""/>
                                        <input type="submit" name="sa" value="Go" style="margin-left: 0.2em;"/>
                                    </xhtml:span>
                                    <input type="hidden" name="cof"
                                           value="GIMP:#FF9900;T:black;LW:510;ALC:#FF9900;L:http://www.orbeon.com/pics/orbeon-google.png;GFNT:#666699;LC:#666699;LH:42;BGC:#FFFFFF;AH:center;VLC:#666699;GL:0;S:http://www.orbeon.com;GALT:#FF9900;AWFID:8ac636f034abb7d8;"/>
                                    <input type="hidden" name="sitesearch" value="orbeon.com"/>
                                </form>
                            </xhtml:span>
                        </xhtml:td>
                    </xhtml:tr>
                    <!-- Tabs -->
                    <xhtml:tr>
                        <xhtml:td colspan="2">
                            <xhtml:div class="tabs">
                                <xsl:choose>
                                    <xsl:when test="$is-source-viewer">
                                        <xhtml:a class="tab" href="/{$source-viewer-application-id}/">Run Application</xhtml:a>
                                        <xhtml:span class="tab-selected-left">&#160;</xhtml:span>
                                        <xhtml:span class="tab-selected">View Source Code</xhtml:span>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xhtml:span class="tab-selected-left">&#160;</xhtml:span>
                                        <xhtml:span class="tab-selected">Run Application</xhtml:span>
                                        <xhtml:a class="tab" href="/source-viewer/{$current-application-id}/">View Source Code</xhtml:a>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xhtml:div>
                        </xhtml:td>
                    </xhtml:tr>
                    <xhtml:tr>
                        <!-- List of examples -->
                        <xhtml:td id="leftcontent" valign="top" width="1%">
                            <h1>Orbeon Forms Apps</h1>
                            <xhtml:ul class="tree-sections">
                                <xsl:for-each select="$applications/*/section">
                                    <xhtml:li class="tree-section">
                                        <xsl:value-of select="@label"/>
                                    </xhtml:li>
                                    <xhtml:ul class="tree-items">
                                        <xsl:for-each select="application">
                                            <xsl:variable name="selected" as="xs:boolean" select="@id = $current-application-id"/>
                                            <xhtml:li class="{if ($selected) then 'tree-items-selected' else 'tree-items'}" style="white-space: nowrap">
                                                <xsl:choose>
                                                    <xsl:when test="$selected">
                                                        <xsl:value-of select="@label"/>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <xhtml:a href="/{@id}{if (tokenize(@id, '/')[1] != 'fr') then '/' else ''}">
                                                            <xsl:value-of select="@label"/>
                                                        </xhtml:a>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                                <!--<xsl:if test="@xforms-ng = 'true'">-->
                                                    <!--&#160;-->
                                                    <!--<xhtml:span style="font-size: 9px; color: #f90; font-weight: normal">-->
                                                        <!--XForms NG-->
                                                    <!--</xhtml:span>-->
                                                <!--</xsl:if>-->
                                            </xhtml:li>
                                        </xsl:for-each>
                                    </xhtml:ul>
                                </xsl:for-each>
                            </xhtml:ul>
                        </xhtml:td>
                        <xhtml:td id="maincontent" valign="top" width="99%">
                            <xhtml:div class="maincontent">
                                <!-- Title -->
                                <xhtml:h1>
                                    <!-- Title -->
                                    <xsl:value-of select="$title"/>
                                </xhtml:h1>
                                <!-- Body -->
                                <xhtml:div id="mainbody">
                                    <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
                                </xhtml:div>
                            </xhtml:div>
                        </xhtml:td>
                    </xhtml:tr>
                </xhtml:table>
                <xhtml:p class="ops-version">Orbeon Forms <xsl:value-of select="$orbeon-forms-version"/></xhtml:p>
            </xhtml:body>
            <!-- Scripts at the bottom of the page. This is not valid HTML, but it is a recommended practice for
                 performance as of early 2008. See http://developer.yahoo.com/performance/rules.html#js_bottom -->
            <xsl:for-each select="/xhtml:html/xhtml:head/xhtml:script">
                <xsl:element name="xhtml:{local-name()}" namespace="{namespace-uri()}">
                    <xsl:apply-templates select="@*|node()"/>
                </xsl:element>
            </xsl:for-each>
        </xhtml:html>
    </xsl:template>

    <!-- Simply copy everything that's not matched -->
    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
