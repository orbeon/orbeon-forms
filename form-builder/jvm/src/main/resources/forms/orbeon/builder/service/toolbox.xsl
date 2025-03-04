<!--
    Copyright (C) 2008 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<!-- Return an aggregate so that each xbl:xbl can have its own metadata -->
<xsl:transform
        version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
    xmlns:p="http://www.orbeon.com/oxf/pipeline">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <xsl:template match="/">
        <components>

            <!-- Support missing app/form parameters so we can load a toolbox just after selecting a form template -->
            <xsl:variable name="app"  as="xs:string" select="(doc('input:request')/*/parameters/parameter[name = 'application']/value[p:non-blank()]/string(), '*')[1]"/>
            <xsl:variable name="form" as="xs:string" select="(doc('input:request')/*/parameters/parameter[name = 'form']/value[p:non-blank()]/string(), '*')[1]"/>

            <!-- Find group names, e.g. "text", "selection", etc. -->
            <xsl:variable name="property-names" select="p:properties-start-with('oxf.fb.toolbox.group')" as="xs:string*" />
            <xsl:variable name="unique-groups"  select="distinct-values(for $v in $property-names return tokenize($v, '\.')[5])" as="xs:string*"/>

            <!-- Iterate over groups -->
            <xsl:for-each select="$unique-groups">

                <xsl:variable name="resources-property" select="p:property(string-join(('oxf.fb.toolbox.group', ., 'uri', $app, $form), '.'))" as="xs:string"/>
                <xsl:variable name="resources"          select="for $uri in p:split($resources-property) return doc($uri)" as="document-node()*"/>

                <xsl:if test="$resources">
                    <xbl:xbl>
                        <!-- Add Form Builder metadata from first metadata found -->
                        <xsl:variable name="metadata" select="($resources/*/fb:metadata)[1]" as="element(fb:metadata)?"/>
                        <!-- TODO: what if missing? -->
                        <xsl:copy-of select="$metadata"/>

                        <!-- Process bindings with metadata -->
                        <xsl:apply-templates select="$resources/xbl:xbl/xbl:binding[exists(fb:metadata)]"/>
                    </xbl:xbl>
                </xsl:if>

            </xsl:for-each>

            <!--
                Before:

                | App     | Form    | Global (`orbeon`) | App (`acme`) |
                |=========|=========|===================|==============|
                | acme    | foo     | Yes               | Yes          |
                | acme    | library | No                | No           |
                | orbeon  | foo     | Yes               | No           |
                | orbeon  | library | No                | No           |

                After:

                | App     | Form    | Global (`_`) | Special (`orbeon`) | App (`acme`) |
                |=========|=========|==============|====================|==============|
                | acme    | foo     | Yes          | Yes                | Yes          |
                | acme    | library | No           | No                 | No           |
                | orbeon  | foo     | Yes          | Yes                | No           |
                | orbeon  | library | No           | No                 | No           |
                | _       | foo     | No           | Yes                | Yes          |
                | _       | library | No           | No                 | No           |
            -->

            <xsl:if test="not($form = 'library')"><!-- https://github.com/orbeon/orbeon-forms/issues/1562 -->

                <!-- TODO: We don't currently prevent the user from creating a form in the `_` name. Should we? -->
                <xsl:if test="not($app = frf:globalLibraryAppName())" xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">
                    <xsl:copy-of select="doc('input:global-template-xbl')/xbl:xbl"/>
                </xsl:if>

                <xsl:copy-of select="doc('input:special-template-xbl')/xbl:xbl"/>

                <!-- We exclude the app library instead of the special (`orbeon`) library for backward compatibility -->
                <xsl:if test="not($app = frf:specialLibraryAppName())" xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">
                    <xsl:copy-of select="doc('input:app-template-xbl')/xbl:xbl"/>
                </xsl:if>
            </xsl:if>
        </components>
    </xsl:template>

    <xsl:template match="xbl:binding[@element]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:attribute
                xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
                name="fb:binding-class-name"
                select="frf:bindingClassNameForToolbox(.)"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

    <!--  Filter out unneeded elements -->
    <xsl:template match="xbl:resources | xbl:handlers | xbl:implementation | xbl:template"/>

</xsl:transform>