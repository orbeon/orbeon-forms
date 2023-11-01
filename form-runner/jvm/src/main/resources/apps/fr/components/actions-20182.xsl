<!--
  Copyright (C) 2013 Orbeon, Inc.

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
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:import href="oxf:/apps/fr/components/actions-common.xsl"/>

    <xsl:variable name="continuation-event-prefix">fr-action-continuation-</xsl:variable>

    <xsl:function name="fr:build-iterate-att" as="attribute(iterate)">
        <xsl:param name="model-id"        as="xs:string"/>
        <xsl:param name="to-control-name" as="xs:string"/>
        <xsl:param name="at"              as="xs:string?"/>
        <xsl:param name="library-name"    as="xs:string?"/>

        <xsl:choose>
            <xsl:when test="exists($at)">
                <xsl:attribute
                    name="iterate"
                    select="
                        concat(
                            'frf:resolveTargetRelativeToActionSourceFromBinds(''',
                            $model-id,
                            ''', ''',
                            $to-control-name,
                            ''')[',
                            if ($at = 'end') then
                                'last()'
                            else if ($at = 'start') then
                                '1'
                            else if ($at castable as xs:integer) then
                                xs:integer($at)
                            else if ($at = 'all') then
                                'true()'
                            else
                                error(),
                            ']'
                        )"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:attribute
                    name="iterate"
                    select="
                        concat(
                            'frf:resolveTargetRelativeToActionSource(xxf:get-document-attribute($current-action-id, ''action-source''), ''',
                            $to-control-name,
                            ''', true(),',
                            if (exists($library-name)) then concat('''', $library-name, '''') else '()',
                            ')'
                        )"/>
            </xsl:otherwise>
        </xsl:choose>

    </xsl:function>

    <xsl:function name="fr:build-context-att" as="attribute()?">
        <xsl:param name="elem"    as="element()"/>
        <xsl:param name="context" as="xs:string?"/>

         <xsl:if test="$context = 'current-iteration'">
             <xsl:attribute name="context">xxf:get-document-attribute($current-action-id, '<xsl:value-of select="fr:build-context-param($elem/..)"/>')</xsl:attribute>
         </xsl:if>
    </xsl:function>

    <xsl:function name="fr:build-context-param" as="xs:string">
        <xsl:param name="elem" as="element()"/>
        <xsl:value-of select="concat('iteration-context-', count($elem/ancestor-or-self::fr:data-iterate))"/>
    </xsl:function>

    <xsl:function name="fr:build-is-last-iteration-param" as="xs:string">
        <xsl:param name="elem" as="element()"/>
        <xsl:value-of select="concat('action-continuation-is-last-iteration-', count($elem/ancestor-or-self::fr:data-iterate))"/>
    </xsl:function>

    <xsl:function name="fr:continuation-id" as="xs:string">
        <xsl:param name="action-name"           as="xs:string"/>
        <xsl:param name="continuation-position" as="xs:integer"/>

        <xsl:value-of select="concat($action-name, '-', $continuation-position,     '-id')"/>
    </xsl:function>

    <xsl:function name="fr:following-continuation-id" as="xs:string">
        <xsl:param name="block-elem"            as="element()"/>
        <xsl:param name="action-name"           as="xs:string"/>
        <xsl:param name="continuation-position" as="xs:integer"/>

        <xsl:value-of
            select="
                fr:continuation-id(
                    $action-name,
                    $continuation-position + 1 + count($block-elem//(fr:service-call | fr:data-iterate | fr:if | fr:copy-content-check))
                )"/>
    </xsl:function>

    <!-- Match and modify `fr:listener`s -->
    <xsl:template
        xmlns:xbl="http://www.w3.org/ns/xbl"
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $candidate-action-models-ids
                ]/fr:listener[
                    @version = '2018.2'
                ]
            |
            /xh:html/xh:head//
                xbl:handlers/fr:listener[
                    @version = '2018.2'
                ]">

        <!-- This must be in scope in the case of `<xbl:handlers>` content -->
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

        <xsl:variable name="is-handler" select="exists(parent::xbl:handlers)" as="xs:boolean"/>
        <xsl:variable name="model-id"   select="(if ($is-handler) then ../../xbl:implementation/xf:model else ..)/@id/string()" as="xs:string"/>

        <xsl:variable name="modes"          select="p:split(@modes)"/>
        <xsl:variable name="events"         select="p:split(@events)"/>
        <xsl:variable name="control-names"  select="p:split(@controls)"/>
        <xsl:variable name="actions"        select="p:split(@actions)"/>

        <xsl:variable name="model-events"   select="$events[. = $form-load-2018.2-action-names]"/>
        <xsl:variable name="control-events" select="$events[. = $controls-2018.2-action-names]"/>

        <xsl:if test="exists($model-events)">
            <xf:action
                target="{$model-id}"
                event="{
                    for $e in $model-events
                    return $form-load-fr-action-names[index-of($form-load-2018.2-action-names, $e)]
                }">

                <xsl:if test="exists($modes)">
                    <xsl:attribute name="if">fr:mode() = (<xsl:value-of
                        select="string-join(for $m in $modes return concat('''', $m, ''''), ',')"/>)</xsl:attribute>
                </xsl:if>

                <xsl:for-each select="$actions">
                    <xf:dispatch name="fr-call-user-{.}-action" targetid="{$model-id}">
                        <xf:property name="fr-action-id"  value="concat('fr-action-', secure:randomHexId())" xxf:tunnel="true" xmlns:secure="java:org.orbeon.oxf.util.SecureUtils"/>
                        <xf:property name="action-source" value="event('xxf:absolute-targetid')"/>
                    </xf:dispatch>
                </xsl:for-each>

            </xf:action>
        </xsl:if>

        <xsl:if test="exists($control-events)">

            <xsl:variable
                xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
                name="is-outer"
                select="@xxbl:scope = 'outer'"/>

            <xsl:element name="{if ($is-handler) then 'xbl:handler' else 'xf:action'}">
                <xsl:attribute
                    name="observer"
                    select="
                        if ($is-outer) then
                            'fr-view-component'
                        else
                            string-join(
                                for $c in $control-names
                                    return concat($c, '-control'),
                                ' '
                            )"/>
                <xsl:if test="not($is-outer)">
                    <xsl:attribute name="target">#observer</xsl:attribute>
                </xsl:if>
                <xsl:attribute
                    name="event"
                    select="
                        string-join(
                            for $e in $control-events
                                return $controls-xforms-action-names[
                                    index-of($controls-2018.2-action-names, $e)
                                ],
                            ' '
                        )"/>

                <xsl:if test="$is-outer">
                    <xsl:copy-of select="@xxbl:scope" xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"/>
                    <xsl:attribute name="xxf:phantom">true</xsl:attribute>
                    <xsl:attribute name="if" select="
                        concat(
                            'frf:controlMatchesNameAndLibrary(event(''xxf:absolute-targetid''), (',
                            string-join(
                                for $o in $control-names
                                    return concat('''', $o, ''''),
                                ','
                            ),
                            '), ''',
                            $library-name,
                            ''')'
                        )"/>
                </xsl:if>

                <xsl:if test="exists($modes)">
                    <xsl:attribute name="if">fr:mode() = (<xsl:value-of
                        select="string-join(for $m in $modes return concat('''', $m, ''''), ',')"/>)</xsl:attribute>
                </xsl:if>

                <xsl:for-each select="$actions">
                    <xf:dispatch name="fr-call-user-{.}-action" targetid="{$model-id}">
                        <xsl:if test="$is-outer">
                            <xsl:attribute name="xxbl:scope" xmlns:xxbl="http://orbeon.org/oxf/xml/xbl">inner</xsl:attribute>
                        </xsl:if>
                        <xf:property name="fr-action-id"  value="concat('fr-action-', secure:randomHexId())" xxf:tunnel="true" xmlns:secure="java:org.orbeon.oxf.util.SecureUtils"/>
                        <xf:property name="action-source" value="event('xxf:absolute-targetid')"/>
                    </xf:dispatch>
                </xsl:for-each>

            </xsl:element>
        </xsl:if>

    </xsl:template>

    <!-- Mark the end of `fr:data-iterate` content -->
    <xsl:template match="fr:data-iterate" mode="within-action-2018.2-marking">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" mode="#current"/>
            <!-- We append this so we can perform grouping -->
            <fr:data-iterate-end-marker/>
        </xsl:copy>
    </xsl:template>

    <!-- Mark the end of `fr:if` content -->
    <xsl:template match="fr:if" mode="within-action-2018.2-marking">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" mode="#current"/>
            <!-- We append this so we can perform grouping -->
            <fr:if-end-marker/>
        </xsl:copy>
    </xsl:template>

    <!-- Prepend `<fr:copy-content-check>` if needed -->
    <xsl:template match="fr:copy-content[@warn = 'true']" mode="within-action-2018.2-marking">
        <fr:copy-content-check>
            <xsl:apply-templates select="(@* except @warn) | node()" mode="#current"/>
        </fr:copy-content-check>
        <xsl:copy>
            <xsl:apply-templates select="(@* except @warn) | node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <!-- Copy everything else -->
    <xsl:template match="fr:*" mode="within-action-2018.2-marking">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <!-- Trigger continuation if needed -->
    <xsl:template match="fr:data-iterate-end-marker" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="action-name"           as="xs:string"/>
        <xsl:param tunnel="yes" name="continuation-position" as="xs:integer"/>

        <xf:dispatch
            if="xxf:get-document-attribute($current-action-id, '{fr:build-is-last-iteration-param(.)}') = true()"
            name="{$continuation-event-prefix}{fr:continuation-id($action-name, $continuation-position + 1)}"
            targetid="fr-form-model">
            <xf:property name="fr-action-id" value="$current-action-id" xxf:tunnel="true"/>
        </xf:dispatch>

    </xsl:template>

    <!-- Trigger continuation -->
    <xsl:template match="fr:if-end-marker" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="action-name"           as="xs:string"/>
        <xsl:param tunnel="yes" name="continuation-position" as="xs:integer"/>

        <xf:dispatch
            name="{$continuation-event-prefix}{fr:continuation-id($action-name, $continuation-position + 1)}"
            targetid="fr-form-model">
            <xf:property name="fr-action-id" value="$current-action-id" xxf:tunnel="true"/>
        </xf:dispatch>

    </xsl:template>

    <!-- Match and modify `fr:action`s -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $candidate-action-models-ids
                ]/fr:action[
                    @version = '2018.2'
                ]">

        <xsl:variable name="model"       select=".." as="element(xf:model)"/>
        <xsl:variable name="view-elem"   select="if (exists($model/parent::xbl:implementation)) then $model/../../xbl:template else /xh:html/xh:body" as="element()" xmlns:xbl="http://www.w3.org/ns/xbl"/>
        <xsl:variable name="model-id"    select="$model/@id/string()" as="xs:string"/>
        <xsl:variable name="action-name" select="@name/string()"  as="xs:string"/>

        <!-- Create a document with a root element so that `preceding::` works. Also include attributes so actions can
             search for settings on the ancestor `fr:action` -->
        <xsl:variable name="content-with-markers" as="element(fr:action)">
            <fr:action>
                <xsl:copy-of select="@*"/>
                <xsl:apply-templates select="fr:*" mode="within-action-2018.2-marking"/>
            </fr:action>
        </xsl:variable>

        <xf:action id="{@name}-binding" event="fr-call-user-{@name}-action" target="{$model-id}">

            <!-- TODO: Consider handling `iterate-control-name` per https://github.com/orbeon/orbeon-forms/issues/1833 -->

            <xf:var
                name="current-action-id"
                value="event('fr-action-id')"/>

            <xxf:log
                class="fr-action-impl"
                name="orbeon.action"
                level="info"
                value="'Starting new top-level action'">
                <xf:property name="action-name"   value="'{@name}'"/>
                <xf:property name="fr-action-id"  value="$current-action-id"/>
                <xf:property name="action-source" value="event('action-source')"/>
            </xxf:log>

            <!-- Initialize action state -->
            <xf:action type="xpath">
                xxf:set-document-attribute($current-action-id, 'action-source', event('action-source'))
            </xf:action>

            <!-- TODO: clean action state in case of success AND error (but will need action id!) -->

            <xsl:for-each-group
                select="$content-with-markers//fr:*[not(parent::fr:service-call | parent::fr:copy-content-check | parent::fr:copy-content)]"
                group-ending-with="fr:service-call | fr:data-iterate-end-marker | fr:if-end-marker | fr:copy-content-check">

                <xsl:variable name="group-position"   select="position()"/>

                <!-- This handles only the execution of the initial actions up to before the first continuation -->
                <xsl:if test="$group-position = 1">

                    <xsl:variable name="current-group"         select="current-group()"/>
                    <xsl:variable name="nested-grouping-elems" select="$current-group[self::fr:data-iterate | self::fr:if]" as="element()*"/>

                    <!-- NOTE: To handle more nesting, this might have to be updated. -->

                    <!-- Going into a nested construct boundary. The content of the nested construct is handled separately so remove it. :) -->
                    <xsl:variable
                        name="group-content"
                        select="$current-group except $nested-grouping-elems/fr:*"/>

                    <xsl:apply-templates select="$group-content" mode="within-action-2018.2">
                        <xsl:with-param tunnel="yes" name="view-elem"             select="$view-elem"/>
                        <xsl:with-param tunnel="yes" name="model-id"              select="$model-id"/>
                        <xsl:with-param tunnel="yes" name="action-name"           select="$action-name"/>
                        <xsl:with-param tunnel="yes" name="continuation-position" select="$group-position"/>
                    </xsl:apply-templates>
                </xsl:if>

            </xsl:for-each-group>

        </xf:action>

        <!-- Place continuations at the top-level, see https://github.com/orbeon/orbeon-forms/issues/4068 -->
        <xsl:for-each-group
            select="$content-with-markers//fr:*[not(parent::fr:service-call | parent::fr:copy-content-check | parent::fr:copy-content)]"
            group-ending-with="fr:service-call | fr:data-iterate-end-marker | fr:if-end-marker | fr:copy-content-check">

            <xsl:variable name="group-position"   select="position()"/>

            <xsl:if test="$group-position gt 1">

                <xsl:variable name="current-group"         select="current-group()"/>
                <xsl:variable name="nested-grouping-elems" select="$current-group[self::fr:data-iterate | self::fr:if]" as="element(*)*"/>

                <!-- NOTE: To handle more nesting, this might have to be updated. -->

                <!-- Going into a nested construct boundary. The content of the nested construct is handled separately so remove it. :) -->
                <xsl:variable
                    name="group-content"
                    select="$current-group except $nested-grouping-elems/fr:*"/>

                <!-- Use `preceding::` so that `fr:service-call` nested in preceding iterations are found too -->
                <xsl:variable
                    name="preceding-delimiter"
                    select="$current-group[1]/preceding::fr:*[local-name() = ('service-call', 'data-iterate-end-marker', 'if-end-marker', 'copy-content-check')][1]"/>

                <xsl:variable
                    name="preceding-service-name-opt"
                    select="$preceding-delimiter/self::fr:service-call/@service/string()" as="xs:string?"/>
                <xsl:variable
                    name="current-continuation-id"
                    select="concat($action-name, '-', $group-position, '-id')"/>

                <xsl:choose>
                    <xsl:when test="exists($preceding-service-name-opt)">
                        <!-- This means that the last group/continuation block ended with a service call. The continuation reacts
                             to the service completing successfully, and evaluates in the context of the service response. -->
                        <xf:action
                            observer="{$preceding-service-name-opt}-submission"
                            event="xforms-submit-done"
                            context="xxf:instance('fr-service-response-instance')"
                            if="xxf:get-document-attribute(event('fr-action-id'), 'action-continuation-id') = '{$current-continuation-id}'">

                            <xf:var
                                name="current-action-id"
                                value="event('fr-action-id')"/>

                            <xsl:apply-templates select="$group-content" mode="within-action-2018.2">
                                <xsl:with-param tunnel="yes" name="view-elem"             select="$view-elem"/>
                                <xsl:with-param tunnel="yes" name="model-id"              select="$model-id"/>
                                <xsl:with-param tunnel="yes" name="action-name"           select="$action-name"/>
                                <xsl:with-param tunnel="yes" name="continuation-position" select="$group-position"/>
                            </xsl:apply-templates>

                        </xf:action>
                    </xsl:when>
                    <xsl:otherwise>
                        <!-- This means the last group/continuation block ended with the end of an `<fr:data-iterate>` or an
                             `<fr:if>` block. The continuation reacts to an explicit event dispatch and evaluates in the context
                             of the preceding service call or enclosing iteration, whichever comes first. -->
                        <xf:action
                            observer="fr-form-model"
                            event="{$continuation-event-prefix}{$current-continuation-id}">

                            <xsl:variable
                                name="context-changing-elem-opt"
                                select="
                                    reverse(
                                        $current-group[1]/ancestor-or-self::fr:*/(self::fr:*, preceding-sibling::fr:*)
                                    )[
                                        local-name() = 'service-call' or
                                        local-name() = 'data-iterate' and exists(. intersect $current-group[1]/ancestor::fr:*)
                                    ][1]"/>

                            <xsl:attribute
                                name="context"
                                select="
                                    if (exists($context-changing-elem-opt/self::fr:service-call)) then
                                        'xxf:instance(''fr-service-response-instance'')'
                                    else if (exists($context-changing-elem-opt/self::fr:data-iterate)) then
                                        concat('xxf:get-document-attribute(event(''action-id''), ''', fr:build-context-param($context-changing-elem-opt), ''')')
                                    else
                                        'instance(''fr-form-instance'')'
                                "/>

                            <xf:var
                                name="current-action-id"
                                value="event('fr-action-id')"/>

                            <xsl:apply-templates select="$group-content" mode="within-action-2018.2">
                                <xsl:with-param tunnel="yes" name="view-elem"             select="$view-elem"/>
                                <xsl:with-param tunnel="yes" name="model-id"              select="$model-id"/>
                                <xsl:with-param tunnel="yes" name="action-name"           select="$action-name"/>
                                <xsl:with-param tunnel="yes" name="continuation-position" select="$group-position"/>
                            </xsl:apply-templates>

                        </xf:action>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:if>

        </xsl:for-each-group>

    </xsl:template>

    <xsl:template match="fr:service-call" mode="within-action-2018.2">
        <xsl:param tunnel="yes" name="action-name"           as="xs:string"/>
        <xsl:param tunnel="yes" name="continuation-position" as="xs:integer"/>
        <xsl:param tunnel="yes" name="library-name"          as="xs:string?"/>

        <xsl:variable name="service-name" select="@service/string()"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
            <xf:property name="service-name" value="'{$service-name}'"/>
        </xxf:log>

        <xf:insert
            ref="xxf:instance('fr-service-request-instance')"
            origin="xf:parse(xxf:instance('{$service-name}-instance'))"/>

        <xsl:variable name="request-actions" select="fr:value | fr:url-param | fr:sql-param"/>

        <xsl:if test="exists($request-actions)">

            <xsl:if test="exists($request-actions/self::fr:*/@value)">
                <xf:var
                    name="original-context"
                    value="."/>
            </xsl:if>

            <xf:action context="xxf:instance('fr-service-request-instance')" class="fr-action-impl">
                <xsl:for-each select="$request-actions">
                    <xf:action>
                        <xsl:choose>
                            <xsl:when test="exists(@control)">
                                <xsl:variable name="control" select="@control/string()" as="xs:string"/>
                                <xf:var
                                    name="value"
                                    value="frf:resolveTargetRelativeToActionSource(xxf:get-document-attribute($current-action-id, 'action-source'), '{$control}', true(), {if (exists($library-name)) then concat('''', $library-name, '''') else '()'})"/>
                            </xsl:when>
                            <xsl:when test="exists(@value)">
                                <xsl:variable name="value" select="@value/string()" as="xs:string"/>
                                <xf:var
                                    name="value"
                                    context="$original-context"
                                    value="{frf:replaceVarReferencesWithFunctionCalls(. , $value, false(), $library-name, ())}">
                                    <xsl:copy-of select="fr:build-context-att(., @expression-context)"/>
                                </xf:var>
                            </xsl:when>
                        </xsl:choose>
                        <xsl:choose>
                            <xsl:when test="./self::fr:value">
                                <xsl:variable name="ref" select="@ref/string()" as="xs:string"/>
                                <xf:setvalue
                                    ref="{frf:replaceVarReferencesWithFunctionCalls(. , $ref, false(), $library-name, ())}"
                                    value="$value"/>
                            </xsl:when>
                            <xsl:when test="./self::fr:sql-param">
                                <xsl:variable name="index" select="xs:integer(@index)" as="xs:integer"/>
                                <xf:setvalue
                                    xmlns:sql="http://orbeon.org/oxf/xml/sql"
                                    ref="/sql:config/sql:query/sql:param[{$index}]/(@value | @select)[1]"
                                    value="
                                        concat(
                                            '''',
                                            replace(
                                                string(
                                                    $value
                                                ),
                                                '''',
                                                ''''''
                                            ),
                                            ''''
                                        )"/>
                            </xsl:when>
                            <xsl:when test="./self::fr:url-param">
                                <xf:var name="name"><xsl:value-of select="@name"/></xf:var>
                                <xf:setvalue
                                    ref="(//*[@name = $name], //*[local-name() = $name])[1]"
                                    value="$value"/>
                            </xsl:when>
                        </xsl:choose>
                    </xf:action>
                </xsl:for-each>
            </xf:action>
        </xsl:if>

        <xf:action type="xpath">
            xxf:set-document-attribute($current-action-id, 'action-continuation-id', '<xsl:value-of select="fr:continuation-id($action-name, $continuation-position + 1)"/>')
        </xf:action>

        <xf:insert ref="xxf:instance('fr-service-response-instance')" origin="xf:element('response')"/>
        <xf:send submission="{$service-name}-submission">
            <xf:property name="fr-action-id" value="$current-action-id" xxf:tunnel="true"/>
            <xsl:variable
                name="async"
                as="xs:boolean"
                select="
                    ancestor::fr:action/@async = 'true' or (
                        not(ancestor::fr:action/@async = 'false') and
                        $actions-async
                    )"/>
            <xf:property name="fr-async" value="'{$async}'"/>
            <xsl:variable
                name="response-must-await"
                as="xs:boolean"
                select="
                    ancestor::fr:action/@response-must-await = 'true' or (
                        not(ancestor::fr:action/@response-must-await = 'false') and
                        $actions-response-must-await
                    )"/>
            <xf:property name="fr-response-must-await" value="'{$response-must-await}'"/>
        </xf:send>

    </xsl:template>

    <xsl:template match="fr:data-iterate" mode="within-action-2018.2">
        <xsl:param tunnel="yes" name="action-name"           as="xs:string"/>
        <xsl:param tunnel="yes" name="continuation-position" as="xs:integer"/>
        <xsl:param tunnel="yes" name="library-name"          as="xs:string?"/>

        <xsl:variable name="data-iterate-elem" select="."/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:action if="empty(({@ref}))">
            <xf:dispatch
                name="{$continuation-event-prefix}{fr:following-continuation-id(., $action-name, $continuation-position)}"
                targetid="fr-form-model">
                <xf:property name="fr-action-id" value="$current-action-id" xxf:tunnel="true"/>
            </xf:dispatch>
        </xf:action>
        <xf:action iterate="{frf:replaceVarReferencesWithFunctionCalls(. , @ref, false(), $library-name, ())}">
            <xsl:copy-of select="fr:build-context-att($data-iterate-elem, @expression-context)"/>

            <!-- Apply only up to the first delimiter for https://github.com/orbeon/orbeon-forms/issues/4067 -->
            <xsl:for-each-group
                select="fr:*"
                group-ending-with="fr:service-call | fr:data-iterate-end-marker | fr:if | fr:data-iterate | fr:copy-content-check">

                <xsl:variable name="group-position" select="position()"/>
                <xsl:if test="$group-position = 1">
                    <!-- 2023-10-16: In case of error, we now remove all document attributes for the action. -->
                    <xf:action if="exists(xxf:get-document-attribute($current-action-id, 'action-source'))">
                        <xf:action type="xpath">
                            xxf:set-document-attribute($current-action-id, '<xsl:value-of select="fr:build-is-last-iteration-param($data-iterate-elem)"/>', position() = last()),
                            xxf:set-document-attribute($current-action-id, '<xsl:value-of select="fr:build-context-param($data-iterate-elem)"/>', .)
                        </xf:action>
                        <xsl:apply-templates select="current-group()" mode="within-action-2018.2"/>
                    </xf:action>
                </xsl:if>

            </xsl:for-each-group>

        </xf:action>

    </xsl:template>

    <xsl:template match="fr:if" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="action-name"           as="xs:string"/>
        <xsl:param tunnel="yes" name="continuation-position" as="xs:integer"/>
        <xsl:param tunnel="yes" name="library-name"          as="xs:string?"/>

        <xsl:variable name="if-position-within-block" select="count(preceding-sibling::fr:if)"/>
        <xsl:variable name="var-name"                 select="concat('cond', $if-position-within-block + 1)"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:var name="{$var-name}" value="{frf:replaceVarReferencesWithFunctionCalls(. , @condition, false(), $library-name, ())}">
            <xsl:copy-of select="fr:build-context-att(., @expression-context)"/>
        </xf:var>
        <xf:action if="${$var-name}">
            <xsl:for-each-group
                select="fr:*"
                group-ending-with="fr:service-call | fr:if-end-marker | fr:if | fr:data-iterate | fr:copy-content-check">

                <xsl:variable name="group-position" select="position()"/>
                <xsl:if test="$group-position = 1">
                    <xsl:apply-templates select="current-group()" mode="within-action-2018.2"/>
                </xsl:if>
            </xsl:for-each-group>
        </xf:action>
        <xf:action if="not(${$var-name})">
            <xf:dispatch
                name="{$continuation-event-prefix}{fr:following-continuation-id(., $action-name, $continuation-position)}"
                targetid="fr-form-model">
                <xf:property name="fr-action-id" value="$current-action-id" xxf:tunnel="true"/>
            </xf:dispatch>
        </xf:action>
    </xsl:template>

    <xsl:template match="fr:repeat-clear" mode="within-action-2018.2">

        <xsl:variable name="repeat-name" select="@repeat/string()" as="xs:string"/>
        <xsl:variable name="at"          select="@at/string()"     as="xs:string?"/>

         <xxf:log
             class="fr-action-impl"
             name="orbeon.action"
             level="info"
             value="'Starting action'">
             <xf:property name="fr-action-id" value="$current-action-id"/>
             <xf:property name="action-type"  value="'{name(.)}'"/>
             <xf:property name="repeat-name"  value="'{$repeat-name}'"/>
         </xxf:log>

        <xf:action type="xpath" class="fr-action-impl">
            frf:repeatClear('<xsl:value-of select="frf:findContainerDetailsCompileTime($fr-form-model, $repeat-name, $at, true())"/>')
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:repeat-add-iteration" mode="within-action-2018.2">

        <xsl:variable name="repeat-name" select="@repeat/string()" as="xs:string"/>
        <xsl:variable name="at"          select="@at/string()"     as="xs:string?"/>

        <!-- TODO -->
        <xsl:variable name="apply-defaults" select="true()"/>

        <!-- NOTE: We might like to support `after-current | before-current`, for for that we need the `index()` function which
             needs a repeat id, and we don't have it right now. -->

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="{name(.)}"/>
            <xf:property name="repeat-name"  value="'{$repeat-name}'"/>
        </xxf:log>

        <xf:action type="xpath" class="fr-action-impl">
            frf:repeatAddIteration('<xsl:value-of select="frf:findContainerDetailsCompileTime($fr-form-model, $repeat-name, $at, false())"/>', <xsl:value-of select="$apply-defaults"/>())
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:repeat-remove-iteration" mode="within-action-2018.2">

        <xsl:variable name="repeat-name" select="@repeat/string()" as="xs:string"/>
        <xsl:variable name="at"          select="@at/string()"     as="xs:string?"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="{name(.)}"/>
            <xf:property name="repeat-name"  value="'{$repeat-name}'"/>
        </xxf:log>

        <xf:action type="xpath" class="fr-action-impl">
            frf:repeatRemoveIteration('<xsl:value-of select="frf:findContainerDetailsCompileTime($fr-form-model, $repeat-name, $at, false())"/>')
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:control-setvalue" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id"     as="xs:string"/>
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="value-expr"      select="@value/string()"   as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:action class="fr-action-impl">
            <xf:var name="value" value="{frf:replaceVarReferencesWithFunctionCalls(. , $value-expr, false(), $library-name, ())}"/>

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:action>
                <xsl:copy-of select="fr:build-iterate-att($model-id, $to-control-name, $at, $library-name)"/>
                <xf:setvalue
                    ref="."
                    value="$value"/>
            </xf:action>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:control-clear" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id"     as="xs:string"/>
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:action class="fr-action-impl">

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:action>
                <xsl:copy-of select="fr:build-iterate-att($model-id, $to-control-name, $at, $library-name)"/>
                <!-- https://github.com/orbeon/orbeon-forms/issues/5471 -->
                <xf:delete
                    ref="_"/>
                <!-- Clear all possible elements and attributes -->
                <xf:setvalue
                    iterate="(. | image)/(.[empty(*)] | @filename | @mediatype | @size | @label)"
                    ref="."/>

            </xf:action>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:control-setvisited" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="view-elem"    as="element()"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
<!--        Q: How to handle `at`? -->
<!--        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>-->
        <xsl:variable name="visited"         select="@visited/string()" as="xs:string?"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xxf:setvisited
            class="fr-action-impl"
            control="{frf:findControlByNameUnderXPath($to-control-name, $view-elem)/@id}"
            recurse="true"
            visited="{($visited, 'true')[1]}"/>

    </xsl:template>

    <xsl:template match="fr:control-setfocus" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="view-elem"    as="element()"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:setfocus
            class="fr-action-impl"
            control="{frf:findControlByNameUnderXPath($to-control-name, $view-elem)/@id}"/>

    </xsl:template>

    <xsl:template match="fr:control-setitems" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id"     as="xs:string"/>
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

        <xsl:variable name="elem"            select="."                                  as="element()"/>

        <xsl:variable name="to-control-name" select="$elem/@control/string()"            as="xs:string"/>
        <xsl:variable name="items-expr"      select="$elem/@items/string()"              as="xs:string"/>
        <xsl:variable name="label-expr"      select="$elem/@label/string()"              as="xs:string"/>
        <xsl:variable name="hint-expr"       select="$elem/@hint/string()"               as="xs:string?"/>
        <xsl:variable name="value-expr"      select="$elem/@value/string()"              as="xs:string"/>
        <xsl:variable name="at"              select="$elem/@at/string()"                 as="xs:string?"/>
        <xsl:variable name="context"         select="$elem/@expression-context/string()" as="xs:string?"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:action class="fr-action-impl">

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:var
                xmlns:secure="java:org.orbeon.oxf.util.SecureUtils"
                name="new-itemset-id"
                value="concat('fr', secure:randomHexId())"/>

            <!-- New `<itemset>` outer element (not inserted into the instance yet) -->
            <xf:var
                name="new-itemset-holder"
                value="xf:element('itemset', xf:attribute('id', $new-itemset-id))"/>

            <xf:var
                name="items-expr-context"
                value="."/>

            <!-- Create `<choices>` elements under the new `<itemset>` outer element -->
            <xf:action iterate="xxf:instance('fr-form-resources')/resource">

                <xf:var name="fr-lang" value="@xml:lang"/>

                <!-- Re-evaluate `$response-items` at each iteration because that can depend on `$fr-lang` -->
                <xf:var
                    name="response-items"
                    context="$items-expr-context"
                    value="{frf:replaceVarReferencesWithFunctionCalls($elem , $items-expr, false(), $library-name, ())}">
                    <xsl:copy-of select="fr:build-context-att(., $context)"/>
                </xf:var>

                <xf:insert
                    context="$new-itemset-holder"
                    ref="*"
                    origin="xf:element('choices')"/>

                <xf:var
                    name="new-choices-holder"
                    value="$new-itemset-holder/choices[last()]"/>

                <!-- Should use a version of `XFormsItemUtils.evaluateItemset()`
                     See https://github.com/orbeon/orbeon-forms/issues/3125 -->
                <xf:action iterate="$response-items">
                    <xf:var name="item-label" value="{frf:replaceVarReferencesWithFunctionCalls($elem , $label-expr, false(), $library-name, ())}"/>
                    <xf:var name="item-value" value="{frf:replaceVarReferencesWithFunctionCalls($elem , $value-expr, false(), $library-name, ())}"/>
                    <xsl:choose>
                        <xsl:when test="exists($hint-expr)">
                            <xf:var name="item-hint"  value="{frf:replaceVarReferencesWithFunctionCalls($elem , $hint-expr, false(), $library-name, ())}"/>
                            <xf:insert
                                context="$new-choices-holder"
                                ref="*"
                                origin="
                                    xf:element(
                                        'item',
                                        (
                                            xf:element('label', xs:string($item-label)),
                                            xf:element('hint',  xs:string($item-hint)),
                                            xf:element('value', xs:string($item-value))
                                        )
                                    )"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xf:insert
                                context="$new-choices-holder"
                                ref="*"
                                origin="
                                    xf:element(
                                        'item',
                                        (
                                            xf:element('label', xs:string($item-label)),
                                            xf:element('value', xs:string($item-value))
                                        )
                                    )"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xf:action>
            </xf:action>

            <!-- Delegate the rest to common implementation. We should not duplicate much of the code above either, but
                 the problem is the evaluation of `response-items`, 'item-label', and 'item-value', which must take place
                 in a context where variables are available, so we cannot use `saxon:evaluate()`/`xxf:evaluate()`.

                 2021-02-02: Q: The offline `xxf:evaluate()` scopes variables. Maybe the older implementation doesn't?
            -->
            <xf:dispatch name="fr-call-itemset-action" targetid="fr-form-instance">
                <xf:property name="action-source"      value="xxf:get-document-attribute($current-action-id, 'action-source')"/>
                <xf:property name="control-name"       value="'{$to-control-name}'"/>
                <xf:property name="new-itemset-id"     value="$new-itemset-id"/>
                <xf:property name="new-itemset-holder" value="$new-itemset-holder"/>
                <xsl:if test="exists($at)">
                    <xf:property name="at"                 value="'{$at}'"/>
                </xsl:if>
            </xf:dispatch>

        </xf:action>

    </xsl:template>

    <xsl:template match="fr:dataset-write" mode="within-action-2018.2">

        <xsl:variable name="dataset-name" select="@name/string()" as="xs:string"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:action class="fr-action-impl">
            <xf:var name="value" value="."/>
            <xf:insert
                context="xxf:instance('fr-service-response-instance')"
                ref="instance('fr-dataset-{$dataset-name}')"
                origin="$value"/>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:dataset-clear" mode="within-action-2018.2">

        <xsl:variable name="dataset-name" select="@name/string()" as="xs:string"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:action class="fr-action-impl">
            <xf:insert
                ref="instance('fr-dataset-{$dataset-name}')"
                origin="xf:element('_')"/>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:process-call" mode="within-action-2018.2">

        <xsl:variable name="process-scope" select="@scope/string()" as="xs:string"/>
        <xsl:variable name="process-name"  select="@name/string()"  as="xs:string"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:action type="xpath" class="fr-action-impl">
            fr:run-process-by-name('<xsl:value-of select="$process-scope"/>', '<xsl:value-of select="$process-name"/>')
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:navigate" mode="within-action-2018.2">

        <xsl:variable name="location" select="@location/string()" as="xs:string"/>
        <xsl:variable name="target"   select="@target/string()"   as="xs:string?"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <!-- Prefer `<xf:load>` to `fr:run-process(…, 'navigate(…))`, as the latter builds a process (string) with
             XPath, which requires escaping, and is thus more error prone -->
        <xf:load resource="{$location}" class="fr-action-impl">
            <xsl:if test="exists($target)">
                <xsl:attribute name="xxf:target" select="$target"/>
            </xsl:if>
        </xf:load>

    </xsl:template>

    <xsl:template match="fr:control-setattachment" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id"     as="xs:string"/>
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:action class="fr-action-impl">
            <xf:var name="value"     value="xxf:instance('fr-service-response-instance')/string()"/>
            <xf:var name="mediatype" value="uri-param-values($value, 'mediatype')[1]"/>
            <xf:var name="size"      value="uri-param-values($value, 'size')[1]"/>

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:action>
                <xsl:copy-of select="fr:build-iterate-att($model-id, $to-control-name, $at, $library-name)"/>
                <xf:setvalue
                    ref="image, ."
                    value="$value"/>
                <xf:setvalue
                    ref="image/@mediatype, ./@mediatype"
                    value="$mediatype"/>
                <xf:setvalue
                    ref="image/@size, ./@size"
                    value="$size"/>
            </xf:action>

        </xf:action>

    </xsl:template>

    <xsl:template
            match="
                fr:control-setfilename  |
                fr:control-setmediatype |
                fr:control-setsize"
            mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id"     as="xs:string"/>
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="value-expr"      select="@value/string()"   as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:action class="fr-action-impl">
            <xf:var name="value" value="{frf:replaceVarReferencesWithFunctionCalls(. , $value-expr, false(), $library-name, ())}"/>

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:action>
                <xsl:copy-of select="fr:build-iterate-att($model-id, $to-control-name, $at, $library-name)"/>
                <xsl:variable
                    name="ref"
                    as="xs:string"
                    select="
                             if (local-name() = 'control-setfilename' ) then '@filename'
                        else if (local-name() = 'control-setmediatype') then 'image/@mediatype, @mediatype'
                        else if (local-name() = 'control-setsize'     ) then '@size'
                        else error()
                    "/>
                <xf:setvalue
                    ref="{$ref}"
                    value="$value"/>
            </xf:action>

        </xf:action>

    </xsl:template>

    <xsl:template match="fr:alert" mode="within-action-2018.2">
        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>
        <xf:action class="fr-action-impl">
            <xf:var name="message"><xsl:value-of select="@message"/></xf:var>
            <xf:message value="xxf:evaluate-avt($message)"/>
        </xf:action>
    </xsl:template>

    <xsl:template match="fr:copy-content-check" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id"              as="xs:string"/>
        <xsl:param tunnel="yes" name="action-name"           as="xs:string"/>
        <xsl:param tunnel="yes" name="continuation-position" as="xs:integer"/>

        <xsl:param tunnel="yes" name="view-elem"             as="element()"/>

        <xsl:variable name="left-names"  select="fr:map/@left/string()"  as="xs:string*"/>
        <xsl:variable name="right-names" select="fr:map/@right/string()" as="xs:string*"/>

        <xsl:variable
            name="left-repeat-names"
            select="
                for $left-name in $left-names
                return
                    (
                        frf:findAncestorRepeatNames(
                            frf:findControlByNameUnderXPath($left-name, $view-elem),
                            false()
                        )[1],
                        '0' (: special marker value :)
                    )[1]"
            as="xs:string*"/>

        <xsl:variable
            name="right-repeat-names"
            select="
                for $right-name in $right-names
                return
                    (
                        frf:findAncestorRepeatNames(
                            frf:findControlByNameUnderXPath($right-name, $view-elem),
                            false()
                        )[1],
                        '0' (: special marker value :)
                    )[1]"
            as="xs:string*"/>

        <xsl:if test="
            not(
                every $i in count($left-repeat-names)
                satisfies
                    not($left-repeat-names[$i] != '0' and $right-repeat-names[$i] = '0')
            )">
            <xsl:message terminate="yes">
                <xsl:text>fr:copy-content: no fr:map can copy from a repeated control to a non-repeatd control</xsl:text>
            </xsl:message>
        </xsl:if>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <xf:var class="fr-action-impl" name="must-warn" value="
            (: TODO: It's not good that we have to replicate the copy logic here. Can we find a better solution? :)
            let $left-names         := ({concat('''', string-join($left-names,         ''','''), '''')}),
                $right-names        := ({concat('''', string-join($right-names,        ''','''), '''')}),
                $left-repeat-names  := ({concat('''', string-join($left-repeat-names,  ''','''), '''')}),
                $right-repeat-names := ({concat('''', string-join($right-repeat-names, ''','''), '''')})
            return
                some $i in 1 to count($left-names)
                satisfies
                    let $left-container         := if ($left-repeat-names[$i]  != '0') then bind(frf:bindId($left-repeat-names[$i]))  else (),
                        $right-container        := if ($right-repeat-names[$i] != '0') then bind(frf:bindId($right-repeat-names[$i])) else (),
                        $diff                   := count($right-container/*) - count($left-container/*),
                        $must-remove-iterations := exists($left-container) and exists($right-container) and ($diff gt 0)
                    return
                        $must-remove-iterations or (
                            if (exists($left-container) and exists($right-container)) then
                                some $p in 1 to min((count($left-container/*), count($right-container/*)))
                                satisfies
                                    let $left-value  := string(($left-container/*[$p]//*[name() = $left-names[$i]])[1]),
                                        $right-value := string(($right-container/*[$p]//*[name() = $right-names[$i]])[1])
                                    return
                                        xxf:non-blank($right-value) and $left-value != $right-value
                            else if (empty($left-container) and empty($right-container)) then
                                let $left-value  := fr:control-string-value(
                                        $left-names[$i],
                                         false(),
                                         () (: TODO: support this :)
                                    ),
                                    $right-value := fr:control-string-value(
                                        $right-names[$i],
                                         false(),
                                         () (: TODO: support this :)
                                    )
                                return
                                    xxf:non-blank($right-value) and $left-value != $right-value
                            else if (exists($right-container)) then
                                (: TODO: handle `@right-at` values :)
                                some $p in 1 to count($right-container/*)
                                satisfies
                                    let $left-value  := fr:control-string-value(
                                            $left-names[$i],
                                             false(),
                                             () (: TODO: support this :)
                                        ),
                                        $right-value := string(($right-container/*[$p]//*[name() = $right-names[$i]])[1])
                                    return
                                        xxf:non-blank($right-value) and $left-value != $right-value
                            else
                                (: TODO: not supported yet :)
                                error()
                        )"/>

        <xxf:log
            class="fr-action-impl"
            if="$must-warn"
            name="orbeon.action"
            level="info"
            value="'Pausing action to show warning message'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="{name(.)}"/>
        </xxf:log>

        <xxf:show if="$must-warn" dialog="fr-action-continuation-dialog" class="fr-action-impl">
            <xf:property
                name="message"
                value="xxf:r('detail.messages.confirmation-dialog-message-overwrite', '|fr-fr-resources|')"/>
            <!-- Context is a `|`-separated tuple as we need more than one piece of information -->
            <xf:property
                name="context"
                value="concat($current-action-id, '|{$continuation-event-prefix}{fr:following-continuation-id(., $action-name, $continuation-position)}')"/>
            <xf:property
                name="positive-targetid"
                value="xxf:absolute-id('fr-actions-model')"/>
            <xf:property
                name="negative-targetid"
                value="xxf:absolute-id('fr-actions-model')"/>
        </xxf:show>
        <xf:dispatch
            if="not($must-warn)"
            name="{$continuation-event-prefix}{fr:following-continuation-id(., $action-name, $continuation-position)}"
            targetid="{$model-id}">
            <xf:property name="fr-action-id" value="$current-action-id" xxf:tunnel="true"/>
        </xf:dispatch>

    </xsl:template>

    <xsl:template match="fr:copy-content" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="view-elem"    as="element()"/>
        <xsl:param tunnel="yes" name="model-id"     as="xs:string"/>
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

<!--        <xsl:variable name="action-sync-iterations"  select="@sync-iterations  = 'true'" as="xs:boolean"/>-->
<!--        <xsl:variable name="action-left-iterations"  select="@left-iterations"           as="xs:string?"/>-->
        <xsl:variable name="action-right-at" select="@right-at/string()" as="xs:string?"/>

        <xxf:log
            class="fr-action-impl"
            name="orbeon.action"
            level="info"
            value="'Starting action'">
            <xf:property name="fr-action-id" value="$current-action-id"/>
            <xf:property name="action-type"  value="'{name(.)}'"/>
        </xxf:log>

        <!-- TODO: improve by grouping all source/destination controls within the same source/destination repeats, and
              adjust the iterations once only. -->
        <xsl:for-each select="fr:map">
            <xsl:variable name="left-name"         select="@left"                                                    as="xs:string"/>
            <xsl:variable name="right-name"        select="@right"                                                   as="xs:string"/>
            <xsl:variable name="right-at"          select="@right-at/string()"                                       as="xs:string?"/>

            <xsl:variable name="left-control"      select="frf:findControlByNameUnderXPath($left-name,  $view-elem)" as="element()?"/>
            <xsl:variable name="right-control"     select="frf:findControlByNameUnderXPath($right-name, $view-elem)" as="element()?"/>
            <xsl:variable name="left-repeat-name"  select="frf:findAncestorRepeatNames($left-control, false())[1]"   as="xs:string?"/> <!-- TODO: more than one repeat level -->
            <xsl:variable name="right-repeat-name" select="frf:findAncestorRepeatNames($right-control, false())[1]"  as="xs:string?"/> <!-- TODO: more than one repeat level -->

            <!-- `start|end|42|all` -->
            <xsl:variable name="right-at"          select="($right-at, $action-right-at, 'end')[1]"                  as="xs:string"/>

            <!-- Pick `apply-defaults` from the destination control.
                 https://github.com/orbeon/orbeon-forms/issues/4038 -->
            <xsl:variable
                name="apply-defaults"
                select="$right-control/@apply-defaults = 'true'"/>

            <xsl:choose>
                <xsl:when test="exists($left-repeat-name) and exists($right-repeat-name)">
<!--                    &lt;!&ndash; Both left and right controls are repeated &ndash;&gt;-->
<!--                    <xsl:variable name="sync-iterations"  select="$action-sync-iterations and not(@sync-iterations  = 'false') or @sync-iterations  = 'true'" as="xs:boolean"/>-->
<!--                    &lt;!&ndash; `first|last|42|all` &ndash;&gt;-->
<!--                    &lt;!&ndash; Could also imagine `1..10` &ndash;&gt;-->
<!--                    <xsl:variable name="left-iterations"  select="(@left-iterations,  $action-left-iterations,  'all')[1]" as="xs:string"/>-->
                    <xf:action class="fr-action-impl"> <!-- group to contain variables scope -->
                        <xf:var
                            name="left-container"
                            value="bind(frf:bindId('{$left-repeat-name}'))"/>
                        <xf:var
                            name="right-container"
                            value="bind(frf:bindId('{$right-repeat-name}'))"/>

                        <xf:var
                            name="repeat-template"
                            value="instance(frf:templateId('{$right-repeat-name}'))"/>

                        <xf:var
                            name="diff"
                            value="count($right-container/*) - count($left-container/*)"/>

                        <!-- Remove extra iterations if any -->
                        <xf:delete
                            if="$diff gt 0"
                            ref="$right-container/*[position() gt count($left-container/*)]"/>

                        <!-- Insert iterations if needed  -->
                        <xf:insert
                            context="$right-container"
                            if="$diff lt 0"
                            ref="*"
                            origin="
                                let $t := frf:updateTemplateFromInScopeItemsetMaps($right-container, $repeat-template)
                                return
                                    for $i in (1 to -$diff)
                                    return $t"
                            position="after"
                            xxf:defaults="{$apply-defaults}"/>

                        <xf:action if="$diff != 0">
                            <xf:rebuild/>
                            <xf:recalculate/>
                        </xf:action>

                        <!-- Update all values -->
                        <xf:action iterate="1 to count($left-container/*)">

                            <!-- TODO: If section template nested under iteration, we need to be explicit about which name
                                  is accessed. -->
                            <xf:var name="p" value="."/>
                            <xf:var name="src" context="$left-container/*[$p]"  value="(.//{$left-name})[1]"/>
                            <xf:var name="dst" context="$right-container/*[$p]" value="(.//{$right-name})[1]"/>

                            <xf:setvalue
                                ref="$dst"
                                value="$src"/>

                        </xf:action>
                    </xf:action>
                </xsl:when>
                <xsl:when test="empty($left-repeat-name)">
                    <!-- Non-repeated source to repeated or non-repeated destination -->
                    <xf:action class="fr-action-impl">
                        <xf:var
                            name="value"
                            value="
                                fr:control-string-value(
                                    '{$left-name}',
                                     false(),
                                     () (: TODO: support this :)
                                )"/>

                        <!-- Q: Is this needed? -->
                        <!--<xf:rebuild/>-->
                        <!--<xf:recalculate/>-->

                        <xf:action>
                            <xsl:copy-of select="fr:build-iterate-att($model-id, $right-name, $right-at, $library-name)"/>
                            <xf:setvalue
                                ref="."
                                value="$value"/>
                        </xf:action>
                    </xf:action>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Temporary restriction: repeated source to non-repeated destination -->
                    <xsl:message terminate="yes">
                        <xsl:text>fr:copy-content: </xsl:text>
                        <xsl:value-of select="concat($left-name, ' and ', $right-name)"/>
                        <xsl:text> can't copy from repeated source to non-repeated destination</xsl:text>
                    </xsl:message>
                </xsl:otherwise>
            </xsl:choose>

        </xsl:for-each>

    </xsl:template>

</xsl:stylesheet>
