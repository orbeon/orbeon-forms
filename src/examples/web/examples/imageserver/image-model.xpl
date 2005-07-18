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

    <p:param name="instance" type="input"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

                <xsl:template match="*">
                    <xsl:copy>
                        <xsl:apply-templates/>
                    </xsl:copy>
                </xsl:template>

                <xsl:template match="scale|crop">
                    <xsl:if test="enable = 'true' ">
                        <transform type="{name(.)}">
                            <xsl:for-each select="*">
                                <xsl:if test="name(.) != 'enable'">
                                  <xsl:copy-of select="."/>
                                </xsl:if>
                            </xsl:for-each>
                        </transform>
                    </xsl:if>
                </xsl:template>

                <xsl:template match="rect|fill|line">
                    <xsl:if test="enable = 'true' ">
                        <transform type="draw">
                            <xsl:element name="{name(.)}">
                                <xsl:for-each select="@*">
                                    <xsl:attribute name="{name(.)}">
                                        <xsl:value-of select="."/>
                                    </xsl:attribute>
                                </xsl:for-each>
                                <color rgb="{color/@rgb}" alpha="{color/@alpha}"/>
                            </xsl:element>
                        </transform>
                    </xsl:if>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="config"/>
    </p:processor>

    <p:processor name="oxf:image-server">
        <p:input name="config" href="#config#xpointer(/form/config)"/>
        <p:input name="image" href="#config#xpointer(/form/image)"/>
    </p:processor>

</p:config>
