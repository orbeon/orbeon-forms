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

    <p:param name="data" type="output"/>

    <!-- Wait for 5 seconds -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="sleep-5-seconds.xpl"/>
        <p:output name="data" id="sleep"/>
    </p:processor>
    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#sleep"/>
    </p:processor>

    <!-- Read image -->
    <p:processor name="oxf:url-generator">
        <p:input name="config">
            <config>
                <url>oxf:/config/theme/images/orbeon-small-blueorange.gif</url>
                <content-type>image/gif</content-type>
            </config>
        </p:input>
        <p:output name="data" id="image-data"/>
    </p:processor>

    <!-- Send result through HTTP -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <!--<header>-->
                    <!--<name>Content-Disposition</name>-->
                    <!--<value>attachment; filename=<xsl:value-of select="replace(/instance/filename, ' ', '_')"/></value>-->
                <!--</header>-->
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#image-data"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data"><dummy/></p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
