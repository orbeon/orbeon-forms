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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

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
                <xsl:for-each-group select="tokenize(/*, '&#x0a;', '')[normalize-space()]" group-adjacent="substring(., 1, 2)">
                    <country code="{substring(., 1, 2)}">
                        <xsl:for-each select="current-group()">
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
