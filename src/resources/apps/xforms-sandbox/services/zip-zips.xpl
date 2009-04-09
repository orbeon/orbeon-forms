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

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter[name = 'state-abbreviation' or name = 'city' or name = 'max']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="request" href="#request"/>
        <p:input name="data" href="zip-flat.xml"/>
        <p:input name="config">
            <zips xsl:version="2.0">
                <xsl:variable name="parameters" as="element(parameter)*" select="doc('input:request')/request/parameters/parameter"/>
                <xsl:variable name="state-abbreviation" as="xs:string?" select="$parameters[name = 'state-abbreviation']/value"/>
                <xsl:variable name="city" as="xs:string?" select="$parameters[name = 'city']/value"/>
                <xsl:variable name="max" as="xs:integer?" select="$parameters[name = 'max']/value"/>
                <xsl:variable name="zips" as="element(zip)+" select="/zips/zip[state-abbreviation = $state-abbreviation and city = $city]"/>
                <xsl:for-each select="$zips[empty($max) or position() lt $max]">
                    <xsl:sort select="code"/>
                    <zip code="{code}" latitute="{latitute}" longitude="{longitude}"/>
                </xsl:for-each>
            </zips>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
