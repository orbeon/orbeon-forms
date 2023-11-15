<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:array="http://www.w3.org/2005/xpath-functions/array">

    <xsl:function name="fr:direct-name-for-select1-element" as="xs:string">
        <xsl:param name="elem" as="element()"/>
        <xsl:value-of select="
            if (local-name($elem) = 'select1' and $elem/@appearance = 'dropdown') then
                'dropdown-select1'
            else if (local-name($elem) = 'select1' and $elem/@appearance = 'search') then
                'dropdown-select1-search'
            else
                local-name($elem)"/>
    </xsl:function>

    <xsl:function name="fr:find-view-for-model" as="element()?">
        <xsl:param name="model" as="element(xf:model)"/>
        <xsl:param name="body" as="element()?"/>
        <xsl:sequence
            select="
                if (exists($model/parent::xbl:implementation)) then
                    $model/parent::xbl:implementation/parent::xbl:binding/xbl:template
                else
                    $body"/>
    </xsl:function>

    <xsl:function name="fr:find-itemset-actions-control-names" as="xs:string*">
        <xsl:param name="model" as="element(xf:model)"/>
        <xsl:sequence select="
            distinct-values(
                (
                    for $e in fr:action-bindings($model)/fr:itemset-actions-elements(.)
                    return
                        replace($e/(*:variable | *:var)[@name = 'control-name']/(@value | @select)[1], '^''(.+)''$', '$1'),
                    for $e in fr:action-bindings-2018.2($model)/fr:itemset-actions-elements-2018.2(.)
                    return
                        $e/@control/string()
                )
            )"/>
    </xsl:function>

    <!-- Static selection control validation -->
    <!-- https://github.com/orbeon/orbeon-forms/issues/6008 -->
    <xsl:function name="fr:find-static-selection-control-names" as="xs:string*">
        <xsl:param name="model" as="element(xf:model)"/>

        <xsl:variable
            name="resources-root"
            as="element()?"
            select="($model/xf:instance[@id = 'fr-form-resources']/*)[1]"/>

        <xsl:sequence
            select="distinct-values($resources-root/*[1]/*[exists(item)]/local-name())"/>
    </xsl:function>

    <xsl:function name="fr:find-single-selection-controls" as="element()*">
        <xsl:param name="controls-root"          as="element()"/>
        <xsl:param name="include-open-selection" as="xs:boolean"/>

        <xsl:sequence
            select="
                $controls-root//*[
                    exists(@id) and
                    frf:isSingleSelectionControl(local-name()) and
                    ($include-open-selection or not(exists(self::fr:open-select1)))
                ]"/>
    </xsl:function>

    <xsl:function name="fr:find-multiple-selection-controls" as="element()*">
        <xsl:param name="controls-root"          as="element()"/>
        <xsl:param name="include-open-selection" as="xs:boolean"/>

        <xsl:sequence
            select="
                $controls-root//*[
                    exists(@id) and
                    frf:isMultipleSelectionControl(local-name()) and
                    ($include-open-selection or not(exists(self::fr:open-select))) (: doesn't exist yet but just in case we cover it :)
                ]"/>
    </xsl:function>

    <xsl:function name="fr:choices-validation-selection-controls" as="element()*">
        <xsl:param name="controls-root"        as="element()"/>
        <xsl:param name="model"                as="element(xf:model)"/>
        <xsl:param name="for-static-selection" as="xs:boolean"/> <!-- vs. for itemset actions -->

        <xsl:sequence
            select="
                (: Shouldn't need `distinct-values()` as single and multiple selection controls are exclusive, and
                   control names should be unique under a given controls root :)
                (
                    fr:find-single-selection-controls(
                        $controls-root,
                        not($for-static-selection)
                    ),
                    fr:find-multiple-selection-controls(
                        $controls-root,
                        not($for-static-selection)
                    )
                )[
                    if ($for-static-selection) then
                        frf:controlNameFromId(@id) = fr:find-static-selection-control-names($model)
                    else
                        frf:controlNameFromId(@id) = fr:find-itemset-actions-control-names($model)
                ]"/>
    </xsl:function>

    <xsl:function name="fr:build-template-param-map">
        <xsl:param name="params"                                as="element(*)*"/>
        <xsl:param name="library-name"                          as="xs:string?"/>
        <xsl:param name="for-pdf"                               as="xs:boolean"/>
        <xsl:param name="custom-css-class-to-control-names-map" as="element(_)?"/>

        <xsl:value-of
            select="
                concat(
                    'map:merge((',
                        string-join(
                            for $p in $params
                                return for $name in ($p/*:name, name($p))[1][p:non-blank()]
                                    return for $type in ($p/*:type, $p/@type)[1] (: element first as attribute can also exist with for example value `object` :)
                            return
                                concat(
                                    'map:entry(''',
                                        $name,
                                        ''',',
                                        if ($type = ('ExpressionParam', 'formula')) then
                                            fr:maybe-replace(
                                                concat(
                                                    'string((',
                                                    frf:replaceVarReferencesWithFunctionCalls(
                                                        $p/(*:expr, *:value)[1],
                                                        $p/(*:expr, *:value)[1],
                                                        false(),
                                                        $library-name,
                                                        ()
                                                    ),
                                                    ')[1])'
                                                ),
                                                $for-pdf
                                            )
                                        else if ($type = ('ControlValueParam', 'control-value')) then
                                            fr:maybe-replace(
                                                concat(
                                                    'string((',
                                                    frf:replaceVarReferencesWithFunctionCalls(
                                                        $p/(*:controlName, *:control-name, *:controlCssClass, *:control-css-class)[1],
                                                        if (exists($p/(*:controlName, *:control-name))) then
                                                            concat('$', $p/(*:controlName, *:control-name)[1])
                                                        else if (exists($p/(*:controlCssClass, *:control-css-class))) then
                                                            (
                                                                $custom-css-class-to-control-names-map/
                                                                    entry[
                                                                        @class = $p/(*:controlCssClass, *:control-css-class)
                                                                    ]/*/concat('$', name(.)),
                                                                ''''''
                                                            )[1]
                                                        else
                                                            error(),
                                                        false(),
                                                        $library-name,
                                                        ()
                                                    ),
                                                    ')[1])'
                                                ),
                                                $for-pdf
                                            )
                                        else if (
                                            $type = (
                                                ('LinkToEditPageParam',    'link-to-edit-page'),
                                                ('LinkToViewPageParam',    'link-to-view-page'),
                                                ('LinkToNewPageParam',     'link-to-new-page'),
                                                ('LinkToSummaryPageParam', 'link-to-summary-page'),
                                                ('LinkToHomePageParam',    'link-to-home-page'),
                                                ('LinkToFormsPageParam',   'link-to-forms-page'),
                                                ('LinkToAdminPageParam',   'link-to-admin-page'),
                                                ('LinkToPdfParam',         'link-to-pdf')
                                            )
                                        ) then
                                            fr:maybe-replace(
                                                concat(
                                                    'frf:buildLinkBackToFormRunner(''',
                                                    $type,
                                                    ''', ',
                                                    if ($p/*:token = 'true') then 'true()' else 'false()',
                                                    ')'
                                                ),
                                                $for-pdf
                                            )
                                        else if ($for-pdf and $type = (('PageNumberParam', 'page-number'), ('PageCountParam', 'page-count'))) then
                                            string-join(
                                                (
                                                    '''',
                                                    '&quot; counter(',
                                                    if ($type = ('PageNumberParam', 'page-number')) then 'page' else 'pages',
                                                    ', ',
                                                    ($p/*:format, 'decimal')[1],
                                                    ') &quot;',
                                                    ''''
                                                ),
                                                ''
                                            )
                                        else if ($for-pdf and $type = ('FormTitleParam', 'form-title')) then
                                            fr:maybe-replace('fr:form-title()', $for-pdf)
                                        else if ($for-pdf and $type = ('ImageParam', 'image')) then
                                            (: TODO: `*:url` :)
                                            if (exists($p/*:visible)) then
                                                concat(
                                                    'if (',
                                                    frf:replaceVarReferencesWithFunctionCalls(
                                                        $p/*:visible,
                                                        $p/*:visible,
                                                        false(),
                                                        $library-name,
                                                        ()
                                                    ),
                                                    ') then ''&quot; element(logo) &quot;'' else '''''
                                                )
                                            else
                                                '''&quot; element(logo) &quot;'''
                                        else
                                            error(),
                                    ')'
                                ),
                            ','
                        ),
                '))'
            )"/>

    </xsl:function>

    <xsl:function name="fr:maybe-replace" as="xs:string?">
        <xsl:param name="content" as="xs:string?"/>
        <xsl:param name="replace" as="xs:boolean"/>
        <xsl:value-of select="
            if (exists($content)) then
                if ($replace) then
                    replace(
                        $content,
                        '&quot;',
                        '\\&quot;'
                    )
                else
                    $content
            else
                ()"/>
    </xsl:function>

</xsl:stylesheet>
