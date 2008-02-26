<?xml version="1.0" encoding="utf-8"?>
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude">

    <p:param type="input" name="instance"/>

    <p:processor name="oxf:identity">
        <p:input name="data">
            <config>
                <key>fr-upload-form-data</key>
                <scope>session</scope>
            </config>
        </p:input>
        <p:output name="data" id="saved-form-data-config"/>
    </p:processor>

    <!-- Check if we have some data that was just uploaded -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config" href="#saved-form-data-config"/>
        <p:output name="data" id="saved-form-data"/>
    </p:processor>

    <p:choose href="#saved-form-data">

        <!-- No form data was uploaded -->
        <p:when test="/*/@xsi:nil = 'true'">
            <!-- Start from empty template -->
            <p:processor name="oxf:url-generator">
                <p:input name="config" href="aggregate('config', aggregate('url', #instance#xpointer(concat(
                                                'oxf:/forms/', /*/app, '/', /*/form, '/template.xml'))))"/>
                <p:output name="data" id="template"/>
            </p:processor>
        </p:when>

        <!-- Use saved data -->
        <p:otherwise>
            <!-- Remove saved data from session -->
            <p:processor name="oxf:scope-serializer">
                <p:input name="config" href="#saved-form-data-config"/>
                <p:input name="data">
                    <root xsi:nil="true"/>
                </p:input>
            </p:processor>
            <!-- Return uploaded data -->
            <p:processor name="oxf:identity">
                <p:input name="data" href="#saved-form-data"/>
                <p:output name="data" id="template" debug="zzz"/>
            </p:processor>
        </p:otherwise>

    </p:choose>

    <!-- Convert and serialize to XML -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <indent>false</indent>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#template"/>
        <p:output name="data" id="converted"/>
    </p:processor>
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>
