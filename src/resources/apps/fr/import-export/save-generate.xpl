<?xml version="1.0" encoding="windows-1252"?>
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:wx="http://schemas.microsoft.com/office/word/2003/auxHint"
        xmlns:w="http://schemas.microsoft.com/office/word/2003/wordml"
        xmlns:w10="urn:schemas-microsoft-com:office:word"
        xmlns:v="urn:schemas-microsoft-com:vml"
        xmlns:o="urn:schemas-microsoft-com:office:office"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
        xmlns:snapshot="ccm.job.snapshot.Snapshot"
        xmlns:f="http://www.orbeon.com/oxf/function">

    <p:param name="form-instance" type="input"/>
    <p:param name="parameters" type="input" debug="parameters"/>
    <p:param name="uuid" type="output" debug="uuid"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-url</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#form-instance"/>
        <p:input name="request" href="#request"/>
        <p:input name="parameters" href="#parameters"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:output method="xml" name="xml"/>
                <xsl:template match="/">
                    <html xsl:version="2.0">
                        <body>
                            <!-- Get values from input documents -->
                            <xsl:variable name="request" as="element(request)" select="doc('input:request')/request"/>
                            <xsl:variable name="request-url" as="element(request-url)" select="$request/request-url"/>
                            <xsl:variable name="parameters" as="element(request)" select="doc('input:parameters')/request"/>
                            <xsl:variable name="app" as="xs:string" select="$parameters/app"/>
                            <xsl:variable name="form" as="xs:string" select="$parameters/form"/>

                            <!-- Form we produce -->
                            <form name="form1" method="post" action="{substring-before($request-url, '/xforms-server')}/fr/{$app}/{$form}/new/" id="form">
                                <input type="hidden" name="form-data" value="{saxon:string-to-base64Binary(saxon:serialize(/*, 'xml'), 'UTF8')}"/>
                                <input type="submit" value="Open"/>
                            </form>
                        </body>
                    </html>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="html"/>
    </p:processor>

    <!-- Serialize to XML -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <indent>false</indent>
                <encoding>utf-8</encoding>
                <standalone>true</standalone>
            </config>
        </p:input>
        <p:input name="data" href="#html"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <!-- Save to disk and return associated UUID -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="serialized-to-uuid.xpl"/>
        <p:input name="data" href="#converted"/>
        <p:output name="data" ref="uuid"/>
    </p:processor>

</p:config>
