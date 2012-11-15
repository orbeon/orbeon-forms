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
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:f="http//www.orbeon.com/function"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:exist="http://exist.sourceforge.net/NS/exist">

    <p:param name="data" type="output"/>

    <p:processor name="oxf:xforms-submission">
        <p:input name="request">
            <exist:query max="0"><exist:text>
                declare namespace xhtml="http://www.w3.org/1999/xhtml";
                declare namespace xforms="http://www.w3.org/2002/xforms";

                (: Retrieve the metadata for the deployed form, which are under /db/orbeon/fr/*/*/form/form.xhtml :)
                (: The beginning of the path, /db/orbeon/fr, is part of the eXist URI we get from the orbeon-exist-uri header :)

                (: TODO: use "current collection" instead of hard coding /db/orbeon/fr, once we know how to get the current collection in XQuery :)

                for $app in xmldb:get-child-collections('/db/orbeon/fr'),
                    $form in xmldb:get-child-collections(concat('/db/orbeon/fr/', $app))
                return
                    element form {
                        doc(concat('/db/orbeon/fr/', $app, '/', $form, '/form/form.xhtml'))
                        /xhtml:html/xhtml:head/xforms:model/xforms:instance[@id = 'fr-form-metadata']/metadata/*
                    }
            </exist:text></exist:query>
        </p:input>
        <p:input name="submission">
            <xforms:submission method="post" replace="instance"
                               resource="{xxforms:get-request-header('orbeon-exist-uri')}">
                <xforms:insert ev:event="xforms-submit-done" ref="/*" origin="xxforms:element('forms', *)"/>
                <xi:include href="propagate-exist-error.xml" xpointer="xpath(/root/*)"/>
            </xforms:submission>
        </p:input>
        <p:output name="response" ref="data"/>
    </p:processor>

</p:config>