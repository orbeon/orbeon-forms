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
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:f="http://orbeon.org/oxf/xml/formatting"
          xmlns:portlet="http://www.orbeon.org/oxf/portlet"
          xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <p:param name="data" type="input"/>
    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="portal-status" href="#data#xpointer(/*/*[1])"/>
        <p:input name="aggregated-portlets" href="#data#xpointer(/*/*[2])"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">

                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

                <xsl:variable name="instance" select="/*" as="element()"/>
                <xsl:variable name="portlets" select="document('oxf:aggregated-portlets')/*/portlet:portlets/portlet:portlet" as="element()*"/>
                <xsl:variable name="portal-status" select="document('oxf:portal-status')" as="document-node()"/>

                <xsl:variable name="examples-list" select="document('oxf:/examples/examples-list.xml')" as="document-node()"/>

                <xsl:variable name="visible-portlet-name" select="$instance/visible-portlet" as="xs:string"/>
                <xsl:variable name="example-id" select="$instance/example-id" as="xs:string"/>
                <xsl:variable name="visible-portlet" select="$portlets[@name = $visible-portlet-name]" as="element()"/>
<!--                <xsl:variable name="oxf-path" select="$portal-status/*/portlet-instance[portlet-name = $visible-portlet-name]/render-parameters/param[name = 'oxf.path']/value[1]" as="xs:string?"/>-->

                <xsl:template match="/">
                    <xhtml:html>
                        <xhtml:head>
                            <xhtml:title>Presentation Server Examples</xhtml:title>
                        </xhtml:head>
                        <xhtml:body>
                            <!-- Banner (with search) -->
                            <div id="banner">
                                <div style="float: left">
                                    <a href="/" f:url-norewrite="true">
                                        <img f:url-norewrite="false" width="199" height="42" style="border: 0 white; margin-left: 1em; margin-top: 0.2em; margin-bottom: 0.4em" src="/oxf-theme/images/orbeon-small-blueorange.gif"/>
                                    </a>
                                </div>
                                <span style="float: right; margin-right: 1em; margin-top: .2em; white-space: nowrap">
                                    <form method="GET" class="blue" style="margin:0.2em; margin-bottom:0em" action="http://www.google.com/custom">
                                        <a href="http://www.orbeon.com/" f:url-norewrite="true">Orbeon.com</a> |
                                        <a href="doc/" f:url-norewrite="true">Documentation</a> |
                                        <span style="white-space: nowrap">
                                            Search:
                                            <input type="text" name="q" size="10" maxlength="255" value=""/>
                                            <input type="submit" name="sa" VALUE="Go" style="margin-left: 0.2em;"/>
                                        </span>
                                        <input type="hidden" name="cof" VALUE="GIMP:#FF9900;T:black;LW:510;ALC:#FF9900;L:http://www.orbeon.com/pics/orbeon-google.png;GFNT:#666699;LC:#666699;LH:42;BGC:#FFFFFF;AH:center;VLC:#666699;GL:0;S:http://www.orbeon.com;GALT:#FF9900;AWFID:8ac636f034abb7d8;"/>
                                        <input type="hidden" name="sitesearch" value="orbeon.com"/>
                                    </form>
                                </span>
                            </div>
                            <!-- Tabs -->
                            <div class="tabs">
                                <xf:group ref="/form" xhtml:style="margin-bottom: 0">
                                    <xsl:for-each select="$portlets">
                                        <xsl:choose>
                                            <xsl:when test="@name = $visible-portlet-name">
                                                <span class="tab-selected-left">&#160;</span>
                                                <span class="tab-selected"><xsl:value-of select="@short-title"/></span>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xf:submit xxf:appearance="link" xhtml:class="tab">
                                                    <xf:label><xsl:value-of select="@short-title"/></xf:label>
                                                    <xf:setvalue ref="action">show-portlet</xf:setvalue>
                                                    <xf:setvalue ref="visible-portlet"><xsl:value-of select="@name"/></xf:setvalue>
                                                </xf:submit>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:for-each>
                                </xf:group>
                            </div>
                            <div id="main">
                                <div id="main1">
                                    <!-- List of examples -->
                                    <div id="leftcontent">
                                        <h1>Presentation Server Examples</h1>
                                        <xf:group ref="/form">
                                            <ul class="tree-sections">
                                                <xsl:for-each select="$examples-list/*/section">
                                                    <li class="tree-section">
                                                        <xsl:value-of select="@label"/>
                                                    </li>
                                                    <ul class="tree-items">
                                                        <xsl:for-each select="example">
                                                            <xsl:variable name="selected" as="xs:boolean" select="@id = $example-id"/>
    <!--                                                        <xsl:variable name="selected" as="xs:boolean" select="false()"/>-->
                                                            <li class="{if ($selected) then 'tree-items-selected' else 'tree-items'}" style="white-space: nowrap">
                                                                <xsl:choose>
                                                                    <xsl:when test="@href">
                                                                        <a href="{@href}" xhtml:target="example"><xsl:value-of select="@label"/></a>
                                                                        <xsl:text>&#160;</xsl:text>
                                                                        <img src="/images/new-window.gif" align="middle"/>
                                                                    </xsl:when>
                                                                    <xsl:when test="$selected">
                                                                        <xsl:value-of select="@label"/>
                                                                    </xsl:when>
                                                                    <xsl:when test="@standalone = 'true'">
                                                                        <a href="/examples-standalone/{@id}" xhtml:target="example"><xsl:value-of select="@label"/></a>
                                                                        <xsl:text>&#160;</xsl:text>
                                                                        <img src="/images/new-window.gif" align="middle"/>
                                                                    </xsl:when>
                                                                    <xsl:otherwise>
                                                                        <xf:submit xxf:appearance="link">
                                                                            <xf:label><xsl:value-of select="@label"/></xf:label>
                                                                            <xf:setvalue ref="action">show-example</xf:setvalue>
                                                                            <xf:setvalue ref="example-id"><xsl:value-of select="@id"/></xf:setvalue>
                                                                        </xf:submit>
                                                                    </xsl:otherwise>
                                                                </xsl:choose>
                                                            </li>
                                                        </xsl:for-each>
                                                    </ul>
                                                </xsl:for-each>
                                            </ul>
                                        </xf:group>
                                    </div>
                                    <div id="maincontent">
                                        <div id="mainbody">
                                            <!-- Title -->
                                            <h1>
                                                <xsl:choose>
                                                    <xsl:when test="$visible-portlet/portlet:portlet-title">
                                                        <xsl:value-of select="$visible-portlet/portlet:portlet-title"/>
                                                    </xsl:when>
                                                    <xsl:otherwise>[Untitled]</xsl:otherwise>
                                                </xsl:choose>
                                            </h1>
                                            <!-- Body -->
                                            <xsl:if test="not($visible-portlet/@window-state = 'minimized')">
                                                <!-- Encapsulate everything in a div that makes sure URLs won't be rewritten further -->
                                                <div f:url-norewrite="true">
                                                    <xsl:choose>
                                                        <xsl:when test="$visible-portlet/portlet:body/html">
                                                            <!-- When parsing text/html with Tidy, we will have a root HTML element -->
                                                            <xsl:copy-of select="$visible-portlet/portlet:body//head[1]/link"/>
                                                            <xsl:copy-of select="$visible-portlet/portlet:body//head[1]/script"/>
                                                            <xsl:copy-of select="$visible-portlet/portlet:body//head[1]/style"/>

                                                            <xsl:apply-templates select="$visible-portlet/portlet:body//body[1]/node()">
                                                                <xsl:with-param name="portlet-id" select="$visible-portlet/@id" tunnel="yes"/>
                                                            </xsl:apply-templates>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <!-- Otherwise, just assume it is a fragment -->
                                                            <xsl:apply-templates select="$visible-portlet/portlet:body/node()">
                                                                <xsl:with-param name="portlet-id" select="$visible-portlet/@id" tunnel="yes"/>
                                                            </xsl:apply-templates>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </div>
                                            </xsl:if>
                                        </div>
                                    </div>
                                    <div class="cleaner">&#160;</div>
                                </div>
                            </div>
                        </xhtml:body>
                    </xhtml:html>
                </xsl:template>

                <!-- Annotate portlet forms with flag and portlet-id attributes -->
                <xsl:template match="form|xhtml:form">
                    <xsl:param name="portlet-id" tunnel="yes"/>

                    <xsl:copy>
                        <xsl:copy-of select="@*"/>

                        <xsl:attribute name="portlet:is-portlet-form" select="'true'"/>
                        <xsl:attribute name="portlet:form-portlet-id" select="$portlet-id"/>

                        <xsl:apply-templates/>
                    </xsl:copy>

                </xsl:template>

                <!-- Make sure resource URLs won't be rewritten down the line -->
<!--                <xsl:template match="a[@f:url-type = 'resource'] | xhtml:a[@f:url-type = 'resource']-->
<!--                                     | link[@href] | xhtml:link[@href]-->
<!--                                     | img[@src] | input[@type = 'image' and @src] | script[@src]-->
<!--                                     | xhtml:img[@src] | xhtml:input[@type = 'image' and @src] | xhtml:script[@src]-->
<!--                                     | td[@background] | body[@background]-->
<!--                                     | xhtml:td[@background] | xhtml:body[@background]">-->
<!--                    <xsl:copy>-->
<!--                        <xsl:copy-of select="@*"/>-->
<!--                        <xsl:attribute name="f:url-norewrite" select="'true'"/>-->
<!--                        <xsl:apply-templates/>-->
<!--                    </xsl:copy>-->
<!--                </xsl:template>-->

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
