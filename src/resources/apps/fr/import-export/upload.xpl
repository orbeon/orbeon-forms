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

    <p:param name="instance" type="input"/>

    <!-- Extract form-data from request -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Decode base64 and parse XML -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#request"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:template match="/">
                    <xsl:variable name="serialized-xml" as="xs:string" select="saxon:base64Binary-to-string(xs:base64Binary(/request/parameters/parameter[name = 'form-data']/value), 'UTF8')"/>
                    <xsl:copy-of select="saxon:parse($serialized-xml)"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="form-data"/>
    </p:processor>

    <!-- Save form-data in session -->
    <p:processor name="oxf:scope-serializer">
        <p:input name="data" href="#form-data"/>
        <p:input name="config" debug="xxx">
            <config>
                <key>fr-upload-form-data</key>
                <scope>session</scope>
            </config>
        </p:input>
    </p:processor>

    <!-- Redirect to the form page -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#request"/>
        <p:input name="config">
            <redirect-url xsl:version="2.0">
                <path-info>
                    <xsl:text>/fr/</xsl:text>
                    <xsl:value-of select="/request/parameters/parameter[name = 'app']/value"/>
                    <xsl:text>/</xsl:text>
                    <xsl:value-of select="/request/parameters/parameter[name = 'form']/value"/>
                    <xsl:text>/new/</xsl:text>
                </path-info>
            </redirect-url>
        </p:input>
        <p:output name="data" id="redirect"/>
    </p:processor>
    <p:processor name="oxf:redirect">
        <p:input name="data" href="#redirect"/>
    </p:processor>

</p:config>
