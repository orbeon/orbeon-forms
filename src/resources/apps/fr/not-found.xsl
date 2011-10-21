<?xml version="1.0" encoding="UTF-8"?>
<!--
  Copyright (C) 2010 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<?oxf-serializer status-code="404"?>
<xh:html xsl:version="2.0"
         xmlns:xh="http://www.w3.org/1999/xhtml"
         xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
         xmlns:xs="http://www.w3.org/2001/XMLSchema"
         xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <xsl:variable name="has-version" select="xpl:property('oxf.fr.version.*.*')" as="xs:boolean?"/>
    <xsl:variable name="css-uri" select="tokenize(normalize-space(xpl:property('oxf.fr.css.uri.*.*')), '\s+')" as="xs:string*"/>

    <xh:head>
        <xh:title>Not Found</xh:title>

        <!-- Form Runner CSS stylesheets -->
        <xsl:for-each select="$css-uri">
            <xh:link rel="stylesheet" href="{.}" type="text/css" media="all"/>
        </xsl:for-each>
    </xh:head>
    <xh:body>
        <xh:div id="fr-view" class="fr-view">
            <xh:div id="doc4">
                <xh:div class="fr-header">
                    <!-- Logo -->
                    <xh:div class="fr-logo">
                        <xh:img src="/apps/fr/style/orbeon-logo-trimmed-transparent-30.png" alt="Logo"/>
                    </xh:div>
                </xh:div>
                <xh:div id="hd" class="fr-shadow">&#160;</xh:div>
                <xh:div id="bd" class="fr-container">
                    <xh:div id="yui-main">
                        <xh:div class="yui-b">
                            <xh:div class="yui-g fr-top">
                                <xh:h1 class="fr-form-title">
                                    Not Found
                                </xh:h1>
                            </xh:div>
                            <xh:div class="yui-g fr-separator">&#160;</xh:div>
                            <xh:div class="yui-g fr-body">
                                <xh:div class="fr-error-message">
                                    <xh:a name="fr-form"/>
                                    <xh:p>
                                        Oops, the page requested was not found!
                                    </xh:p>
                                </xh:div>
                            </xh:div>
                            <xh:div class="yui-g fr-separator">&#160;</xh:div>
                        </xh:div>
                    </xh:div>
                </xh:div>
                <xh:div id="ft" class="fr-footer">
                    <xsl:if test="not($has-version = false())">
                        <xsl:variable xmlns:version="java:org.orbeon.oxf.common.Version" name="orbeon-forms-version" select="version:getVersionString()" as="xs:string"/>
                        <xh:div class="fr-orbeon-version"><xsl:value-of select="$orbeon-forms-version"/></xh:div>
                    </xsl:if>
                </xh:div>
            </xh:div>
        </xh:div>
    </xh:body>
</xh:html>