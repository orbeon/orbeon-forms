<!--
  Copyright (C) 2012 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:f="http//www.orbeon.com/function"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:exist="http://exist.sourceforge.net/NS/exist">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Using separate XSLT to avoid useless namespace declarations in XML document sent to eXist-db -->
    <p:processor name="oxf:xslt">
        <p:input name="data"><dummy/></p:input>
        <p:input name="config">
            <exist:query max="0" xsl:version="2.0" xsl:exclude-result-prefixes="#all"><exist:text>
                declare namespace xh="http://www.w3.org/1999/xhtml";
                declare namespace xf="http://www.w3.org/2002/xforms";

                (: Retrieve the metadata for the deployed form, which are under /db/orbeon/fr/*/*/form/form.xhtml :)
                (: The beginning of the path, /db/orbeon/fr, is part of the eXist URI we get from the orbeon-exist-uri header :)

                declare variable $fr-path := request:get-path-info();
                declare variable $request-app := request:get-parameter('app', '');
                declare variable $request-form := request:get-parameter('form', '');
                if (xmldb:collection-available($fr-path)) then
                    for $app  in xmldb:get-child-collections($fr-path)[$request-app = '' or . = $request-app],
                        $form in xmldb:get-child-collections(concat($fr-path, '/', $app))[$request-form = '' or . = $request-form]
                    let $coll     := concat($fr-path, '/', $app, '/', $form, '/form')
                    let $doc-path := concat($coll, '/form.xhtml')
                    where doc-available($doc-path)
                    order by xmldb:last-modified($coll, 'form.xhtml') descending
                    return
                        element form {
                            doc($doc-path)/xh:html/xh:head/xf:model/xf:instance[@id = 'fr-form-metadata']/metadata/*,
                            element last-modified-time {
                                xmldb:last-modified($coll, 'form.xhtml')
                            }
                        }
                else ()
            </exist:text></exist:query>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <p:processor name="oxf:xforms-submission">
        <p:input name="request" href="#query"/>
        <p:input name="submission" transform="oxf:xslt" href="#instance">
            <xf:submission
                xsl:version="2.0"
                method="post"
                replace="instance"
                resource="{{
                        for $uri in xxf:get-request-header('orbeon-exist-uri')[1]
                        return
                            if (ends-with($uri, '/')) then
                                substring($uri, 1, string-length($uri) - 1)
                            else
                                $uri
                    }}?app={
                        encode-for-uri(/request/app)
                    }&amp;form={
                        encode-for-uri(/request/form)
                    }">
                <xf:insert event="xforms-submit-done" ref="/*" origin="xf:element('forms', *)"/>
                <xi:include href="exist-submission-common.xml" xpointer="xpath(/root/*)"/>
            </xf:submission>
        </p:input>
        <p:output name="response" ref="data"/>
    </p:processor>

</p:config>