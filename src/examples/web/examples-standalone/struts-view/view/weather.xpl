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

    <p:param type="input" name="weather"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#weather"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">
                <xsl:template match="/beans/weather">
                    <xhtml:portlet>
                        <form action="portlets">
                            <xhtml:table class="gridtable" border="0">
                                <xhtml:tr>
                                    <xhtml:td>Zip Code</xhtml:td>
                                    <xhtml:td>
                                        <input type="text" name="zipCode" value="{zip-code}"/>
                                        <input type="submit"/>
                                    </xhtml:td>
                                </xhtml:tr>
                                <xhtml:tr>
                                    <xhtml:td>Forecast</xhtml:td>
                                    <xhtml:td>
                                        <xsl:value-of select="forecast"/>
                                    </xhtml:td>
                                </xhtml:tr>
                            </xhtml:table>
                        </form>
                    </xhtml:portlet>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>


</p:config>
