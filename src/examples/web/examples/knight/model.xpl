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
          xmlns:oxf="http://www.orbeon.com/oxf/processors">
    
    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <!-- We encapsulate tour.xsl so that we can set a parameter -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:import href="tour.xsl"/>
                <xsl:variable name="test" select="document('tour.xsl')"/>
                <xsl:variable name="start-cell" select="/form/start"/>
                <xsl:param
                    name="start"
                    select="if (string-length($start-cell) = 2
                                and translate(substring($start-cell,1,1), 'abcdefgh', 'aaaaaaaa') = 'a'
                                and translate(substring($start-cell,2,1), '12345678', '11111111') = '1')
                            then string($start-cell)
                            else 'a1'"/>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
