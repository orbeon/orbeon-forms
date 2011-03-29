<!--
    Copyright (C) 2011 Orbeon, Inc.

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
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:saxon="http://saxon.sf.net/">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <!-- Read .xlsx file -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:xslt" href="#instance">
            <config xsl:version="2.0">
                <!--<url><xsl:value-of select="/*"/></url>-->
                <url>file:/Users/ebruchez/Desktop/DBExcel/contact5.xlsx</url>
                <content-type>multipart/x-zip</content-type>
                <cache-control><use-local-cache>false</use-local-cache></cache-control>
            </config>
        </p:input>
        <p:output name="data" id="zip"/>
    </p:processor>

    <!-- Unzip file -->
    <p:processor name="oxf:unzip">
        <p:input name="data" href="#zip"/>
        <p:output name="data" id="zip-file-list"/>
    </p:processor>

    <!-- Amend .xlsx file's first sheet by duplicating its rows a number of times -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#zip-file-list"/>
        <p:input name="config">
            <xsl:transform version="2.0"
                           xmlns:net="java:org.orbeon.oxf.util.NetUtils"
                           xmlns:ms-main="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                           xmlns:ms-orels="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                           xmlns:ms-prels="http://schemas.openxmlformats.org/package/2006/relationships">

                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

                <!-- Fixed documents -->
                <xsl:variable name="workbook" select="doc(/*/file[@name = 'xl/workbook.xml'])" as="document-node()"/>
                <xsl:variable name="rels" select="doc(/*/file[@name = 'xl/_rels/workbook.xml.rels'])" as="document-node()"/>
                <xsl:variable name="strings" select="doc(/*/file[@name = 'xl/sharedStrings.xml'])" as="document-node()"/>

                <!-- Get first sheet -->
                <xsl:variable name="sheet-rid" select="$workbook/*/ms-main:sheets/ms-main:sheet[1]/@ms-orels:id" as="xs:string"/>
                <xsl:variable name="sheet-file" select="$rels/*/ms-prels:Relationship[@Id = $sheet-rid]/@Target" as="xs:string"/>
                <xsl:variable name="sheet-element" select="/*/file[@name = concat('xl/', $sheet-file)]" as="element()"/>
                <xsl:variable name="sheet" select="doc($sheet-element)" as="document-node()"/>

                <!-- All rows including header row -->
                <xsl:variable name="rows" select="$sheet/*/ms-main:sheetData/ms-main:row"/>
                <xsl:variable name="data-rows-count" select="count($rows) - 1"/>

                <!-- Create empty temp file that expires with the request -->
                <xsl:variable name="tmp" select="net:createTemporaryFile(0)" as="xs:string"/>

                <!-- Number of times to duplicate existing rows (except header row) -->
                <xsl:variable name="iterations" select="60"/>
                <!--<xsl:variable name="iterations" select="12000"/>-->
                
                <xsl:template match="/">

                    <!-- Create new sheet content -->
                    <xsl:result-document href="{$tmp}">
                        <xsl:apply-templates select="$sheet/*" mode="data"/>
                    </xsl:result-document>

                    <!-- Make updated list of files available -->
                    <xsl:apply-templates select="/*" mode="files"/>
                </xsl:template>

                <!-- Update sheet dimensions -->
                <xsl:template match="ms-main:dimension" mode="data">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:attribute name="ref" select="concat('A1:', replace($rows[1]/ms-main:c[last()]/@r, '([A-Z]+).*', concat('$1', $data-rows-count * $iterations + 1)))"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Duplicate existing rows $iterations times -->
                <xsl:template match="ms-main:sheetData" mode="data">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <!-- Process header row -->
                        <xsl:apply-templates select="$rows[1]"/>
                        <!-- Process data rows -->
                        <xsl:for-each select="1 to $iterations">
                            <xsl:variable name="i" select="position()"/>
                            <xsl:for-each select="$rows[position() gt 1]">
                                <xsl:variable name="r" select="($i - 1) * $data-rows-count + position() + 1"/>
                                <xsl:copy>
                                    <xsl:copy-of select="@*"/>
                                    <xsl:attribute name="r" select="$r"/>
                                    <xsl:for-each select="ms-main:c">
                                        <xsl:copy>
                                            <xsl:copy-of select="@*"/>
                                            <xsl:attribute name="r" select="replace(@r, '([A-Z]+).*', concat('$1', $r))"/>
                                            <xsl:apply-templates mode="#current"/>
                                        </xsl:copy>
                                    </xsl:for-each>
                                </xsl:copy>
                            </xsl:for-each>
                        </xsl:for-each>
                    </xsl:copy>
                </xsl:template>

                <!-- Replace first sheet file name with temp file name -->
                <xsl:template match="file[generate-id() = $sheet-element/generate-id()]" mode="files" >
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:value-of select="$tmp"/>
                    </xsl:copy>
                </xsl:template>

            </xsl:transform>
        </p:input>
        <p:output name="data" id="files"/>
    </p:processor>

    <!-- Zip stuff back -->
    <p:processor name="oxf:zip">
        <p:input name="data" href="#files"/>
        <p:output name="data" id="new-zip" ref="data"/>
    </p:processor>

</p:config>
