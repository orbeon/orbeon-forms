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
          xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="model.xpl"/>
        <p:output name="data" id="data"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="config" href="generic-table.xsl"/>
        <p:input name="data" href="#data"/>
        <p:output name="data" id="table"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <workbook>
                        <sheet name="Inventory Report">
                            <xsl:copy-of select="/"/>
                        </sheet>
                    </workbook>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:input name="data" href="#table"/>
        <p:output name="data" id="workbook"/>
    </p:processor>

    <p:processor name="oxf:xls-serializer">
        <p:input name="config">
            <config template="oxf:/examples/reports/report.xls" filename="report.xls">
                <repeat-row row-num="2" for-each="*/*/*/*[position() > 1]"/>
            </config>
        </p:input>
        <p:input name="data" href="#workbook"/>
    </p:processor>

</p:config>
