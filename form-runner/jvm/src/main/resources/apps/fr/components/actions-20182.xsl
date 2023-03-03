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

    <xsl:variable name="continuation-key">fr-action-continuation-id</xsl:variable>
    <xsl:variable name="continuation-event-prefix">fr-action-continuation-</xsl:variable>

    <xsl:function name="fr:build-iterate-att" as="attribute(iterate)">
        <xsl:param name="model-id"        as="xs:string"/>
        <xsl:param name="to-control-name" as="xs:string"/>
        <xsl:param name="at"              as="xs:string?"/>

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
                            'frf:resolveTargetRelativeToActionSource(xxf:get-request-attribute(''fr-action-source''), ''',
                            $to-control-name,
                            ''', true(), ())'
                        )"/>
            </xsl:otherwise>
        </xsl:choose>

    </xsl:function>

    <xsl:function name="fr:build-context-att" as="attribute()?">
        <xsl:param name="elem"    as="element()"/>
        <xsl:param name="context" as="xs:string?"/>

         <xsl:if test="$context = 'current-iteration'">
             <xsl:attribute name="context">xxf:get-request-attribute('<xsl:value-of select="fr:build-context-param($elem/..)"/>')</xsl:attribute>
         </xsl:if>
    </xsl:function>

    <xsl:function name="fr:build-context-param" as="xs:string">
        <xsl:param name="elem" as="element()"/>
        <xsl:value-of select="concat('fr-iteration-context-', count($elem/ancestor-or-self::fr:data-iterate))"/>
    </xsl:function>

    <xsl:function name="fr:build-is-last-iteration-param" as="xs:string">
        <xsl:param name="elem" as="element()"/>
        <xsl:value-of select="concat('fr-action-continuation-is-last-iteration-', count($elem/ancestor-or-self::fr:data-iterate))"/>
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
                    $continuation-position + 1 + count($block-elem//(fr:service-call | fr:data-iterate | fr:if))
                )"/>
    </xsl:function>

    <!-- Match and modify `fr:action`s -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $candidate-action-models-ids
                ]/fr:listener[
                    @version = '2018.2'
                ]">

        <xsl:variable name="model-id" select="../@id/string()" as="xs:string"/>

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
                        <xf:property name="action-source" value="event('xxf:absolute-targetid')"/>
                    </xf:dispatch>
                </xsl:for-each>

            </xf:action>
        </xsl:if>

        <xsl:if test="exists($control-events)">
            <xf:action
                observer="{for $c in $control-names return concat($c, '-control')}"
                target="#observer"
                event="{
                    for $e in $control-events
                    return $controls-xforms-action-names[index-of($controls-2018.2-action-names, $e)]
                }">

                <xsl:if test="exists($modes)">
                    <xsl:attribute name="if">fr:mode() = (<xsl:value-of
                        select="string-join(for $m in $modes return concat('''', $m, ''''), ',')"/>)</xsl:attribute>
                </xsl:if>

                <xsl:for-each select="$actions">
                    <xf:dispatch name="fr-call-user-{.}-action" targetid="{$model-id}">
                        <xf:property name="action-source" value="event('xxf:absolute-targetid')"/>
                    </xf:dispatch>
                </xsl:for-each>

            </xf:action>
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
            if="xxf:get-request-attribute('{fr:build-is-last-iteration-param(.)}') = true()"
            name="{$continuation-event-prefix}{fr:continuation-id($action-name, $continuation-position + 1)}"
            targetid="fr-form-model">
        </xf:dispatch>

    </xsl:template>

    <!-- Trigger continuation -->
    <xsl:template match="fr:if-end-marker" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="action-name"           as="xs:string"/>
        <xsl:param tunnel="yes" name="continuation-position" as="xs:integer"/>

        <xf:dispatch
            name="{$continuation-event-prefix}{fr:continuation-id($action-name, $continuation-position + 1)}"
            targetid="fr-form-model">
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

        <!-- Create a document with a root element so that `preceding::` works -->
        <xsl:variable name="content-with-markers" as="element(root)">
            <root>
                <xsl:apply-templates select="fr:*" mode="within-action-2018.2-marking"/>
            </root>
        </xsl:variable>

        <xf:action id="{@name}-binding" event="fr-call-user-{@name}-action" target="{$model-id}">

            <!-- TODO: Consider handling `iterate-control-name` per https://github.com/orbeon/orbeon-forms/issues/1833 -->
            <!-- Reset global state-->
            <xf:action type="xpath">
                xxf:set-request-attribute('fr-action-source',                                                               event('action-source')),
                xxf:set-request-attribute('fr-action-error',                                                                false()),
                for $i in 1 to 10 return xxf:set-request-attribute(concat('fr-action-continuation-is-last-iteration-', $i), ()),
                for $i in 1 to 10 return xxf:set-request-attribute(concat('fr-iteration-context-', $i),                     ())
            </xf:action>

            <xsl:for-each-group
                select="$content-with-markers//fr:*[not(parent::fr:service-call)]"
                group-ending-with="fr:service-call | fr:data-iterate-end-marker | fr:if-end-marker">

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
            select="$content-with-markers//fr:*[not(parent::fr:service-call)]"
            group-ending-with="fr:service-call | fr:data-iterate-end-marker | fr:if-end-marker">

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
                    select="$current-group[1]/preceding::fr:*[local-name() = ('service-call', 'data-iterate-end-marker', 'if-end-marker')][1]"/>

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
                            if="xxf:get-request-attribute('{$continuation-key}') = '{$current-continuation-id}'">

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
                                        concat('xxf:get-request-attribute(''', fr:build-context-param($context-changing-elem-opt), ''')')
                                    else
                                        'instance(''fr-form-instance'')'
                                "/>

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

            <xf:action context="xxf:instance('fr-service-request-instance')">
                <xsl:for-each select="$request-actions">
                    <xf:action>
                        <xsl:choose>
                            <xsl:when test="exists(@control)">
                                <xsl:variable name="control" select="@control/string()" as="xs:string"/>
                                <xf:var
                                    name="value"
                                    value="frf:resolveTargetRelativeToActionSource(xxf:get-request-attribute('fr-action-source'), '{$control}', true(), ())"/>
                            </xsl:when>
                            <xsl:when test="exists(@value)">
                                <xsl:variable name="value" select="@value/string()" as="xs:string"/>
                                <xf:var
                                    name="value"
                                    context="$original-context"
                                    value="{frf:replaceVarReferencesWithFunctionCalls(. , $value, false(), $library-name)}">
                                    <xsl:copy-of select="fr:build-context-att(., @expression-context)"/>
                                </xf:var>
                            </xsl:when>
                        </xsl:choose>
                        <xsl:choose>
                            <xsl:when test="./self::fr:value">
                                <xsl:variable name="ref" select="@ref/string()" as="xs:string"/>
                                <xf:setvalue
                                    ref="{frf:replaceVarReferencesWithFunctionCalls(. , $ref, false(), $library-name)}"
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

        <xf:action type="xpath">xxf:set-request-attribute('<xsl:value-of select="$continuation-key"/>', '<xsl:value-of select="fr:continuation-id($action-name, $continuation-position + 1)"/>')</xf:action>
        <xf:insert ref="xxf:instance('fr-service-response-instance')" origin="xf:element('response')"/>
        <xf:send submission="{$service-name}-submission"/>

    </xsl:template>

    <xsl:template match="fr:data-iterate" mode="within-action-2018.2">
        <xsl:param tunnel="yes" name="action-name"           as="xs:string"/>
        <xsl:param tunnel="yes" name="continuation-position" as="xs:integer"/>
        <xsl:param tunnel="yes" name="library-name"          as="xs:string?"/>

        <xsl:variable name="data-iterate-elem" select="."/>

        <xf:action if="empty(({@ref}))">
            <xf:dispatch
                name="{$continuation-event-prefix}{fr:following-continuation-id(., $action-name, $continuation-position)}"
                targetid="fr-form-model">
        </xf:dispatch>
        </xf:action>
        <xf:action iterate="{frf:replaceVarReferencesWithFunctionCalls(. , @ref, false(), $library-name)}">
            <xsl:copy-of select="fr:build-context-att($data-iterate-elem, @expression-context)"/>

            <!-- Apply only up to the first delimiter for https://github.com/orbeon/orbeon-forms/issues/4067 -->
            <xsl:for-each-group
                select="fr:*"
                group-ending-with="fr:service-call | fr:data-iterate-end-marker | fr:if | fr:data-iterate">

                <xsl:variable name="group-position" select="position()"/>
                <xsl:if test="$group-position = 1">
                    <xf:action if="not(xxf:get-request-attribute('fr-action-error') = true())">
                        <xf:action type="xpath">xxf:set-request-attribute('<xsl:value-of select="fr:build-is-last-iteration-param($data-iterate-elem)"/>', position() = last())</xf:action>
                        <xf:action type="xpath">xxf:set-request-attribute('<xsl:value-of select="fr:build-context-param($data-iterate-elem)"/>', .)</xf:action>
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

        <xf:var name="{$var-name}" value="{frf:replaceVarReferencesWithFunctionCalls(. , @condition, false(), $library-name)}">
            <xsl:copy-of select="fr:build-context-att(., @expression-context)"/>
        </xf:var>
        <xf:action if="${$var-name}">
            <xsl:for-each-group
                select="fr:*"
                group-ending-with="fr:service-call | fr:if-end-marker | fr:if | fr:data-iterate">

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
            </xf:dispatch>
        </xf:action>
    </xsl:template>

    <xsl:template match="fr:repeat-clear" mode="within-action-2018.2">

        <xsl:variable name="repeat-name" select="@repeat/string()" as="xs:string"/>
        <xsl:variable name="at"          select="@at/string()"     as="xs:string?"/>

        <xf:action type="xpath">
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

        <xf:action type="xpath">
            frf:repeatAddIteration('<xsl:value-of select="frf:findContainerDetailsCompileTime($fr-form-model, $repeat-name, $at, false())"/>', <xsl:value-of select="$apply-defaults"/>())
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:repeat-remove-iteration" mode="within-action-2018.2">

        <xsl:variable name="repeat-name" select="@repeat/string()" as="xs:string"/>
        <xsl:variable name="at"          select="@at/string()"     as="xs:string?"/>

        <xf:action type="xpath">
            frf:repeatRemoveIteration('<xsl:value-of select="frf:findContainerDetailsCompileTime($fr-form-model, $repeat-name, $at, false())"/>')
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:control-setvalue" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id"     as="xs:string"/>
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="value-expr"      select="@value/string()"   as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xf:action>
            <xf:var name="value" value="{frf:replaceVarReferencesWithFunctionCalls(. , $value-expr, false(), $library-name)}"/>

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:action>
                <xsl:copy-of select="fr:build-iterate-att($model-id, $to-control-name, $at)"/>
                <xf:setvalue
                    ref="."
                    value="$value"/>
            </xf:action>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:control-clear" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id" as="xs:string"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xf:action>

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:action>
                <xsl:copy-of select="fr:build-iterate-att($model-id, $to-control-name, $at)"/>
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

        <xxf:setvisited
            control="{frf:findControlByNameUnderXPath($to-control-name, $view-elem)/@id}"
            recurse="true"
            visited="{($visited, 'true')[1]}"/>

    </xsl:template>

    <xsl:template match="fr:control-setfocus" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="view-elem"    as="element()"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>

        <xf:setfocus
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

        <xf:action>

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
                    value="{frf:replaceVarReferencesWithFunctionCalls($elem , $items-expr, false(), $library-name)}">
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
                    <xf:var name="item-label" value="{frf:replaceVarReferencesWithFunctionCalls($elem , $label-expr, false(), $library-name)}"/>
                    <xf:var name="item-value" value="{frf:replaceVarReferencesWithFunctionCalls($elem , $value-expr, false(), $library-name)}"/>
                    <xsl:choose>
                        <xsl:when test="exists($hint-expr)">
                            <xf:var name="item-hint"  value="{frf:replaceVarReferencesWithFunctionCalls($elem , $hint-expr, false(), $library-name)}"/>
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

        <xf:action>
            <xf:var name="value" value="."/>
            <xf:insert
                context="xxf:instance('fr-service-response-instance')"
                ref="instance('fr-dataset-{$dataset-name}')"
                origin="$value"/>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:dataset-clear" mode="within-action-2018.2">

        <xsl:variable name="dataset-name" select="@name/string()" as="xs:string"/>

        <xf:action>
            <xf:insert
                ref="instance('fr-dataset-{$dataset-name}')"
                origin="xf:element('_')"/>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:process-call" mode="within-action-2018.2">

        <xsl:variable name="process-scope" select="@scope/string()" as="xs:string"/>
        <xsl:variable name="process-name"  select="@name/string()"  as="xs:string"/>

        <xf:action type="xpath">
            fr:run-process-by-name('<xsl:value-of select="$process-scope"/>', '<xsl:value-of select="$process-name"/>')
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:navigate" mode="within-action-2018.2">

        <xsl:variable name="location" select="@location/string()" as="xs:string"/>
        <xsl:variable name="target"   select="@target/string()"   as="xs:string?"/>

        <!-- Prefer `<xf:load>` to `fr:run-process(…, 'navigate(…))`, as the latter builds a process (string) with
             XPath, which requires escaping, and is thus more error prone -->
        <xf:load resource="{$location}">
            <xsl:if test="exists($target)">
                <xsl:attribute name="xxf:target" select="$target"/>
            </xsl:if>
        </xf:load>

    </xsl:template>

    <xsl:template match="fr:control-setattachment" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id" as="xs:string"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xf:action>
            <xf:var name="value"     value="xxf:instance('fr-service-response-instance')/string()"/>
            <xf:var name="mediatype" value="uri-param-values($value, 'mediatype')[1]"/>
            <xf:var name="size"      value="uri-param-values($value, 'size')[1]"/>

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:action>
                <xsl:copy-of select="fr:build-iterate-att($model-id, $to-control-name, $at)"/>
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

    <xsl:template match="fr:control-setfilename" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id"     as="xs:string"/>
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="value-expr"      select="@value/string()"   as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xf:action>
            <xf:var name="value" value="{frf:replaceVarReferencesWithFunctionCalls(. , $value-expr, false(), $library-name)}"/>

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:action>
                <xsl:copy-of select="fr:build-iterate-att($model-id, $to-control-name, $at)"/>
                <xf:setvalue
                    ref="@filename"
                    value="$value"/>
            </xf:action>

        </xf:action>

    </xsl:template>

    <xsl:template match="fr:control-setmediatype" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id"     as="xs:string"/>
        <xsl:param tunnel="yes" name="library-name" as="xs:string?"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="value-expr"      select="@value/string()"   as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xf:action>
            <xf:var name="value" value="{frf:replaceVarReferencesWithFunctionCalls(. , $value-expr, false(), $library-name)}"/>

            <xf:rebuild/>
            <xf:recalculate/>

            <xf:action>
                <xsl:copy-of select="fr:build-iterate-att($model-id, $to-control-name, $at)"/>
                <xf:setvalue
                    ref="image/@mediatype, ./@mediatype"
                    value="$value"/>
            </xf:action>

        </xf:action>

    </xsl:template>

    <xsl:template match="fr:alert" mode="within-action-2018.2">
        <xf:var name="message"><xsl:value-of select="@message"/></xf:var>
        <xf:message value="xxf:evaluate-avt($message)"/>
    </xsl:template>

</xsl:stylesheet>
