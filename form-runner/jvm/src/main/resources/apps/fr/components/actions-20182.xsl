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
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:import href="oxf:/apps/fr/components/actions-common.xsl"/>

    <xsl:variable name="continuation-key">fr-service-continuation-id</xsl:variable>

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

    <!-- Match and modify `fr:action`s -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $candidate-action-models-ids
                ]/fr:action[
                    @version = '2018.2'
                ]">

        <xsl:variable name="model-id"    select="../@id/string()" as="xs:string"/>
        <xsl:variable name="action-name" select="@name/string()"  as="xs:string"/>

        <xf:action id="{@name}-binding" event="fr-call-user-{@name}-action" target="{$model-id}">

            <!-- TODO -->
            <xsl:variable name="var" select="()"/>

            <!-- Choose to iterate or not on `$iterate-control-name` -->
            <!-- Also store the absolute id of the source in the request -->
            <!-- NOTE: If another action was triggered during the execution of this action, there
                 could be a race condition and `fr-action-source` might be set to the incorrect
                 value. -->
            <xsl:choose>
                <xsl:when test="exists($var)">
                    <xsl:copy-of select="$var"/>
                    <!-- Q: Why `@iterate` and not `@context`, or just on the nested action? -->
                    <xf:action iterate="frf:findRepeatedControlsForTarget(event('action-source'), $iterate-control-name)">
                        <xf:action type="xpath">xxf:set-request-attribute('fr-action-source', string(.))</xf:action>
                    </xf:action>
                </xsl:when>
                <xsl:otherwise>
                    <xf:action type="xpath">xxf:set-request-attribute('fr-action-source', event('action-source'))</xf:action>
                </xsl:otherwise>
            </xsl:choose>

            <xsl:for-each-group
                select="fr:*"
                group-ending-with="fr:service-call">

                <xsl:variable name="group-position" select="position()"/>

                <xsl:choose>
                    <xsl:when test="position() = 1">
                        <xsl:apply-templates select="current-group()" mode="within-action-2018.2">
                            <xsl:with-param tunnel="yes" name="model-id"        select="$model-id"/>
                            <xsl:with-param tunnel="yes" name="action-name"     select="$action-name"/>
                            <xsl:with-param tunnel="yes" name="continuation-id" select="concat($action-name, '-', $group-position, '-id')"/>
                        </xsl:apply-templates>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:variable
                            name="preceding-service-name"
                            select="preceding-sibling::fr:service-call[1]/@service/string()"/>
                        <xsl:variable
                            name="preceding-continuation-id"
                            select="concat($action-name, '-', $group-position - 1, '-id')"/>
                        <xf:action
                            observer="{$preceding-service-name}-submission"
                            event="xforms-submit-done"
                            context="xxf:instance('fr-service-response-instance')"
                            if="xxf:get-request-attribute('{$continuation-key}') = '{$preceding-continuation-id}'">

                            <!-- TODO: check whether we need to put this at the top-level -->

                            <xsl:apply-templates select="current-group()" mode="within-action-2018.2">
                                <xsl:with-param tunnel="yes" name="model-id"        select="$model-id"/>
                                <xsl:with-param tunnel="yes" name="action-name"     select="$action-name"/>
                                <xsl:with-param tunnel="yes" name="continuation-id" select="concat($action-name, '-', $group-position, '-id')"/>
                            </xsl:apply-templates>

                        </xf:action>
                    </xsl:otherwise>
                </xsl:choose>

            </xsl:for-each-group>

        </xf:action>

    </xsl:template>

    <xsl:template match="fr:service-call" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="continuation-id" as="xs:string"/>

        <xsl:variable name="service-name" select="@service/string()"/>

        <xf:insert
            ref="xxf:instance('fr-service-request-instance')"
            origin="saxon:parse(xxf:instance('{$service-name}-instance'))"/>

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
                                    value="frf:resolveTargetRelativeToActionSource(xxf:get-request-attribute('fr-action-source'), '{$control}', true())"/>
                            </xsl:when>
                            <xsl:when test="exists(@value)">
                                <xsl:variable name="value" select="@value/string()" as="xs:string"/>
                                <xf:var
                                    name="value"
                                    context="$original-context"
                                    value="{$value}"/>
                            </xsl:when>
                        </xsl:choose>
                        <xsl:choose>
                            <xsl:when test="./self::fr:value">
                                <xsl:variable name="ref" select="@ref/string()" as="xs:string"/>
                                <xf:setvalue
                                    ref="{$ref}"
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
                                <xsl:variable name="name" select="@name/string()" as="xs:string"/>
                                <xf:setvalue
                                    ref="/*/{$name}"
                                    value="$value"/>
                            </xsl:when>
                        </xsl:choose>
                    </xf:action>
                </xsl:for-each>
            </xf:action>
        </xsl:if>

        <xf:action type="xpath">xxf:set-request-attribute('<xsl:value-of select="$continuation-key"/>', '<xsl:value-of select="$continuation-id"/>')</xf:action>
        <xf:send submission="{$service-name}-submission"/>

    </xsl:template>

    <xsl:template match="fr:data-iterate" mode="within-action-2018.2">

        <xf:action iterate="{@ref}">
            <xsl:if test="exists(fr:service-call)">
                <xsl:message terminate="yes">Nested `fr:service-call` are not supported yet!</xsl:message>
            </xsl:if>
            <xsl:apply-templates select="fr:*" mode="within-action-2018.2"/>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:repeat-clear" mode="within-action-2018.2">

        <xsl:variable name="repeat-name" select="@repeat/string()" as="xs:string"/>

        <xf:delete ref="bind(frf:bindId('{$repeat-name}'))/*"/>

    </xsl:template>

    <xsl:template match="fr:repeat-add-iteration" mode="within-action-2018.2">

        <xsl:variable name="repeat-name" select="@repeat/string()" as="xs:string"/>
        <xsl:variable name="at"          select="@at/string()"   as="xs:string"/>

        <!-- TODO -->
        <xsl:variable name="apply-defaults" select="true()"/>

        <!-- NOTE: We might like to support `after-current | before-current`, for for that we need the `index()` function which
             needs a repeat id, and we don't have it right now. -->

        <xf:action>
            <xf:var
                name="container"
                value="bind(frf:bindId('{$repeat-name}'))"/>
            <xf:var
                name="repeat-template"
                value="instance(frf:templateId('{$repeat-name}'))"/>
            <xf:insert
                context="$container"
                ref="*[{
                    if ($at = 'end') then
                        'last()'
                    else if ($at = 'start') then
                        '1'
                    else if ($at castable as xs:integer) then
                        $at
                    else
                        error()
                }]"
                origin="frf:updateTemplateFromInScopeItemsetMaps($container, $repeat-template)"
                position="{
                    if ($at = 'start') then
                        'before'
                    else
                        'after'
                }"
                xxf:defaults="{$apply-defaults}"/>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:repeat-remove-iteration" mode="within-action-2018.2">

        <xsl:variable name="repeat-name" select="@repeat/string()" as="xs:string"/>
        <xsl:variable name="at"          select="@at/string()"   as="xs:string"/>

        <xf:action>
            <xf:var
                name="container"
                value="bind(frf:bindId('{$repeat-name}'))"/>
            <xf:delete
                context="$container"
                ref="*[{
                    if ($at = 'end') then
                        'last()'
                    else if ($at = 'start') then
                        '1'
                    else if ($at castable as xs:integer) then
                        $at
                    else
                        error()
                }]"/>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:control-setvalue" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id" as="xs:string"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="value-expr"      select="@value/string()"   as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xf:action>
            <xf:var name="value" value="{$value-expr}"/>

            <!-- Will run only if needed, right? -->
            <xf:rebuild/>
            <xf:revalidate/>

            <xsl:choose>
                <xsl:when test="exists($at)">
                    <xf:setvalue
                        ref="frf:resolveTargetRelativeToActionSourceFromBinds('{$model-id}', '{$to-control-name}')[{
                            if ($at = 'end') then
                                'last()'
                            else if ($at = 'start') then
                                '1'
                            else if ($at castable as xs:integer) then
                                xs:integer($at)
                            else
                                error()
                        }]"
                        value="$value"/>
                </xsl:when>
                <xsl:otherwise>
                    <xf:setvalue
                        iterate="frf:resolveTargetRelativeToActionSource(xxf:get-request-attribute('fr-action-source'), '{$to-control-name}', true())"
                        ref="."
                        value="$value"/>
                </xsl:otherwise>
            </xsl:choose>
        </xf:action>

    </xsl:template>

    <xsl:template match="fr:control-setitems" mode="within-action-2018.2">

        <xsl:param tunnel="yes" name="model-id" as="xs:string"/>

        <xsl:variable name="to-control-name" select="@control/string()" as="xs:string"/>
        <xsl:variable name="items-expr"      select="@items/string()"   as="xs:string"/>
        <xsl:variable name="label-expr"      select="@label/string()"   as="xs:string"/>
        <xsl:variable name="value-expr"      select="@value/string()"   as="xs:string"/>
        <xsl:variable name="at"              select="@at/string()"      as="xs:string?"/>

        <xf:action>

            <!-- Will run only if needed, right? -->
            <xf:rebuild/>
            <xf:revalidate/>

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
                    value="{$items-expr}"/>

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
                    <xf:var name="item-label" value="{$label-expr}"/>
                    <xf:var name="item-value" value="{$value-expr}"/>
                    <xf:insert
                        context="$new-choices-holder"
                        ref="*"
                        origin="xf:element('item', (xf:element('label', xs:string($item-label)), xf:element('value', xs:string($item-value))))"/>
                </xf:action>
            </xf:action>

            <!-- Delegate the rest to common implementation. We should not duplicate much of the code above either, but
                 the problem is the evaluation of `response-items`, 'item-label', and 'item-value', which must take place
                 in a context where variables are available, so we cannot use `saxon:evaluate()`. -->
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

</xsl:stylesheet>
