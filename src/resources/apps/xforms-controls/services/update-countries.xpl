<!--
    Copyright (C) 2004-2007 Orbeon, Inc.
  
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
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="countries" href="countries.xml"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:template match="/">
                    <xsl:variable name="letter" as="xs:string" select="/countries/@letter"/>
                    <xsl:variable name="countries" as="element()*"
                        select="doc('input:countries')/countries/country
                            [starts-with(name, $letter)]"/>
                    <countries letter="{$letter}">
                        <xsl:copy-of select="$countries"/>
                    </countries>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
