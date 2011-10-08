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
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <!-- Extract parameters -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/container-type</include>
                <include>/request/parameters</include>
            </config>
        </p:input>
        <!--<p:output name="data" id="request-params" debug="xxxrequest-params"/>-->
        <p:output name="data" id="request-params"/>
    </p:processor>

    <p:choose href="#request-params">
        <!-- Second pass of a submission with replace="all" -->
        <p:when test="not(/*/parameters/parameter[name = '$noscript']/value = 'true')">
            <!-- Create XForms Server request -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#request-params"/>
                <p:input name="config">
                    <xxforms:event-request xsl:version="2.0" xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">
                        <xxforms:uuid>
                            <xsl:value-of select="/*/parameters/parameter[name = '$uuid']/value"/>
                        </xxforms:uuid>
                        <!-- Omit sequence number -->
                        <xxforms:sequence/>
                        <xxforms:static-state>
                            <xsl:value-of select="/*/parameters/parameter[name = '$static-state']/value"/>
                        </xxforms:static-state>
                        <xxforms:dynamic-state>
                            <xsl:value-of select="/*/parameters/parameter[name = '$dynamic-state']/value"/>
                        </xxforms:dynamic-state>
                        <!-- Only include files and omit all other parameters -->
                        <xsl:variable name="files" select="/*/parameters/parameter[filename]"/>
                        <xsl:if test="$files">
                            <xxforms:files>
                                <xsl:copy-of select="$files"/>
                            </xxforms:files>
                        </xsl:if>
                        <xxforms:action/>
                        <xsl:variable name="server-events" select="/*/parameters/parameter[name = '$server-events']/value"/>
                        <xsl:if test="not($server-events = '')">
                            <xxforms:server-events>
                                <xsl:value-of select="$server-events"/>
                            </xxforms:server-events>
                        </xsl:if>
                    </xxforms:event-request>
                </p:input>
                <!--<p:output name="data" id="xml-request" debug="xxxsubmit-request"/>-->
                <p:output name="data" id="xml-request"/>
            </p:processor>

            <!-- Run XForms Server -->
            <p:processor name="oxf:xforms-server">
                <p:input name="request" href="#xml-request" schema-href="xforms-server-request.rng"/>
            </p:processor>
        </p:when>
        <!-- Noscript mode response -->
        <p:otherwise>
            <!-- Create XForms Server request -->
            <p:processor name="oxf:unsafe-xslt">
                <p:input name="data" href="#request-params"/>
                <p:input name="config">
                    <xxforms:event-request xsl:version="2.0" xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">
                        <xxforms:uuid>
                            <xsl:value-of select="/*/parameters/parameter[name = '$uuid']/value"/>
                        </xxforms:uuid>
                        <!-- Omit sequence number -->
                        <xxforms:sequence/>
                        <xxforms:static-state>
                            <xsl:value-of select="/*/parameters/parameter[name = '$static-state']/value"/>
                        </xxforms:static-state>
                        <xxforms:dynamic-state>
                            <xsl:value-of select="/*/parameters/parameter[name = '$dynamic-state']/value"/>
                        </xxforms:dynamic-state>
                        <!-- Handle files -->
                        <xsl:variable name="files" select="/*/parameters/parameter[filename and normalize-space(value)]"/>
                        <xsl:if test="$files">
                            <xxforms:files>
                                <xsl:copy-of select="$files"/>
                            </xxforms:files>
                        </xsl:if>
                        <xxforms:action>
                            <!-- Create list of events based on parameters -->
                            <xsl:for-each select="/*/parameters/parameter[not(starts-with(name, '$') or ends-with(name, '.y') or exists(filename))]">
                                <!-- Here we don't know the type of the control so can't create the proper type of event -->

                                <!-- If the parameter name ends with "[]", remove it. This is to support PHP-based proxies, which might add the brackets. -->
                                <xsl:variable name="name" as="xs:string"
                                              select="for $n in name return if (ends-with($n, '[]')) then substring($n, 1, string-length($n) - 2) else $n"/>

                                <xsl:variable name="value" as="xs:string*" select="value"/>

                                <!-- For input[@type = 'image'], filter .y events above, and remove ending .x below -->
                                <xxforms:event name="xxforms-value-or-activate"
                                                       source-control-id="{if (ends-with($name, '.x')) then substring($name, 1, string-length($name) - 2) else $name}">
                                    <xsl:choose>
                                        <xsl:when test="contains($name, '$xforms-input-1')">
                                            <!-- Case of xforms:input, which may have two HTML input controls -->
                                            <xsl:variable name="source-control-id" select="replace($name, '\$xforms-input-1', '')"/>
                                            <xsl:variable name="other-control-id" select="replace($name, '\$xforms-input-1', '\$xforms-input-2')"/>
                                            <xsl:attribute name="source-control-id" select="$source-control-id"/>
                                            <xsl:variable name="second-input" select="../parameter[name = $other-control-id]"/>
                                            <xsl:choose>
                                                <xsl:when test="$second-input">
                                                    <!-- This is a bit of a hack: we concatenate the two values with a
                                                    separator. In the future, use a component for dateTime anyway. -->
                                                    <xsl:value-of select="concat($value, '·', $second-input/value)"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <xsl:value-of select="$value"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </xsl:when>
                                        <xsl:when test="contains($name, '$xforms-input-2')">
                                            <!-- NOP: handled with $xforms-input-1 -->
                                        </xsl:when>
                                        <xsl:when test="ends-with($name, '.x')">
                                            <!-- input[@type = 'image'] -->
                                            <xsl:attribute name="source-control-id" select="substring($name, 1, string-length($name) - 2)"/>
                                            <xsl:value-of select="$value"/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <!-- Regular case -->
                                            <xsl:attribute name="source-control-id" select="$name"/>
                                            <!-- There may be several times the same value with selection controls: keep only one so as not to confuse xforms:select1 -->
                                            <xsl:value-of select="string-join(distinct-values($value), ' ')"/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xxforms:event>
                            </xsl:for-each>
                        </xxforms:action>
                        <!-- There shouldn't be any server events, but process them if there are any (future use) -->
                        <xsl:variable name="server-events" select="/*/parameters/parameter[name = '$server-events']/value"/>
                        <xsl:if test="$server-events != ''">
                            <xxforms:server-events>
                                <xsl:value-of select="$server-events"/>
                            </xxforms:server-events>
                        </xsl:if>
                    </xxforms:event-request>
                </p:input>
                <!--<p:output name="data" id="xml-request" debug="xxxsubmit-request"/>-->
                <p:output name="data" id="xml-request"/>
            </p:processor>

            <!-- Run XForms Server -->
            <p:processor name="oxf:xforms-server">
                <p:input name="request" href="#xml-request" schema-href="xforms-server-request.rng"/>
                <p:output name="response" id="xformed-data"/>
            </p:processor>

            <!-- Call epilogue -->
            <!-- NOTE: We either get an XHTML document produced by the XForms engine, a binary document in case there
                 was a submission with replace="all", or a null document -->
            <p:choose href="#xformed-data">
                <p:when test="not(/null[@xsi:nil = 'true'])">

                    <!-- Combine resources if needed -->
                    <p:processor name="oxf:resources-aggregator">
                        <p:input name="data" href="#xformed-data"/>
                        <p:output name="data" id="aggregated-data"/>
                    </p:processor>

                    <!-- Choose which epilogue to call depending on container type -->
                    <p:choose href="#request-params">
                        <!-- If the container is a servlet, call the servlet epilogue pipeline -->
                        <p:when test="/request/container-type = 'servlet'">
                            <p:processor name="oxf:pipeline">
                                <p:input name="config" href="/config/epilogue-servlet.xpl"/>
                                <p:input name="data"><null xsi:nil="true"/></p:input>
                                <p:input name="xformed-data" href="#aggregated-data"/>
                                <p:input name="instance"><null xsi:nil="true"/></p:input>
                            </p:processor>
                        </p:when>
                        <!-- If the container is a portlet, call the portlet epilogue pipeline -->
                        <p:otherwise>
                            <p:processor name="oxf:pipeline">
                                <p:input name="config" href="/config/epilogue-portlet.xpl"/>
                                <p:input name="data"><null xsi:nil="true"/></p:input>
                                <p:input name="xformed-data" href="#aggregated-data"/>
                                <p:input name="instance"><null xsi:nil="true"/></p:input>
                            </p:processor>
                        </p:otherwise>
                    </p:choose>
                </p:when>
            </p:choose>
        </p:otherwise>
    </p:choose>

</p:config>
