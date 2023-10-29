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
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <!-- Unrolled XHTML+XForms -->
    <p:param type="input" name="xforms"/>
    <!-- Request parameters -->
    <p:param type="input" name="parameters"/>
    <!-- PDF document -->
    <p:param type="output" name="data"/>

    <!-- Call XForms epilogue -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/ops/pfc/xforms-epilogue.xpl"/>
        <p:input name="data" href="#xforms"/>
        <p:input name="model-data"><null xsi:nil="true"/></p:input>
        <p:input name="instance" href="#parameters"/>
        <p:output name="xformed-data" id="xformed-data"/>
    </p:processor>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/container-namespace</include>
                <include>/request/parameters/parameter</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Prepare XHTML before conversion to PDF -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config">
            <xsl:transform version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <!-- Remove portlet namespace from ids if present. Do this because in a portlet environment, the CSS
                     retrieved by oxf:xhtml-to-pdf doesn't know about the namespace. Not doing so, the CSS won't apply
                     and also this can cause a ClassCastException in Flying Saucer. -->
                <xsl:template match="@id">
                    <xsl:attribute name="id" select="substring-after(., doc('input:request')/*/container-namespace)"/>
                </xsl:template>
                <!-- While we are at it filter out scripts as they won't be used -->
                <xsl:template match="*:script | *:noscript"/>
                <!-- Remove xforms-initially-hidden class on the form, normally removed by the script -->
                <!-- 2019-11-01: PDF template doesn't use Flying Saucer so above comment doesn't make sense? -->
                <xsl:template match="*:form">
                    <xsl:copy>
                        <xsl:attribute name="class" select="string-join(p:classes()[. != 'xforms-initially-hidden'], ' ')"/>
                        <xsl:apply-templates select="@* except @class | node()"/>
                    </xsl:copy>
                </xsl:template>
                <!-- Remove all prefixes because Flying Saucer doesn't like them -->
                <!-- 2019-11-01: PDF template doesn't use Flying Saucer so above comment doesn't make sense? -->
                <xsl:template match="*">
                    <xsl:element name="{local-name()}">
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>
            </xsl:transform>
        </p:input>
        <p:input name="data" href="#xformed-data"/>
        <p:input name="request" href="#request"/>
        <p:output name="data" id="xhtml-data"/>
    </p:processor>

    <!-- Create mapping file -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#xhtml-data"/>
        <p:input name="parameters" href="#parameters"/>
        <p:input name="config" href="print-pdf-template.xsl"/>
        <p:output name="data" id="mapping"/>
    </p:processor>

    <!-- Obtain original form document -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>fr-form-definition</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:output name="data" id="form-document"/>
    </p:processor>

    <!-- Call up persistence layer to obtain the PDF file -->
    <p:processor name="oxf:url-generator">
        <!-- NOTE: Depend on #request for request parameters to avoid caching issues -->
        <p:input name="config" href="aggregate('root', #form-document, #parameters, #request)" transform="oxf:unsafe-xslt">
            <config xsl:version="2.0" xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

                <xsl:variable name="form"   select="/*/*[1]"/>
                <xsl:variable name="params" select="/*/*[2]"/>

                <xsl:variable name="format"                select="$params/mode"/>
                <xsl:variable name="pdf-template-name-opt" select="p:get-request-parameter('fr-pdf-template-name')[frf:isServicePath(p:get-request-path()) or p:get-request-method() = 'POST']"/>
                <xsl:variable name="pdf-template-lang-opt" select="p:get-request-parameter('fr-pdf-template-lang')[frf:isServicePath(p:get-request-path()) or p:get-request-method() = 'POST']"/>

                <xsl:variable name="attach" select="$form//xf:instance[@id = 'fr-form-attachments']/*"/>

                <url>
                    <xsl:value-of
                        xmlns:rendered-format="java:org.orbeon.oxf.fr.process.FormRunnerRenderedFormat"
                        select="
                            p:rewrite-service-uri(
                                rendered-format:findTemplatePath(
                                    $attach,
                                    $format,
                                    $pdf-template-name-opt,
                                    $pdf-template-lang-opt
                                ),
                                true()
                            )"/>
                </url>

                <mode>binary</mode>

                <xsl:for-each select="$params/form-version">
                    <header>
                        <name>Orbeon-Form-Definition-Version</name>
                        <value><xsl:value-of select="."/></value>
                    </header>
                </xsl:for-each>

            </config>
        </p:input>
        <p:output name="data" id="pdf-template"/>
    </p:processor>

    <!-- Produce PDF document -->
    <p:processor name="oxf:pdf-template">
        <p:input name="data" href="#xhtml-data"/>
        <p:input name="model" href="#mapping"/>
        <p:input name="template" href="#pdf-template"/>
        <p:output name="data" ref="data"/>
    </p:processor>

    <!-- TODO: example of oxf:add-attribute processor adding content-disposition information -->
    <!-- TODO: build file name dynamically using requested document id? -->
    <!--<p:processor name="oxf:add-attribute">-->
        <!--<p:input name="data" href="#pdf-data"/>-->
        <!--<p:input name="config">-->
            <!--<config>-->
                <!--<match>/*</match>-->
                <!--<attribute-name>content-disposition</attribute-name>-->
                <!--<attribute-value>attachment; filename=form.pdf</attribute-value>-->
            <!--</config>-->
        <!--</p:input>-->
        <!--<p:output name="data" ref="data"/>-->
    <!--</p:processor>-->

</p:config>
