<!--
  Copyright (C) 2026 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:p="http://www.orbeon.com/oxf/pipeline">

    <xsl:import href="oxf:/apps/fr/components/actions-common.xsl"/>

    <!-- Map a legacy XForms/FR event name to the corresponding 2018.2 event name -->
    <xsl:function name="fr:map-event-to-20182" as="xs:string?">
        <xsl:param name="event" as="xs:string"/>
        <xsl:choose>
            <xsl:when test="$event = $form-load-xforms-action-names">
                <xsl:sequence select="$form-load-2018.2-action-names[index-of($form-load-xforms-action-names, $event)]"/>
            </xsl:when>
            <xsl:when test="$event = $form-load-fr-action-names">
                <xsl:sequence select="$form-load-2018.2-action-names[index-of($form-load-fr-action-names, $event)]"/>
            </xsl:when>
            <xsl:when test="$event = $controls-xforms-action-names">
                <xsl:sequence select="$controls-2018.2-action-names[index-of($controls-xforms-action-names, $event)]"/>
            </xsl:when>
        </xsl:choose>
    </xsl:function>

    <!-- Get the string value of an `xf:var`: `@value`/`@select` attribute or text content -->
    <xsl:function name="fr:var-value" as="xs:string">
        <xsl:param name="var" as="element()"/>
        <xsl:sequence select="(($var/@value, $var/@select)[1]/string(), $var/string())[1]"/>
    </xsl:function>

    <!-- Strip outer single quotes from an XPath string literal: 'foo' → foo -->
    <xsl:function name="fr:xpath-string-value" as="xs:string">
        <xsl:param name="expr" as="xs:string"/>
        <xsl:variable name="t" select="normalize-space($expr)"/>
        <xsl:sequence select="
            if (starts-with($t, &quot;'&quot;) and ends-with($t, &quot;'&quot;))
            then substring($t, 2, string-length($t) - 2)
            else $t"/>
    </xsl:function>

    <!-- Extract mode names from a legacy @if condition; returns empty sequence for no restriction -->
    <xsl:function name="fr:legacy-if-to-modes" as="xs:string*">
        <xsl:param name="if-attr" as="xs:string?"/>
        <xsl:choose>
            <xsl:when test="empty($if-attr) or normalize-space($if-attr) = 'true()'"/>
            <xsl:when test="matches($if-attr, &quot;\$fr-mode\s*=\s*'(\w+)'&quot;)">
                <xsl:analyze-string select="$if-attr" regex="\$fr-mode\s*=\s*'(\w+)'">
                    <xsl:matching-substring><xsl:sequence select="regex-group(1)"/></xsl:matching-substring>
                    <xsl:non-matching-substring/>
                </xsl:analyze-string>
            </xsl:when>
            <xsl:when test="matches($if-attr, 'fr:mode\(\)\s*=')">
                <xsl:analyze-string select="$if-attr" regex="'(\w+)'">
                    <xsl:matching-substring><xsl:sequence select="regex-group(1)"/></xsl:matching-substring>
                    <xsl:non-matching-substring/>
                </xsl:analyze-string>
            </xsl:when>
        </xsl:choose>
    </xsl:function>

    <!-- Return the trigger xf:action child: the one that fires on a known form-load or control event -->
    <xsl:function name="fr:legacy-trigger" as="element(xf:action)?">
        <xsl:param name="binding" as="element(xf:action)"/>
        <xsl:sequence select="
            $binding/xf:action[
                tokenize((@event, @ev:event)[1], '\s+') = (
                    $form-load-fr-action-names,
                    $form-load-xforms-action-names,
                    $controls-xforms-action-names
                )
            ][1]"/>
    </xsl:function>

    <!-- Extract the service name from the trigger's xf:send/@submission (strips '-submission' suffix) -->
    <xsl:function name="fr:legacy-service-name" as="xs:string?">
        <xsl:param name="binding" as="element(xf:action)"/>
        <xsl:variable name="sub" select="fr:legacy-trigger($binding)/xf:send/@submission/string()"/>
        <xsl:if test="exists($sub) and ends-with($sub, '-submission')">
            <xsl:sequence select="substring-before($sub, '-submission')"/>
        </xsl:if>
    </xsl:function>

    <!-- Convert legacy action bindings to fr:listener + fr:action[@version='2018.2'] -->
    <xsl:template match="xf:action[ends-with(@id, '-binding')]">
        <xsl:variable name="binding"      select="."/>
        <xsl:variable name="action-name"  select="substring-before(@id, '-binding')"/>
        <xsl:variable name="trigger"      select="fr:legacy-trigger($binding)"/>
        <xsl:variable name="service-name" select="fr:legacy-service-name($binding)"/>
        <xsl:variable name="request-act"  select="xf:action[tokenize((@event, @ev:event)[1], '\s+') = 'xforms-submit'][1]"/>
        <xsl:variable name="response-act" select="xf:action[tokenize((@event, @ev:event)[1], '\s+') = 'xforms-submit-done'][1]"/>
        <xsl:variable name="data-condition" as="xs:string?" select="
            if (exists($trigger) and
                string($trigger/@if) != '' and
                normalize-space($trigger/@if) != 'true()' and
                empty(fr:legacy-if-to-modes($trigger/@if)))
            then string($trigger/@if)
            else ()"/>

        <xsl:if test="exists($trigger)">
            <xsl:variable name="raw-event"    select="($trigger/@event, $trigger/@ev:event)[1]"/>
            <xsl:variable name="mapped-event" select="fr:map-event-to-20182($raw-event)"/>
            <xsl:variable name="observer"     select="($trigger/@ev:observer, $trigger/@observer)[1]"/>
            <xsl:variable name="modes"        select="fr:legacy-if-to-modes($trigger/@if)"/>
            <xsl:variable name="is-ctrl-evt"  select="$mapped-event = $controls-2018.2-action-names"/>

            <xsl:if test="exists($mapped-event)">
                <fr:listener version="2018.2" events="{$mapped-event}" actions="{$action-name}">
                    <xsl:if test="$is-ctrl-evt">
                        <xsl:attribute name="controls" select="
                            string-join(
                                for $o in tokenize($observer, '\s+')
                                return
                                    if (ends-with($o, '-control'))
                                    then substring($o, 1, string-length($o) - string-length('-control'))
                                    else $o,
                                ' '
                            )"/>
                    </xsl:if>
                    <xsl:if test="exists($modes)">
                        <xsl:attribute name="modes" select="string-join($modes, ' ')"/>
                    </xsl:if>
                </fr:listener>
            </xsl:if>
        </xsl:if>

        <fr:action name="{$action-name}" version="2018.2">
            <xsl:variable name="action-body">
                <xsl:if test="exists($service-name)">
                    <fr:service-call service="{$service-name}">
                        <xsl:for-each select="
                            $request-act//xf:action[
                                tokenize(@class, '\s+') = 'fr-set-service-value-action'
                            ]">
                            <xsl:choose>
                                <xsl:when test="exists(xf:var[@name = 'control-name'])">
                                    <xsl:variable name="ctl"
                                        select="fr:xpath-string-value(fr:var-value(xf:var[@name = 'control-name']))"/>
                                    <xsl:choose>
                                        <xsl:when test="exists(xf:var[@name = 'path'])">
                                            <fr:value control="{$ctl}" ref="{fr:var-value(xf:var[@name = 'path'])}"/>
                                        </xsl:when>
                                        <xsl:when test="exists(xf:var[@name = 'parameter-name'])">
                                            <fr:url-param
                                                name="{fr:xpath-string-value(fr:var-value(xf:var[@name = 'parameter-name']))}"
                                                control="{$ctl}"/>
                                        </xsl:when>
                                        <xsl:when test="exists(xf:var[@name = 'header-name'])">
                                            <fr:header
                                                name="{fr:xpath-string-value(fr:var-value(xf:var[@name = 'header-name']))}"
                                                control="{$ctl}"/>
                                        </xsl:when>
                                    </xsl:choose>
                                </xsl:when>
                                <xsl:when test="exists(xf:var[@name = 'source-path']) and exists(xf:var[@name = 'path'])">
                                    <fr:value
                                        value="{fr:var-value(xf:var[@name = 'source-path'])}"
                                        ref="{fr:var-value(xf:var[@name = 'path'])}"/>
                                </xsl:when>
                            </xsl:choose>
                        </xsl:for-each>
                        <xsl:for-each select="
                            $request-act//xf:action[
                                tokenize(@class, '\s+') = 'fr-set-database-service-value-action'
                            ]">
                            <xsl:variable name="ctl"
                                select="fr:xpath-string-value(fr:var-value(xf:var[@name = 'control-name']))"/>
                            <xsl:variable name="idx"
                                select="fr:xpath-string-value(fr:var-value(xf:var[@name = 'parameter']))"/>
                            <fr:sql-param index="{$idx}" control="{$ctl}"/>
                        </xsl:for-each>
                    </fr:service-call>
                </xsl:if>

                <xsl:for-each select="
                    $response-act//xf:action[
                        tokenize(@class, '\s+') = 'fr-set-control-value-action'
                    ]">
                    <xsl:variable name="ctl"
                        select="fr:xpath-string-value(fr:var-value(xf:var[@name = 'control-name']))"/>
                    <xsl:variable name="val" select="fr:var-value(xf:var[@name = 'control-value'])"/>
                    <fr:control-setvalue control="{$ctl}" value="{$val}"/>
                </xsl:for-each>

                <xsl:for-each select="
                    $response-act//xf:action[
                        tokenize(@class, '\s+') = 'fr-itemset-action'
                    ]">
                    <xsl:variable name="ctl"
                        select="fr:xpath-string-value(fr:var-value(xf:var[@name = 'control-name']))"/>
                    <xsl:variable name="items" select="fr:var-value(xf:var[@name = 'response-items'])"/>
                    <!-- `test/resources/org/orbeon/oxf/fb/template.xml` has case where the `xf:var` for the item label
                         and value are inside a nested `xf:action`. 2025.1 doesn't produce that additional nesting,
                         but it's possible that an earlier version did. -->
                    <xsl:variable name="label" select="fr:var-value(.//xf:var[@name = 'item-label'])"/>
                    <xsl:variable name="value" select="fr:var-value(.//xf:var[@name = 'item-value'])"/>
                    <xsl:variable name="hint" select="if (exists(.//xf:var[@name = 'item-hint'])) then fr:var-value(.//xf:var[@name = 'item-hint']) else ''"/>
                    <fr:control-setitems control="{$ctl}" items="{$items}" label="{$label}" value="{$value}">
                        <xsl:if test="string-length($hint) gt 0">
                            <xsl:attribute name="hint" select="$hint"/>
                        </xsl:if>
                    </fr:control-setitems>
                </xsl:for-each>

                <xsl:for-each select="
                    $response-act//xf:action[
                        tokenize(@class, '\s+') = 'fr-save-to-dataset-action'
                    ]">
                    <xsl:variable name="ds"
                        select="fr:xpath-string-value(fr:var-value(xf:var[@name = 'dataset-name']))"/>
                    <fr:dataset-write name="{$ds}"/>
                </xsl:for-each>
            </xsl:variable>
            <xsl:choose>
                <xsl:when test="exists($data-condition)">
                    <fr:if condition="{$data-condition}">
                        <xsl:copy-of select="$action-body/node()"/>
                    </fr:if>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:copy-of select="$action-body/node()"/>
                </xsl:otherwise>
            </xsl:choose>
        </fr:action>
    </xsl:template>

    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
