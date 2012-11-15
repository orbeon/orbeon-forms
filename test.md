Plain input:

    <xf:input id="my-new-input" ref="default">
        <xf:label>My label</xf:label>
        <xf:hint>My hint</xf:hint>
        <xf:help>My help</xf:help>
        <xf:alert>My alert</xf:alert>
    </xf:input>

    <xh:span id="my-new-input·1" class="xforms-control xforms-input xforms-invalid xforms-required xforms-required-filled xforms-type-NCName">
        <xh:label class="xforms-label" for="my-new-input$xforms-input-1·1">My label</xh:label>
        <xh:input id="my-new-input$xforms-input-1·1" type="text" name="my-new-input$xforms-input-1·1" value="Hello!" class="xforms-input-input xforms-type-NCName"/>
        <xh:span class="xforms-help-image" title="" alt=""/>
        <xh:span class="xforms-help">My help</xh:span>
        <xh:span class="xforms-alert-active xforms-alert">My alert</xh:span>
        <xh:span class="xforms-hint">My hint</xh:span>
    </xh:span>

Boolean input:

    <xf:input id="my-new-boolean-input" ref="boolean">
        <xf:label>My label</xf:label>
        <xf:hint>My hint</xf:hint>
        <xf:help>My help</xf:help>
        <xf:alert>My alert</xf:alert>
    </xf:input>

    <xh:span id="my-new-boolean-input·1" class="xforms-control xforms-input xforms-required xforms-required-filled xforms-type-boolean">
        <xh:label class="xforms-label" for="my-new-boolean-input$$e0·1">My label</xh:label>
        <xh:span class="xforms-selected">
            <xh:input id="my-new-boolean-input$$e0·1" type="checkbox" name="my-new-boolean-input·1" value="true" checked="checked"/>
        </xh:span>
        <xh:span class="xforms-help-image" title="" alt=""/>
        <xh:span class="xforms-help">My help</xh:span>
        <xh:span class="xforms-alert-inactive xforms-alert">My alert</xh:span>
        <xh:span class="xforms-hint">My hint</xh:span>
    </xh:span>

Plain input with external LHHA:

    <xf:label for="my-new-input-2">My label</xf:label>
    <xf:hint for="my-new-input-2">My hint</xf:hint>
    <xf:input id="my-new-input-2" ref="default"/>
    <xf:help for="my-new-input-2">My help</xf:help>
    <xf:alert for="my-new-input-2">My alert</xf:alert>

    <xh:label class="xforms-label" id="my-new-input-2$$l·1" for="my-new-input-2$xforms-input-1·1">My label</xh:label>
    <xh:span class="xforms-hint" id="my-new-input-2$$t·1">My hint</xh:span>
    <xh:span id="my-new-input-2·1" class="xforms-control xforms-input xforms-invalid xforms-required xforms-required-filled xforms-type-NCName">
        <xh:input id="my-new-input-2$xforms-input-1·1" type="text" name="my-new-input-2$xforms-input-1·1" value="Hello!" class="xforms-input-input xforms-type-NCName"/>
    </xh:span>
    <xh:span id="my-new-input-2$$i·1" class="xforms-help-image" title="" alt=""/>
    <xh:span class="xforms-help" id="my-new-input-2$$p·1">My help</xh:span>
    <xh:span class="xforms-alert-active xforms-alert" id="my-new-input-2$$a·1">My alert</xh:span>

Boolean input with external LHHA:

    <xf:label for="my-new-boolean-input-2">My label</xf:label>
    <xf:hint for="my-new-boolean-input-2">My hint</xf:hint>
    <xf:input id="my-new-boolean-input-2" ref="boolean"/>
    <xf:help for="my-new-boolean-input-2">My help</xf:help>
    <xf:alert for="my-new-boolean-input-2">My alert</xf:alert>

    <xh:label class="xforms-label" id="my-new-boolean-input-2$$l·1" for="my-new-boolean-input-2$$e0·1">My label</xh:label>
    <xh:span class="xforms-hint" id="my-new-boolean-input-2$$t·1">My hint</xh:span>
    <xh:span id="my-new-boolean-input-2·1" class="xforms-control xforms-input xforms-required xforms-required-filled xforms-type-boolean">
        <xh:span class="xforms-selected">
            <xh:input id="my-new-boolean-input-2$$e0·1" type="checkbox" name="my-new-boolean-input-2·1" value="true" checked="checked"/>
        </xh:span>
    </xh:span>
    <xh:span id="my-new-boolean-input-2$$i·1" class="xforms-help-image" title="" alt=""/>
    <xh:span class="xforms-help" id="my-new-boolean-input-2$$p·1">My help</xh:span>
    <xh:span class="xforms-alert-inactive xforms-alert" id="my-new-boolean-input-2$$a·1">My alert</xh:span>