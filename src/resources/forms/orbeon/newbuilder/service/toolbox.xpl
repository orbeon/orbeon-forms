<?xml version="1.0" encoding="utf-8"?>
<!--
  Copyright (C) 2010 Orbeon, Inc.

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
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <!-- Extract page detail (app, form, document, and mode) from URL -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>
    <!--<p:processor name="oxf:perl5-matcher">-->
        <!--<p:input name="config"><config>/fr/service/components/([^/]+)/([^/]+)</config></p:input>-->
        <!--<p:input name="data" href="#request#xpointer(/request/request-path)"/>-->
        <!--<p:output name="data" id="matcher-groups"/>-->
    <!--</p:processor>-->

    <!-- Put app, form, and mode in format understood by read-form.xpl -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#request"/>
        <p:input name="config">
            <request xsl:version="2.0">
                <app><xsl:value-of select="/request/parameters/parameter[name = 'application']/value"/></app>
                <form>library</form>
                <document/>
                <mode/>
            </request>
        </p:input>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data">
            <request>
                <app>orbeon</app>
                <form>library</form>
                <document/>
                <mode/>
            </request>
        </p:input>
        <p:output name="data" id="global-parameters"/>
    </p:processor>

    <!-- Read template form for global orbeon library -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/apps/fr/detail/read-form.xpl"/>
        <p:input name="instance" href="#global-parameters"/>
        <p:output name="data" id="global-template-form"/>
    </p:processor>

    <!-- Read template form for application library -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/apps/fr/detail/read-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:output name="data" id="custom-template-form"/>
    </p:processor>

    <!-- Convert templates to XBL -->
    <p:processor name="oxf:xslt">
        <p:input name="config" href="form-to-xbl.xsl"/>
        <p:input name="data" href="#global-template-form"/>
        <p:input name="parameters" href="#global-parameters"/>
        <p:output name="data" id="global-template-xbl"/>
    </p:processor>
    <p:processor name="oxf:xslt">
        <p:input name="config" href="form-to-xbl.xsl"/>
        <p:input name="data" href="#custom-template-form"/>
        <p:input name="parameters" href="#parameters"/>
        <p:output name="data" id="custom-template-xbl"/>
    </p:processor>

    <!-- Aggregate results -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#global-template-xbl"/>
        <p:input name="custom-template-xbl" href="#custom-template-xbl"/>
        <p:input name="parameters" href="#parameters"/>
        <!-- Externalize to limit impact of namespaces declared in this pipeline -->
        <p:input name="config" href="toolbox.xsl"/>
        <!--<p:output name="data" ref="data"/>-->
        <p:output name="data" id="components"/>
    </p:processor>

    <p:processor name="oxf:xml-converter">
        <p:input name="data" href="#components"/>
        <p:input name="config"><config/></p:input>
        <p:output name="data" id="components-xml"/>
    </p:processor>

    <!-- Serialize out as is -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#components-xml">
            <config xsl:version="2.0">
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
                <!--<header>-->
                    <!--<name>Last-Modified</name>-->
                    <!--<value>-->
                         <!-- Format the date -->
                         <!-- TODO: extract meaningful date in eXist CRUD! -->
                        <!--<xsl:value-of select="format-dateTime(xs:dateTime('2008-11-18T00:00:00'), '[FNn,*-3], [D] [MNn,*-3] [Y] [H01]:[m01]:[s01] GMT', 'en', (), ()) "/>-->
                    <!--</value>-->
                <!--</header>-->
            </config>
        </p:input>
        <p:input name="data" href="#components-xml"/>
    </p:processor>

</p:config>
