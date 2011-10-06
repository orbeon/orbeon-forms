Dom = YAHOO.util.Dom
Connect = YAHOO.util.Connect
Event = YAHOO.util.Event
JSON = YAHOO.lang.JSON
Get = YAHOO.util.Get
StyleSheet = YAHOO.util.StyleSheet

insertResponseInContainer = (container, response) ->

    # Insert DOM from Ajax response
    container.innerHTML = response.responseText

    # Mock JSON we're retrieve from a div
    jsonContainer = (Dom.getElementsByClassName "orbeon-portlet-resources", "div", container)[0]
    if jsonContainer
        jsonContainer.parentNode.removeChild jsonContainer
        json = jsonContainer.innerText || jsonContainer.textContent
        json = json.replace /\n/g, " "
        json = eval "json = " + json

        # Prevent YUI DOMReady event from firing everything is loaded
        Event.DOMReadyEvent.fired = false
        # When everything is loaded, fire the DOMReady event
        loadComplete = do ->
            count = 0
            -> if ++count == 2
                ORBEON.xforms.Init.document()
                #Event.DOMReadyEvent.fire()
                container.style.display = ""

        # Load styles
        do ->
            # Loading linked CSS
            styleURLs = (style.src for style in json.styles when style.src?)
            Get.css styleURLs, onSuccess: loadComplete
            # Insert inline CSS
            new StyleSheet style.text for style in json.styles when style.text?

        # Load scripts
        do ->
            # Load scripts
            do loadScripts = () ->
                if json.scripts.length > 0
                    existingScript = json.scripts.shift()
                    newScript = document.createElement "script"
                    newScript.type = "text/javascript"
                    newScript.text = existingScript.text
                    if existingScript.src?
                        newScript.src = existingScript.src
                        newScript.onreadystatechange = loadScripts
                        newScript.onload = loadScripts
                    document.body.appendChild newScript
                    loadScripts() if existingScript.text
                else loadComplete()
    else
        container.style.display = ""

Event.onDOMReady ->
    containers = Dom.getElementsByClassName "orbeon-portlet-deferred", "div", document
    for container in containers
        url = container.innerText || container.textContent
        Connect.asyncRequest "POST", url, {
            success: (response) -> insertResponseInContainer container, response
            failure: ->}, "" # TODO: failure
