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

    <p:param type="output" name="locale"/>

    <p:processor name="oxf:session-generator">
        <p:input name="config">
            <key>locale</key>
        </p:input>
        <p:output name="data" id="locale"/>
    </p:processor>


    <p:processor name="oxf:xslt">
        <p:input name="data" href="#locale"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0">

                <!-- Get locale from session, default to English -->
                <xsl:template match="/">
                    <xsl:choose>
                        <xsl:when test="/locale != ''">
                            <xsl:copy-of select="/locale"/>
                        </xsl:when>
                        <xsl:otherwise><locale>en_US</locale></xsl:otherwise>
                    </xsl:choose>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="locale"/>
    </p:processor>
</p:config>