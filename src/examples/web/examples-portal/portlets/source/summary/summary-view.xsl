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
<html xsl:version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xi="http://www.w3.org/2003/XInclude"
    xmlns="http://www.w3.org/1999/xhtml">

    <xsl:variable name="example-descriptor" select="/*" as="element()"/>

    <head>
        <title><xsl:value-of select="$example-descriptor/title"/> - Example Source Code</title>
        <style type="text/css">
            .xforms-repeat-selected-item-1 { background: white; }
            .xforms-repeat-selected-item-2 { background: white; }
        </style>
        <xforms:model id="main-model">

            <!-- This instance contains the entire example descriptor -->
            <xforms:instance id="descriptor-instance">
                <xi:include href="input:data"/>
            </xforms:instance>

            <!-- This instance contains the example id -->
            <xforms:instance id="submission-instance">
                <xi:include href="input:instance"/>
            </xforms:instance>

            <!-- Control instance -->
            <xforms:instance id="control-instance">
                <control xmlns="">
                    <path/>
                    <show-xml>false</show-xml>
                    <xml-trigger/>
                    <text-trigger/>
                    <current-index/>
                </control>
            </xforms:instance>

            <!-- Source request instance -->
            <xforms:instance id="source-request-instance">
                <form xmlns="">
                    <example-id/>
                    <source-url/>
                    <mediatype/>
                </form>
            </xforms:instance>

            <!-- Source response instance -->
            <xforms:instance id="source-response-instance">
                <document xmlns=""/>
            </xforms:instance>

            <!-- Make sure there are no backslashes in file names -->
            <xforms:bind nodeset="instance('descriptor-instance')//file/@name" calculate="replace(., '\\', '/')"/>

            <xforms:bind nodeset="instance('source-request-instance')">
                <xforms:bind nodeset="example-id" calculate="instance('submission-instance')/example-id"/>
                <xforms:bind nodeset="source-url" calculate="if (. = '' and instance('submission-instance')/source-url != '')
                                                             then instance('submission-instance')/source-url
                                                             else instance('descriptor-instance')//file[index('files-repeat')]/@name"/>
                <xforms:bind nodeset="mediatype"
                             calculate="if (instance('control-instance')/show-xml = 'false' or ends-with(../source-url, '.txt') or ends-with(../source-url, '.java'))
                                        then 'text/plain' else 'application/xml'"/>
            </xforms:bind>

            <xforms:bind nodeset="instance('control-instance')">
                <xforms:bind nodeset="xml-trigger" readonly="../show-xml = 'true'"/>
                <xforms:bind nodeset="text-trigger" readonly="not(../show-xml = 'true')"/>
            </xforms:bind>

            <xforms:submission id="request-source-submission" ref="instance('source-request-instance')" method="get" separator="&amp;"
                action="/source/service/format" replace="instance" instance="source-response-instance"/>

            <xforms:action ev:event="xforms-ready">
                <xforms:setindex repeat="files-repeat" index="count(instance('descriptor-instance')//file[@name = instance('source-request-instance')/source-url]/preceding-sibling::file) + 1"/>
                <xforms:dispatch name="ops-format-source" target="main-model"/>
            </xforms:action>

            <xforms:action ev:event="ops-format-source">
                <xforms:recalculate/>
                <xforms:setvalue ref="instance('control-instance')/current-index" value="index('files-repeat')"/>
                <xforms:send submission="request-source-submission"/>
                <xforms:setindex repeat="files-repeat" index="instance('control-instance')/current-index"/>
            </xforms:action>

        </xforms:model>
    </head>
    <body>
        <!-- Toolbar -->
        <table class="ops-action-table">
            <tr>
                <td>
                    <xforms:group ref="instance('control-instance')/text-trigger">
                        <xforms:action ev:event="DOMActivate" >
                            <xforms:setvalue ref="instance('control-instance')/show-xml">false</xforms:setvalue>
                            <xforms:dispatch ev:event="DOMActivate" name="ops-format-source" target="main-model"/>
                        </xforms:action>
                        <xforms:trigger appearance="xxforms:image">
                            <xforms:label>View as text</xforms:label>
                            <xxforms:img src="/images/text.gif"/>
                        </xforms:trigger>
                        <xforms:trigger appearance="xxforms:link">
                            <xforms:label>View as text</xforms:label>
                        </xforms:trigger>
                    </xforms:group>
                </td>
                <td>
                    <xforms:group ref="instance('control-instance')/xml-trigger">
                        <xforms:action ev:event="DOMActivate" >
                            <xforms:setvalue ref="instance('control-instance')/show-xml">true</xforms:setvalue>
                            <xforms:dispatch ev:event="DOMActivate" name="ops-format-source" target="main-model"/>
                        </xforms:action>
                        <xforms:trigger appearance="xxforms:image">
                            <xforms:label>View as formatted XML</xforms:label>
                            <xxforms:img src="/images/view-xml.gif"/>
                        </xforms:trigger>
                        <xforms:trigger appearance="xxforms:link">
                            <xforms:label>View as formatted XML</xforms:label>
                        </xforms:trigger>
                    </xforms:group>
                </td>
                <td>
                    <xforms:group>
                        <xforms:action ev:event="DOMActivate" >
                            <xforms:setvalue ref="instance('control-instance')/path" value="concat('/source/download/', instance('submission-instance')/example-id, '/', instance('descriptor-instance')//file[index('files-repeat')]/@name)"/>
                            <xforms:load ref="instance('control-instance')/path" f:url-type="resource"/>
                        </xforms:action>
                        <xforms:trigger appearance="xxforms:image">
                            <xforms:label>Download</xforms:label>
                            <xxforms:img src="/images/download.gif"/>
                        </xforms:trigger>
                        <xforms:trigger appearance="xxforms:link">
                            <xforms:label>Download</xforms:label>
                        </xforms:trigger>
                    </xforms:group>
                </td>
            </tr>
        </table>

        <!-- List of files -->
        <table style="border: solid 1px #f93">
            <tr>
                <td style="vertical-align: top">
                    <table>
                        <!--<tr>-->
                            <!--<th>Name</th>-->
                            <!--<th>Bytes</th>-->
                        <!--</tr>-->
                        <xforms:group>
                            <xforms:dispatch ev:event="DOMActivate" name="ops-format-source" target="main-model"/>
                            <xforms:repeat nodeset="instance('descriptor-instance')/source-files/file" id="files-repeat">
                                <tr>
                                    <td style="white-space: nowrap">
                                        <!--<xforms:output ref="@name"/>-->
                                        <xforms:trigger appearance="xxforms:link">
                                            <xforms:label ref="@name"/>
                                        </xforms:trigger>
                                    </td>
                                    <td style="text-align: right">
                                        <xforms:output value="format-number(@size, '###,##0')"/>
                                    </td>
                                </tr>
                            </xforms:repeat>
                        </xforms:group>
                        <tr/>
                    </table>
                </td>
                <td style="vertical-align: top; width: 100%">
                    <table style="width: 100%">
                        <tr>
                            <td style="padding: 0px">
                                <div class="ops-source">
                                    <xforms:output ref="instance('source-response-instance')" mediatype="text/html"/>
                                </div>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>

    </body>
</html>
