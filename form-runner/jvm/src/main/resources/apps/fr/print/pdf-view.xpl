<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
>
    <p:param type="input"  name="instance"/><!-- Parameters: `app`, `form`, `form-version`, `document`, `mode` -->
    <p:param type="input"  name="data"/>    <!-- XHTML+FR+XForms                                               -->
    <p:param type="output" name="data"/>    <!-- PDF/TIFF binary                                               -->

    <!-- Unroll the form (theme, components, inclusions) -->
    <p:processor name="oxf:pipeline">
        <p:input  name="config"             href="../unroll-form.xpl"/>
        <p:input  name="instance"           href="#instance"/>
        <p:input  name="data"               href="#data"/>
        <p:output name="data"               id="unrolled-form"/>
        <p:output name="form-runner-config" id="form-runner-config"/>
    </p:processor>

    <!--
        Read `data` first, because a side effect of it is to make `form-runner-config` available, and just below we
        need that first. Maybe `fr:form-runner-config` could do this internally?
    -->
    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#unrolled-form"/>
    </p:processor>

    <p:choose href="#form-runner-config">
        <p:when test="/*/use-pdf-template = 'true'">
            <!-- A PDF template is attached to the form and its use is enabled -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="print-pdf-template.xpl"/>
                <p:input name="xforms" href="#unrolled-form"/>
                <p:input name="form-definition" href="#data"/>
                <p:input name="parameters" href="#instance"/>
                <p:output name="data" id="pdf-data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- No PDF template attached or use is disabled -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="print-pdf-notemplate.xpl"/>
                <p:input name="xforms" href="#unrolled-form"/>
                <p:input name="parameters" href="#instance"/>
                <p:output name="data" id="pdf-data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:choose
            href="#instance"
            xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
            xmlns:version="java:org.orbeon.oxf.common.Version">
        <p:when test="/*/mode = 'tiff' and (version:isPE() or frf:sendError(404))">
            <p:processor name="oxf:pdf-to-image">
                <p:input name="data" href="#pdf-data"/>
                <p:input name="config" transform="oxf:unsafe-xslt" href="#instance">
                    <config xsl:version="2.0">

                        <xsl:variable name="app"  select="/*/app/string()"/>
                        <xsl:variable name="form" select="/*/form/string()"/>

                        <xsl:variable
                            name="compression-type"
                            select="p:property(string-join(('oxf.fr.detail.tiff.compression.type', $app, $form), '.'))"/>

                        <xsl:variable
                            name="compression-quality"
                            select="p:property(string-join(('oxf.fr.detail.tiff.compression.quality', $app, $form), '.'))"/>

                        <xsl:variable
                            name="scale"
                            select="p:property(string-join(('oxf.fr.detail.tiff.scale', $app, $form), '.'))"/>

                        <format>tiff</format>
                        <xsl:if test="$compression-type or $compression-quality">
                            <compression>
                                <xsl:if test="$compression-type">
                                    <type><xsl:value-of select="$compression-type"/></type>
                                </xsl:if>
                                <xsl:if test="$compression-quality">
                                    <quality><xsl:value-of select="$compression-quality"/></quality>
                                </xsl:if>
                            </compression>
                        </xsl:if>
                        <xsl:if test="$scale">
                            <scale><xsl:value-of select="$scale"/></scale>
                        </xsl:if>

                    </config>
                </p:input>
                <p:output name="data" id="rendered"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Just produce the PDF document -->
            <p:processor name="oxf:identity">
                <p:input name="data" href="#pdf-data"/>
                <p:output name="data" id="rendered"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#rendered"/>
        <p:input name="config">
            <document
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsl:version="2.0"
                disposition-type="inline"
                filename="{p:get-request-parameter('fr-rendered-filename')}"
                cache-headers-type="page">
                <xsl:copy-of select="/document/(@* | node())"/>
            </document>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
