function submitForm(formName, actionName) {
    top.document.forms[formName].action = actionName;
    top.document.forms[formName].submit();
}

function hideShow(obj) {
    if (parseInt(navigator.appVersion) >= 5 || navigator.appVersion.indexOf["MSIE 5"] != -1)
    {
        if (obj.style.display == "none")
             obj.style.display = "";
        else
             obj.style.display = "none";
    }
}

function oncl(event) {
    var target = event == null ? window.event.srcElement : event.target;

    // Only hide/show when clicked on -/+
    if (target.parentNode.className != 'x') {
        return null;
    }

    while (target.className != 'cd' && target.parentNode) {
        target = target.parentNode;
    }

    if (target.className == 'cd') {
        // Toggle all internal DIVs
        var child = target.firstChild;
        while (child) {
            if (child.className == 'rd' || child.className == 'cd' || child.className == 'id' || child.className == 'c') {
                // Toggle visibility of all relevant children
                hideShow(child);
            } else if (child.className == 'x' && child.firstChild) {
                // Toggle +/-
                var textNode = child.firstChild;
                var value = textNode.nodeValue;
                if (value.indexOf('-') != -1)
                    textNode.nodeValue = value.substring(0, value.indexOf('-')) + '+' + value.substring(value.indexOf('-') + 1);
                else
                    textNode.nodeValue = value.substring(0, value.indexOf('+')) + '-' + value.substring(value.indexOf('+') + 1);
            }

            child = child.nextSibling;
        }
    }
}

function initialize() {
    document.onclick = oncl;
}
