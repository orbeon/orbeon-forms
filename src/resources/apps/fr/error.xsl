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
<?oxf-serializer status-code="500"?>
<html xsl:version="2.0"
      xmlns="http://www.w3.org/1999/xhtml"
      xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      xmlns:xs="http://www.w3.org/2001/XMLSchema"
      xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <xsl:variable name="has-version" select="xpl:property('oxf.fr.version.*.*')" as="xs:boolean?"/>
    <xsl:variable name="css-uri" select="tokenize(normalize-space(xpl:property('oxf.fr.css.uri.*.*')), '\s+')" as="xs:string*"/>

    <head>
        <title>Orbeon Forms Error</title>

        <!-- Form Runner CSS stylesheets -->
        <xsl:for-each select="$css-uri">
            <link rel="stylesheet" href="{.}" type="text/css" media="all"/>
        </xsl:for-each>
    </head>
    <body>
        <div id="fr-view">
            <div id="doc4">
                <div class="fr-header">
                    <!-- Logo -->
                    <div class="fr-logo">
                        <img src="/apps/fr/style/orbeon-logo-trimmed-transparent-30.png" alt="Logo"/>
                    </div>
                </div>
                <div id="hd" class="fr-shadow">&#160;</div>
                <div id="bd" class="fr-container">
                    <div id="yui-main">
                        <div class="yui-b">
                            <div class="yui-g fr-top">
                                <h1 class="fr-form-title">
                                    Orbeon Forms Error
                                </h1>
                            </div>
                            <div class="yui-g fr-separator">&#160;</div>
                            <div class="yui-g fr-body">
                                <div class="fr-error-message">
                                    <a name="fr-form"/>
                                    <p>
                                        Oops, something bad happened!
                                    </p>
                                    <p>
                                        We apologize about that. Please let us know about this error by sending an
                                        email to <a href="mailto:info@orbeon.com">info@orbeon.com</a>. We definitely
                                        appreciate!
                                    </p>
                                    <p>
                                        You can then do the following:
                                    </p>
                                    <ul>
                                        <li>If this error occurred when you followed a link, press your browser's Back button.</li>
                                        <li>
                                            If the above does not work, try reloading the page:
                                            <ul>
                                                <li>
                                                    With Firefox: hold down the <code>shift</code> key and click the Reload button in your browser toolbar.
                                                </li>
                                                <li>
                                                    With Safari and Chrome: click the Reload button in your browser toolbar.
                                                </li>
                                                <li>
                                                    With Internet Explorer: hold down the <code>control</code> key and click the Reload button in your browser toolbar.
                                                </li>
                                            </ul>
                                        </li>
                                        <li>Return to the <a href="http://www.orbeon.com/">Orbeon web site</a>.</li>
                                        <li>Return to the <a href="/">Orbeon Forms demos</a>.</li>
                                    </ul>
                                </div>
                            </div>
                            <div class="yui-g fr-separator">&#160;</div>
                        </div>
                    </div>
                </div>
                <div id="ft" class="fr-footer">
                    <xsl:if test="not($has-version = false())">
                        <xsl:variable xmlns:version="java:org.orbeon.oxf.common.Version" name="orbeon-forms-version" select="version:getVersionString()" as="xs:string"/>
                        <div class="fr-orbeon-version"><xsl:value-of select="$orbeon-forms-version"/></div>
                    </xsl:if>
                </div>
            </div>
        </div>
    </body>
</html>