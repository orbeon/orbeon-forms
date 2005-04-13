// global flag
var isIE = false;

// global request and XML document objects
var req;

// retrieve XML document (reusable generic function);
// parameter is URL string (relative or complete) to
// an .xml file whose Content-Type is a valid XML
// type, such as text/xml; XML source must be from
// same domain as HTML file
function loadXMLDoc(url) {
    // branch for native XMLHttpRequest object
    if (window.XMLHttpRequest) {
        req = new XMLHttpRequest();
        req.onreadystatechange = processReqChange;
        req.open("GET", url, true);
        req.send(null);
    // branch for IE/Windows ActiveX version
    } else if (window.ActiveXObject) {
        isIE = true;
        req = new ActiveXObject("Microsoft.XMLHTTP");
        if (req) {
            req.onreadystatechange = processReqChange;
            req.open("GET", url, true);
            req.send();
        }
    }
}

// handle onreadystatechange event of req object
function processReqChange() {
    // only if req shows "loaded"
    if (req.readyState == 4) {
        // only if "OK"
        if (req.status == 200) {
            xmlDocLoaded();
         } else {
            alert("There was a problem retrieving the XML data:\n" +
                req.statusText);
         }
    }
}

var previous = "";
var next = "";
var searching = "";

function xmlDocLoaded() {
    var translation = req.responseXML.documentElement.firstChild.nodeValue;
    document.getElementById("target").value = translation;
    if (next == "") {
    	searching = false;
    } else {
    	var text = next;
    	next = "";
    	loadTranslation(text);
    }
}

function loadTranslation(text) {
	loadXMLDoc("/ops/direct/translate/rest-service?text=" + text);
}

function updateTranslation() {
	var source = document.getElementById("source").value;
	if (source != previous) {
		previous = source;
		if (source == "") {
			document.getElementById("target").value = "";
		} else {
			if (searching) {
				next = escape(source);
			} else {
				searching = true;
				loadTranslation(escape(source));
			}
		}
	}
}
