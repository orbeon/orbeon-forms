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

    <p:param name="data" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Serialize again (we want the content of the file to be in text/binary format) -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <indent>false</indent>
                <encoding>utf-8</encoding>
                <standalone>true</standalone>
            </config>
        </p:input>
        <p:input name="data" href="#data"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <!-- Write the document to a file -->
    <p:processor name="oxf:file-serializer">
        <p:input name="config">
            <config>
                <scope>session</scope>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
        <p:output name="data" id="url"/>
    </p:processor>

    <!-- Generate UUID -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#url"/>
        <p:input name="config">
            <uuid xsl:version="2.0">
                <xsl:value-of xmlns:uuid-utils="org.orbeon.oxf.util.UUIDUtils" select="uuid-utils:createPseudoUUID()"/>
            </uuid>
        </p:input>
        <p:output name="data" id="uuid"/>
    </p:processor>

    <!-- Save mapping from UUID to URL -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#uuid"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <key>
                    <xsl:value-of select="/uuid"/>
                </key>
                <scope>session</scope>
            </config>
        </p:input>
        <p:output name="data" id="scope-config"/>
    </p:processor>
    <p:processor name="oxf:scope-serializer">
        <p:input name="config" href="#scope-config"/>
        <p:input name="data" href="#url"/>
    </p:processor>

    <!-- Return UUID -->
    <p:processor name="oxf:identity">
        <p:input name="data" href="#uuid"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
