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
<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param type="input"  name="file"/>
    <p:param type="output" name="rows"/>

    <!-- Unzip Excel file -->
    <p:processor name="oxf:unzip">
        <p:input name="data" href="#file"/>
        <p:output name="data" id="zip-file-list"/>
    </p:processor>

    <!-- Extract Excel file's first sheet data as a series of rows -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#zip-file-list"/>
        <p:input name="config">
            <rows
                xsl:version="2.0"
                xmlns:ms-main="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:ms-orels="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                xmlns:ms-prels="http://schemas.openxmlformats.org/package/2006/relationships"
                xmlns:fri="java:org.orbeon.oxf.fr.FormRunnerImport">

                <!-- Fixed documents -->
                <xsl:variable name="workbook"         select="doc(/*/file[@name = 'xl/workbook.xml'])"            as="document-node()"/>
                <xsl:variable name="rels"             select="doc(/*/file[@name = 'xl/_rels/workbook.xml.rels'])" as="document-node()"/>
                <xsl:variable name="strings"          select="doc(/*/file[@name = 'xl/sharedStrings.xml'])"       as="document-node()"/>
                <xsl:variable name="styles"           select="doc(/*/file[@name = 'xl/styles.xml'])"              as="document-node()"/>

                <!-- Open first sheet -->
                <xsl:variable name="sheet-rid"        select="$workbook/*/ms-main:sheets/ms-main:sheet[1]/@ms-orels:id" as="xs:string"/>
                <xsl:variable name="sheet-filename"   select="$rels/*/ms-prels:Relationship[@Id = $sheet-rid]/@Target"  as="xs:string"/>
                <xsl:variable name="sheet"            select="doc(/*/file[@name = concat('xl/', $sheet-filename)])"     as="document-node()"/>

                <xsl:variable
                    name="use1904windowing"
                    select="$workbook/*/ms-main:workbookPr/@date1904 = ('1', 'on', 'true')"
                    as="xs:boolean"/>

                <xsl:variable name="builtin-formats" as="element()+">
                    <numFmt numFmtId="0" formatCode="general"/>
                    <numFmt numFmtId="1" formatCode="0"/>
                    <numFmt numFmtId="2" formatCode="0.00"/>
                    <numFmt numFmtId="3" formatCode="#,##0"/>
                    <numFmt numFmtId="4" formatCode="#,##0,00"/>
                    <numFmt numFmtId="9" formatCode="0%"/>
                    <numFmt numFmtId="10" formatCode="0.00%"/>
                    <numFmt numFmtId="11" formatCode="0.00E+00"/>
                    <numFmt numFmtId="12" formatCode="# ?/?"/>
                    <numFmt numFmtId="13" formatCode="# ??/??"/>
                    <numFmt numFmtId="14" formatCode="mm-dd-yy"/>
                    <numFmt numFmtId="15" formatCode="d-mmm-yy"/>
                    <numFmt numFmtId="16" formatCode="d-mmm"/>
                    <numFmt numFmtId="17" formatCode="mmm-yy"/>
                    <numFmt numFmtId="18" formatCode="h:mm AM/PM"/>
                    <numFmt numFmtId="19" formatCode="h:mm:ss AM/PM"/>
                    <numFmt numFmtId="20" formatCode="h:mm"/>
                    <numFmt numFmtId="21" formatCode="h:mm:ss"/>
                    <numFmt numFmtId="22" formatCode="m/d/yy h:mm"/>
                    <numFmt numFmtId="37" formatCode="#,##0 ;(#,##0)"/>
                    <numFmt numFmtId="38" formatCode="#,##0 ;[Red](#,##0)"/>
                    <numFmt numFmtId="39" formatCode="#,##0.00;(#,##0.00)"/>
                    <numFmt numFmtId="40" formatCode="#,##0.00;[Red](#,##0.00)"/>
                    <numFmt numFmtId="45" formatCode="mm:ss"/>
                    <numFmt numFmtId="46" formatCode="[h]:mm:ss"/>
                    <numFmt numFmtId="47" formatCode="mmss.0"/>
                    <numFmt numFmtId="48" formatCode="##0.0E+0"/>
                    <numFmt numFmtId="49" formatCode="@"/>
                </xsl:variable>

                <xsl:for-each select="$sheet/*/ms-main:sheetData/ms-main:row">

                    <!-- Format row  -->
                    <xsl:variable name="row" as="element(row)">
                        <row>
                            <xsl:for-each select="ms-main:c">
                                <xsl:variable name="c" select="."/>
                                <xsl:variable name="v" select="$c/ms-main:v"/>

                                <xsl:variable
                                    name="format"
                                    select="
                                        for $i in (@s/xs:integer(.), 0)[1]
                                        return
                                            for $fmtId in $styles/*/ms-main:cellXfs/ms-main:xf[$i + 1]/@numFmtId/xs:integer(.)
                                            return (
                                                $fmtId,
                                                (
                                                    $styles/*/ms-main:numFmts/ms-main:numFmt |
                                                    $builtin-formats
                                                )[@numFmtId = $fmtId]/@formatCode/xs:string(.)
                                            )"/>

                                <xsl:variable
                                    name="type"
                                    select="
                                        if (@t = 'b') then
                                            'boolean'
                                        else if (@t = 'inlineStr' or @t = 's') then
                                            'string'
                                        else if (@t = 'e') then
                                            'error'
                                        else if (@t = 'str') then
                                            'formula'
                                        else if ((@t = 'n' or empty(@t)) and exists($format)) then
                                            for $f in fri:findOoxmlCellType($format[1], $format[2])
                                            return if ($f = 'other') then 'number' else $f
                                        else
                                            'number'"/>

                                <c  r="{replace(@r, '\d', '')}"
                                    type="{$type}">
                                    <xsl:value-of
                                        select="
                                            if (@t = 's') then
                                                $strings/*/ms-main:si[xs:integer($v) + 1]/ms-main:t
                                            else if (@t = 'inlineStr') then
                                                $c/ms-main:is
                                            else if ($type = ('datetime', 'date', 'time')) then
                                                fri:convertDateTime($v, $type, $use1904windowing)
                                            else if ($type = 'number') then
                                                fri:convertNumber($v)
                                            else
                                                $v"/>
                                </c>
                            </xsl:for-each>
                        </row>
                    </xsl:variable>

                    <!-- Only output non-blank rows -->
                    <xsl:if test="exists($row/c[normalize-space()])">
                        <xsl:copy-of select="$row"/>
                    </xsl:if>

                </xsl:for-each>
            </rows>
        </p:input>
        <p:output name="data" id="rows" ref="rows"/>
    </p:processor>

</p:config>
