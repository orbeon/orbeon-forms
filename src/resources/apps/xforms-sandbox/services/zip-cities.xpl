<?xml version="1.0" encoding="windows-1252"?>
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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
        xmlns:f="http://www.orbeon.com/oxf/function">

    <p:param name="data" type="output"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter[name = 'state-abbreviation' or name = 'max']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="request" href="#request"/>
        <p:input name="data" href="zip-flat.xml"/>
        <p:input name="config">
            <cities xsl:version="2.0">
                <xsl:variable name="parameters" as="element(parameter)*" select="doc('input:request')/request/parameters/parameter"/>
                <xsl:variable name="state-abbreviation" as="xs:string?" select="$parameters[name = 'state-abbreviation']/value"/>
                <xsl:variable name="max" as="xs:integer?" select="$parameters[name = 'max']/value"/>
                <xsl:variable name="zips" as="element(zip)*" select="/zips/zip[state-abbreviation = $state-abbreviation]"/>
                <xsl:for-each select="distinct-values($zips/city)[empty($max) or position() lt $max]">
                    <xsl:sort/>
                    <city name="{.}"/>
                </xsl:for-each>
            </cities>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
