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
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:xh="http://www.w3.org/1999/xhtml">

    <!-- Model to apply -->
    <p:param type="input"  name="xforms-model"/>
    <!-- See `validate.xpl` -->
    <p:param type="input"  name="instance"/>

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
                <content-type>multipart/x-zip</content-type>
                <cache-control><use-local-cache>false</use-local-cache></cache-control>
                <mode>binary</mode>
            </config>
        </p:input>
        <p:output name="data" id="zip"/>
    </p:processor>

    <!-- Obtain the form definition -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../detail/read-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:output name="data" id="xhtml-fr-xforms"/>
        <p:output name="instance" id="parameters-with-version"/>
    </p:processor>

    <!-- This is read lazily by the Excel processor if data is needed -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#parameters" transform="oxf:unsafe-xslt">
            <config xsl:version="2.0">

                <xsl:variable name="params" select="/*"/>

                <xsl:variable
                    name="resource"
                    select="
                        concat(
                            '/fr/service/persistence/crud/',
                            $params/app,
                            '/',
                            $params/form,
                            '/data/',
                            p:get-request-parameter('document-id'),
                            '/data.xml'
                        )"/>

                <url><xsl:value-of select="p:rewrite-service-uri($resource, true())"/></url>
                <mode>xml</mode>
                <handle-xinclude>false</handle-xinclude>
                <cache-control><use-local-cache>false</use-local-cache><conditional-get>false</conditional-get></cache-control>

            </config>
        </p:input>
        <p:output name="data" id="form-data"/>
    </p:processor>

    <!-- Extract rows -->
    <p:choose href="#instance">
        <p:when test="/*/file-format = 'xml-metadata'">

            <p:processor name="oxf:url-generator">
                <p:input name="config" transform="oxf:xslt" href="#instance">
                    <config xsl:version="2.0">
                        <url><xsl:value-of select="/*/file"/></url>
                        <content-type><xsl:value-of select="/*/file/@mediatype"/></content-type>
                        <cache-control><use-local-cache>false</use-local-cache></cache-control>
                        <mode>xml</mode>
                    </config>
                </p:input>
                <p:output name="data" id="xml-content"/>
            </p:processor>

            <p:processor name="fr:xml-import">
                <p:input  name="params" href="#parameters"/>
                <p:input  name="data"   href="#form-data"/>
                <p:input  name="form"   href="#xhtml-fr-xforms"/>
                <p:input  name="file"   href="#xml-content" debug="xxx xml-content"/>
                <p:output name="data"   id="rows-or-stats-and-data"/>
            </p:processor>
        </p:when>
        <p:when test="/*/file-format = 'excel-named-ranges'">
            <p:processor name="fr:extract-rows-from-excel-with-named-ranges">
                <p:input  name="params" href="#parameters"/>
                <p:input  name="data"   href="#form-data"/>
                <p:input  name="form"   href="#xhtml-fr-xforms"/>
                <p:input  name="file"   href="#zip"/>
                <p:output name="data"   id="rows-or-stats-and-data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:pipeline">
                <p:input  name="config" href="extract-rows-from-excel-with-headings.xpl"/>
                <p:input  name="file"   href="#zip"/>
                <p:output name="rows"   id="rows-or-stats-and-data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <!-- Unroll the form (theme, components, inclusions) -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../unroll-form.xpl"/>
        <p:input name="instance" href="#parameters-with-version"/>
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
        <p:input  name="annotated-document" href="#full-form"/>
        <p:input  name="instance"           href="#parameters-with-version"/>
        <p:input  name="data"               href="#rows-or-stats-and-data"/>
        <p:output name="document"           ref="data" id="binary-document"/>
    </p:processor>

</p:config>
