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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param name="instance" type="input"/>
    <p:param name="instance" type="output"/>

    <!-- Remove action from instance -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="/form/action">
                    <xsl:copy/>
                    <xsl:choose>
                        <xsl:when test=". = 'show-example'" >
                            <xsl:element name='render' >show-example</xsl:element>
                        </xsl:when>
                        <xsl:when test=". = 'show-source'" >
                            <xsl:element name='render' >show-source</xsl:element>
                        </xsl:when>
                    </xsl:choose>
                </xsl:template>
                <xsl:template match="/form/render" />
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="instance"/>
    </p:processor>

</p:config>
