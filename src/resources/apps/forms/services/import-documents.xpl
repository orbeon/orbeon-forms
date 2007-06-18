<!--
    Copyright (C) 2006 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:pipeline xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- List of documents to import -->
    <p:processor name="oxf:identity">
        <p:input name="data">
            <documents>
                <name>DMV-14/documents/document-1.xml</name>
                <name>DMV-14/documents/document-2.xml</name>
                <name>DMV-14/documents/document-3.xml</name>
                <name>DMV-14/documents/document-4.xml</name>
                <name>DMV-14/documents/document-5.xml</name>
                <name>G-325A/documents/document-1.xml</name>
                <name>G-325A/documents/document-2.xml</name>
            </documents>
        </p:input>
        <p:output name="data" id="documents"/>
    </p:processor>

    <!-- For each document, read it, format it and insert it -->
    <p:for-each href="#documents" select="/*/name">

        <!-- Retrieve local document by name -->
        <p:processor name="oxf:url-generator">
            <p:input name="config" href="aggregate('config', aggregate('url', current()#xpointer(concat('../forms/', string(/)))))"/>
            <p:output name="data" id="document"/>
        </p:processor>

        <!-- Create REST submission -->
        <p:processor name="oxf:xslt">
            <p:input name="config">
                <xforms:submission xmlns:xforms="http://www.w3.org/2002/xforms" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                   xsl:version="2.0" method="put" action="/exist/rest/db/orbeon/forms/{tokenize(/*, '/')[1]}/{{digest(string(random(true)), 'MD5', 'hex')}}"/>
            </p:input>
            <p:input name="data" href="current()"/>
            <p:output name="data" id="submission"/>
        </p:processor>

        <!-- Execute REST submission -->
        <p:processor name="oxf:xforms-submission">
            <p:input name="submission" href="#submission"/>
            <p:input name="request" href="#document"/>
            <p:output name="response" id="response"/>
        </p:processor>

        <p:processor name="oxf:null-serializer">
            <p:input name="data" href="#response"/>
        </p:processor>

    </p:for-each>

</p:pipeline>
