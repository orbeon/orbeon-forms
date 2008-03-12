<?xml version="1.0" encoding="utf-8"?>
<!--
    Copyright (C) 2008 Orbeon, Inc.

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
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <!-- Call up persistence layer to obtain XHTML+XForms -->
    <p:processor name="oxf:xforms-submission">
        <p:input name="submission">
            <xforms:submission serialization="none" method="get" replace="instance"
                    resource="{/*/form-collection}/crud/{/*/app}/{/*/form}/form/form.xhtml">

                <!-- Called before submitting -->
                <xforms:action ev:event="xforms-submit">
                    <!-- Initialize URI based on hierarchy of properties -->

                    <!-- TODO: This code must be moved to a function (see persistence-model.xml) -->
                    <xxforms:variable name="prefix" select="'oxf.fr.persistence.app'"/>
                    <xxforms:variable name="app" select="/*/app"/>
                    <xxforms:variable name="form" select="/*/form"/>
                    <xxforms:variable name="suffix" select="'uri'"/>

                    <!-- List of properties from specific to generic -->
                    <xxforms:variable name="form-properties"
                                      select="(string-join(($prefix, $app, $form, 'form', $suffix), '.'),
                                               string-join(($prefix, $app, $form, $suffix), '.'),
                                               string-join(($prefix, $app, $suffix), '.'),
                                               string-join(($prefix, '*', $suffix), '.'))"/>

                    <!-- Create new element to hold computed element -->
                    <xxforms:variable name="instance" select="/*"/>
                    <xforms:insert context="$instance" nodeset="*" origin="xxforms:element('form-collection')"/>

                    <!-- Iterate to find property -->
                    <xforms:action xxforms:iterate="$form-properties">
                        <xforms:action if="not(empty(xxforms:property(context()))) and $instance/form-collection = ''">
                            <xforms:setvalue ref="$instance/form-collection" value="xxforms:property(context())"/>
                        </xforms:action>
                    </xforms:action>
                </xforms:action>

                <!-- Called in case of error -->
                <xforms:action ev:event="xforms-submit-error">
                    <!-- TODO: Propagate error to caller -->
                    <xforms:delete while="/*/*" nodeset="/*/*"/>
                    <xforms:setvalue ref="/*" value="event('response-body')"/>
                    <xforms:message level="xxforms:log-error"><xforms:output value="event('response-body')"/></xforms:message>
                </xforms:action>
            </xforms:submission>
        </p:input>
        <p:input name="request" href="#instance" debug="xxx"/>
        <p:output name="response" ref="data"/>
    </p:processor>

    <!-- TODO: Use digest to allow caching by XForms processor -->

</p:config>
