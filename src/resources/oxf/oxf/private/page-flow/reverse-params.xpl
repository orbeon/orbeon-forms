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

    <p:param type="input" name="instance"/>
    <p:param type="input" name="params"/>
    <p:param type="input" name="instance-params"/>
    <p:param type="input" name="path-info"/>
    <p:param type="output" name="redirect-data"/>

    <p:processor name="oxf:xalan">
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:xalan= "http://xml.apache.org/xalan"
                xmlns:c="http://www.orbeon.com/oxf/controller"
                xmlns:xforms="http://www.w3.org/2002/xforms">

                <xsl:template match="/">
                    <xsl:variable name="instance" select="/*/instance"/>
                    <xsl:variable name="params" select="/*/params//c:param"/>
                    <xsl:variable name="instance-params" select="/*/parameters"/>
                    <xsl:variable name="path-info" select="string(/*/path-info)"/>

                    <redirect-url>
                        <path-info>
                            <xsl:call-template name="build-path-info">
                                <xsl:with-param name="params" select="$params"/>
                                <xsl:with-param name="instance" select="$instance"/>
                                <xsl:with-param name="path-info" select="$path-info"/>
                            </xsl:call-template>
                        </path-info>
                        <xsl:copy-of select="$instance-params"/>
                    </redirect-url>
                </xsl:template>

                <xsl:template name="build-path-info">
                    <xsl:param name="params"/>
                    <xsl:param name="instance"/>
                    <xsl:param name="path-info"/>

                    <xsl:choose>
                        <xsl:when test="contains($path-info, '(')">
                            <xsl:value-of select="substring-before($path-info, '(')"/>

                            <xsl:variable name="ref">
                                <xsl:choose>
                                    <xsl:when test="starts-with($params[1]/@ref, '/')">
                                        <xsl:value-of select="substring($params[1]/@ref, 2)"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:value-of select="$params[1]/@ref"/>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:variable>

                            <xsl:message terminate="no">1: <xsl:value-of select="$ref"/></xsl:message>

                            <xsl:for-each select="$instance">
                                <xsl:value-of select="string(xalan:evaluate($ref))"/>
                                <xsl:message terminate="no">2: <xsl:value-of select="string(xalan:evaluate($ref))"/></xsl:message>
                            </xsl:for-each>

                            <xsl:call-template name="build-path-info">
                                <xsl:with-param name="params" select="$params[position() > 1]"/>
                                <xsl:with-param name="instance" select="$instance"/>
                                <xsl:with-param name="path-info" select="substring-after($path-info, ')')"/>
                            </xsl:call-template>

                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="$path-info"/>
                        </xsl:otherwise>
                    </xsl:choose>

                </xsl:template>

                <xsl:template name="apply-param">
                    <xsl:param name="params"/>
                    <xsl:param name="matcher-groups"/>
                    <xsl:param name="instance"/>
                    <xsl:variable name="instance-replaced">
                        <xsl:choose>
                            <!-- Keep default value if new value is an empty string -->
                            <xsl:when test="string($matcher-groups[1]) = ''">
                                <xsl:copy-of select="$instance"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- Remove first '/' in reference, if there is one -->
                                <xsl:variable name="ref">
                                    <xsl:choose>
                                        <xsl:when test="starts-with($params[1]/@ref, '/')">
                                            <xsl:value-of select="substring($params[1]/@ref, 2)"/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:value-of select="$params[1]/@ref"/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xsl:variable>
<!--                                                <xsl:message terminate="no">1: <xsl:value-of select="$ref"/></xsl:message>-->
                                <!-- Select the node pointed by the first param -->
                                <xsl:variable name="id">
                                    <xsl:for-each select="$instance">
                                        <xsl:value-of select="generate-id(xalan:evaluate($ref))"/>
                                    </xsl:for-each>
                                </xsl:variable>
<!--                                                <xsl:message terminate="no">2: <xsl:value-of select="$id"/></xsl:message>-->
                                <!-- Do the replacement in the instance -->
                                <xsl:call-template name="replace">
                                    <xsl:with-param name="document" select="$instance"/>
                                    <xsl:with-param name="id" select="$id"/>
                                    <xsl:with-param name="value" select="string($matcher-groups[1])"/>
                                </xsl:call-template>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
<!--                                    <xsl:message terminate="no">3: <xsl:value-of select="$instance-replaced"/></xsl:message>-->

                    <!-- Recursive call with rest of params and groups -->
                    <xsl:call-template name="apply-param">
                        <xsl:with-param name="params" select="$params[position() > 1]"/>
                        <xsl:with-param name="matcher-groups" select="$matcher-groups[position() > 1]"/>
                        <xsl:with-param name="instance" select="xalan:nodeset($instance-replaced)/*"/>
                    </xsl:call-template>
                </xsl:template>

                <!-- Replaces in "document" the content of the node for
                     which generate-id() returns "id" with the string in
                     "value". -->
                <xsl:template name="replace">
                    <xsl:param name="document"/>
                    <xsl:param name="id"/>
                    <xsl:param name="value"/>

                    <xsl:choose>
                        <xsl:when test="generate-id($document) = $id">
                            <!-- Found the element -->
                            <xsl:element name="{name($document)}">
                                <xsl:value-of select="$value"/>
                            </xsl:element>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:element name="{name($document)}">
                                <xsl:copy-of select="$document/@*"/>
                                <xsl:for-each select="$document/*|$document/text()">
                                    <xsl:choose>
                                        <xsl:when test="name(.) = ''">
                                            <!-- This is not an element -->
                                            <xsl:copy-of select="."/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <!-- This is an element -->
                                            <xsl:call-template name="replace">
                                                <xsl:with-param name="document" select="."/>
                                                <xsl:with-param name="id" select="$id"/>
                                                <xsl:with-param name="value" select="$value"/>
                                            </xsl:call-template>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xsl:for-each>
                            </xsl:element>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:input name="data" href="aggregate('root', aggregate('instance', #instance), #params, #instance-params, #path-info)"/>
        <p:output name="data" ref="redirect-data"/>
    </p:processor>
</p:config>
