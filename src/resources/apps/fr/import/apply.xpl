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
          xmlns:xh="http://www.w3.org/1999/xhtml">

    <p:param type="input" name="xforms-model"/>
    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <!-- Extract request parameters (app, form, document, and mode) from URL -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../request-parameters.xpl"/>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:xslt" href="#instance">
            <config xsl:version="2.0">
                <url><xsl:value-of select="/*"/></url>
                <!--<url>file:/Users/ebruchez/Desktop/contact2.zip</url>-->
                <content-type>multipart/x-zip</content-type>
                <cache-control><use-local-cache>false</use-local-cache></cache-control>
            </config>
        </p:input>
        <p:output name="data" id="zip"/>
    </p:processor>

    <!-- Unzip Excel file -->
    <p:processor name="oxf:unzip">
        <p:input name="data" href="#zip"/>
        <p:output name="data" id="zip-file-list"/>
    </p:processor>

    <!-- Extract Excel file's first sheet data as a series of rows -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#zip-file-list"/>
        <p:input name="config">
            <rows xsl:version="2.0"
                     xmlns:ms-main="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                     xmlns:ms-orels="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                     xmlns:ms-prels="http://schemas.openxmlformats.org/package/2006/relationships">
                <!-- Fixed documents -->
                <xsl:variable name="workbook" select="doc(/*/file[@name = 'xl/workbook.xml'])" as="document-node()"/>
                <xsl:variable name="rels" select="doc(/*/file[@name = 'xl/_rels/workbook.xml.rels'])" as="document-node()"/>
                <xsl:variable name="strings" select="doc(/*/file[@name = 'xl/sharedStrings.xml'])" as="document-node()"/>

                <!-- Open first sheet -->
                <xsl:variable name="sheet-rid" select="$workbook/*/ms-main:sheets/ms-main:sheet[1]/@ms-orels:id" as="xs:string"/>
                <xsl:variable name="sheet-filename" select="$rels/*/ms-prels:Relationship[@Id = $sheet-rid]/@Target" as="xs:string"/>
                <xsl:variable name="sheet" select="doc(/*/file[@name = concat('xl/', $sheet-filename)])" as="document-node()"/>

                <xsl:for-each select="$sheet/*/ms-main:sheetData/ms-main:row">
                    <row>
                        <xsl:for-each select="ms-main:c">
                            <xsl:variable name="v" select="ms-main:v"/>
                            <c><xsl:value-of select="if (exists(@t)) then $strings/*/ms-main:si[xs:integer($v) + 1]/ms-main:t else $v"/></c>
                        </xsl:for-each>
                    </row>
                </xsl:for-each>
            </rows>
        </p:input>
        <p:output name="data" id="rows"/>
    </p:processor>

    <!-- Obtain the form definition -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../detail/read-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:output name="data" id="xhtml-fr-xforms"/>
    </p:processor>

    <!-- Unroll the form (theme, components, inclusions) -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../unroll-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:input name="data" href="#xhtml-fr-xforms"/>
        <p:output name="data" id="unrolled-form"/>
    </p:processor>

    <!-- Append model to apply -->
    <p:processor name="oxf:xslt">
        <p:input name="xforms-model" href="#xforms-model"/>
        <p:input name="data" href="#unrolled-form"/>
        <p:input name="config">
            <xsl:transform version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <xsl:template match="xh:head">
                    <xsl:copy>
                        <xsl:apply-templates select="@* | node()"/>
                        <xsl:copy-of select="doc('input:xforms-model')"/>
                    </xsl:copy>
                </xsl:template>

            </xsl:transform>
        </p:input>
        <p:output name="data" id="full-form"/>
    </p:processor>

    <!-- Process -->
    <p:processor name="oxf:xforms-to-xhtml">
        <p:input name="annotated-document" href="#full-form"/>
        <p:input name="instance" href="#parameters"/>
        <p:input name="data" href="#rows"/>
        <p:output name="document" id="binary-document" ref="data"/>
    </p:processor>

</p:config>
