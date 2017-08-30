<!--
    Copyright (C) 2005 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>


    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter[name = 'url']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Create URL generator configuration and generate data from the URL -->
    <p:processor name="oxf:xslt">
        <p:input name="data"><dummy/></p:input>
        <p:input name="instance" href="#instance"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <config xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:variable name="instance" as="document-node()" select="doc('input:instance')"/>
                <xsl:variable name="request" as="document-node()" select="doc('input:request')"/>
                <xsl:variable name="url-parameter" as="xs:string?" select="$request/request/parameters/parameter/value"/>
                <url>
                    <!-- Also check that the URL supplied as a parameter starts with http:// for security reasons -->
                    <xsl:value-of select="if ($instance/form) then $instance/form/file[1]
                        else if (tokenize($url-parameter, ':')[1] = ('http', 'https')) then $url-parameter
                        else ()"/>
                </url>
                <content-type>application/xml</content-type>
                <force-content-type>true</force-content-type>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:output name="data" id="url-config"/>
    </p:processor>
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-config"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
