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
<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Extract page detail (app, form, document, and mode) from URL -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Put app, form, and mode in format understood by read-form.xpl -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#request"/>
        <p:input name="config">
            <request xsl:version="2.0">
                <app><xsl:value-of select="/request/parameters/parameter[name = 'application']/value"/></app>
                <form>library</form>
                <form-version><xsl:value-of select="/request/parameters/parameter[name = 'app-library-version']/value"/></form-version>
                <document/>
                <mode/>
            </request>
        </p:input>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#request"/>
        <p:input name="config">
            <request xsl:version="2.0">
                <app>orbeon</app>
                <form>library</form>
                <form-version><xsl:value-of select="/request/parameters/parameter[name = 'orbeon-library-version']/value"/></form-version>
                <document/>
                <mode/>
            </request>
        </p:input>
        <p:output name="data" id="global-parameters"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input  name="config"     href="toolbox.xsl"/>
        <p:input  name="data"               ><dummy/></p:input>
        <p:input  name="global-template-xbl"><dummy/></p:input>
        <p:input  name="custom-template-xbl"><dummy/></p:input>
        <p:input  name="request"   href="#request"/>
        <p:output name="data"      id="components-but-not-section-templates"/>
    </p:processor>

    <!-- Read template form for global orbeon library -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/apps/fr/detail/read-form.xpl"/>
        <p:input name="instance" href="#global-parameters"/>
        <p:output name="data" id="global-template-form"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config"     href="/forms/orbeon/builder/form/annotate.xpl"/>
        <p:input name="data"       href="#global-template-form"/>
        <p:input name="bindings"   href="#components-but-not-section-templates"/>
        <p:output name="data"      id="global-template-form-annotated"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config"     href="/forms/orbeon/builder/form/deannotate.xpl"/>
        <p:input name="data"       href="#global-template-form-annotated"/>
        <p:output name="data"      id="global-template-form-deannotated"/>
    </p:processor>

    <p:processor name="oxf:exception-catcher">
        <p:input name="config"><config><stack-trace>false</stack-trace></config></p:input>
        <p:input name="data" href="#global-template-form-deannotated"/>
        <p:output name="data" id="global-template-form-safe"/>
    </p:processor>

    <!-- Read template form for application library -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/apps/fr/detail/read-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:output name="data" id="custom-template-form"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config"     href="/forms/orbeon/builder/form/annotate.xpl"/>
        <p:input name="data"       href="#custom-template-form"/>
        <p:input name="bindings"   href="#components-but-not-section-templates"/>
        <p:output name="data"      id="custom-template-form-annotated"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config"     href="/forms/orbeon/builder/form/deannotate.xpl"/>
        <p:input name="data"       href="#custom-template-form-annotated"/>
        <p:output name="data"      id="custom-template-form-deannotated"/>
    </p:processor>

    <p:processor name="oxf:exception-catcher">
        <p:input name="config"><config><stack-trace>false</stack-trace></config></p:input>
        <p:input name="data" href="#custom-template-form-deannotated"/>
        <p:output name="data" id="custom-template-form-safe"/>
    </p:processor>

    <!-- Convert templates to XBL -->

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config"     href="form-to-xbl.xsl"/>
        <p:input name="data"       href="#global-template-form-safe"/>
        <p:input name="parameters" href="#global-parameters"/>
        <p:output name="data"      id="global-template-xbl"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config"     href="form-to-xbl.xsl"/>
        <p:input name="data"       href="#custom-template-form-safe"/>
        <p:input name="parameters" href="#parameters"/>
        <p:output name="data"      id="custom-template-xbl"/>
    </p:processor>

    <!-- Aggregate results -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config" href="toolbox.xsl"/>
        <p:input name="data"><dummy/></p:input>
        <p:input name="global-template-xbl" href="#global-template-xbl"/>
        <p:input name="custom-template-xbl" href="#custom-template-xbl"/>
        <p:input name="request" href="#request"/>
        <p:output name="data" id="components"/>
    </p:processor>

    <p:processor name="oxf:xml-converter">
        <p:input name="data" href="#components"/>
        <p:input name="config"><config/></p:input>
        <p:output name="data" id="components-xml"/>
    </p:processor>

    <!-- Serialize out -->
    <!--
        WARNING: Don't remove the XSLT transformation until we have fixed:

            https://github.com/orbeon/orbeon-forms/issues/1363

        See the bug description:

            https://github.com/orbeon/orbeon-forms/issues/1881
    -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#components-xml">
            <config xsl:version="2.0">
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#components-xml"/>
    </p:processor>

</p:config>
