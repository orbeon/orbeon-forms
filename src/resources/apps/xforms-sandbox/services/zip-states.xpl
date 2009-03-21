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

    <p:processor name="oxf:xslt">
        <p:input name="data" href="zip-flat.xml"/>
        <p:input name="config">
            <states xsl:version="2.0">
                <xsl:variable name="zips" as="element(zip)+" select="/zips/zip"/>
                <xsl:for-each select="distinct-values($zips/state-abbreviation)">
                    <xsl:sort/>
                    <xsl:variable name="abbreviation" as="xs:string" select="."/>
                    <state abbreviation="{$abbreviation}" name="{($zips[state-abbreviation = $abbreviation])[1]/state-name}"/>
                </xsl:for-each>
            </states>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
