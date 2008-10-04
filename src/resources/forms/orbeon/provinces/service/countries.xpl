<?xml version="1.0" encoding="utf-8"?>
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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param type="output" name="data"/>

    <p:processor name="oxf:url-generator">
        <p:input name="config">
            <config>
                <url>admin1Codes.txt</url>
                <mode>text</mode>
                <encoding>utf-8</encoding>
                <force-encoding>true</force-encoding>
            </config>
        </p:input>
        <p:output name="data" id="text"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#text"/>
        <p:input name="config">
            <countries xsl:version="2.0">
                <!-- NOTE: the format of admin1Codes.txt is not extremely clear -->
                <xsl:for-each-group select="tokenize(/*, '&#x0a;', '')[normalize-space()]" group-adjacent="substring(., 1, 2)">
                    <xsl:variable name="country-code" select="substring(., 1, 2)" as="xs:string"/>
                    <xsl:variable name="country-number" select="position()" as="xs:integer"/>
                    <!--<xsl:variable name="country-name"-->
                                  <!--select="for $n in substring(., 7) return if (contains($n, '(general)')) then substring-before($n, '(general)') else $n" as="xs:string"/>-->
                    <country code="{$country-code}" name="{$country-code}">
                        <xsl:for-each select="current-group()[not(contains(., 'general'))]">
                            <province>
                                <xsl:value-of select="substring(., 7)"/>
                            </province>
                        </xsl:for-each>
                    </country>
                </xsl:for-each-group>
            </countries>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
