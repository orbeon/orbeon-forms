<%@ page pageEncoding="utf-8" contentType="text/html; charset=UTF-8" import="org.orbeon.oxf.fr.embedding.servlet.API" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.util.stream.Collectors" %>
<!DOCTYPE HTML>
<%
    // Where Orbeon Forms is deployed (used by JS API and Angular/React components). Use orbeon-forms-context context
    // parameter from web.xml if available, use /orbeon by default if not.
    String orbeonFormsContext = Objects.requireNonNullElse(application.getInitParameter("orbeon-forms-context"), "/orbeon");

    String ApiCookieName = "orbeon-embedding-api";
    Cookie[] cookies     = request.getCookies();
    String embeddingApi  = "java";
    if (cookies != null)
        for (int i = 0; i < cookies.length; i++) {
            Cookie cookie = cookies[i];
            if (cookie.getName().equals(ApiCookieName))
                embeddingApi = cookie.getValue();
        }

    boolean isEmbeddingApiJava    = embeddingApi.equals("java");
    boolean isEmbeddingApiJS      = embeddingApi.equals("js");
    boolean isEmbeddingApiAngular = embeddingApi.equals("angular");
    boolean isEmbeddingApiReact   = embeddingApi.equals("react");

    String  disabledIfJava    = isEmbeddingApiJava    ? "disabled" : "";
    String  disabledIfJS      = isEmbeddingApiJS      ? "disabled" : "";
    String  disabledIfAngular = isEmbeddingApiAngular ? "disabled" : "";
    String  disabledIfReact   = isEmbeddingApiReact   ? "disabled" : "";

    String  currentApiName = isEmbeddingApiJava    ? "Java API" :
                            (isEmbeddingApiJS      ? "JavaScript API" :
                            (isEmbeddingApiAngular ? "Angular Component"
                                                   : "React Component"));

    String  appParameter  = request.getParameter("app");
    String  formParameter = request.getParameter("form");
    String  selectedApp   = appParameter != null ? appParameter : "orbeon";
    String  selectedForm  = formParameter != null &&
            !((isEmbeddingApiAngular || isEmbeddingApiReact) && formParameter.equals("builder")) ?
            formParameter : "bookshelf";
%>
<html>
<head>
    <title>Orbeon Embedding Demo</title>
    <link rel="stylesheet" href="//getbootstrap.com/2.3.2/assets/css/bootstrap.css">
    <style>
        body    { padding-top: 50px }
        .navbar { font-size: 13px }
    </style>
    <link rel="stylesheet" href="//getbootstrap.com/2.3.2/assets/css/bootstrap-responsive.css">

    <% if (isEmbeddingApiJS) { %>
    <script type="text/javascript" src="<%= orbeonFormsContext %>/xforms-server/baseline.js?updates=<%= selectedForm.equals("builder") ? "fb" : "fr" %>"></script>
    <% } %>

    <% if (isEmbeddingApiAngular) { %>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/zone.js/0.11.4/zone.min.js"></script>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/reflect-metadata/0.1.13/Reflect.min.js"></script>
    <script type="text/javascript" src="assets/angular/polyfills.js"></script>
    <script type="text/javascript" src="assets/angular/main.js"></script>
    <% } %>

    <% if (isEmbeddingApiReact) { %>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/react/17.0.2/umd/react.production.min.js"></script>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/react-dom/17.0.2/umd/react-dom.production.min.js"></script>
    <script type="text/javascript" src="assets/react/main.js"></script>
    <% } %>

    <script type="text/javascript">

        const ApiCookieName = "<%= ApiCookieName %>";

        function getEmbeddingApi() {
            const cookie =
                document.cookie
                    .split("; ")
                    .find(function(cookie) { return cookie.startsWith(ApiCookieName + "="); });
            return cookie
                ? cookie.split("=")[1]
                : "java";
        }

        function setEmbeddingApi(api) {
            document.cookie = ApiCookieName + "=" + api;
        }

        document.addEventListener("click", function(event) {
            if (event.target.id === "switch-to-js-api") {
                setEmbeddingApi("js");
                location.reload();
            } else if (event.target.id === "switch-to-java-api") {
                setEmbeddingApi("java");
                location.reload();
            } else if (event.target.id === "switch-to-angular-api") {
                setEmbeddingApi("angular");
                location.reload();
            } else if (event.target.id === "switch-to-react-api") {
                setEmbeddingApi("react");
                location.reload();
            }
        });

        <% if (isEmbeddingApiJS) { %>
        window.addEventListener('DOMContentLoaded', function() {
            ORBEON.fr.API.embedForm(
                document.getElementById("my-form"),
                "<%= orbeonFormsContext %>",
                "<%= selectedApp %>",
                "<%= selectedForm %>",
                "new"
            )
                .then(() => console.log("`embedForm()` successfully loaded the form"))
                .catch((e) => {
                    console.log("`embedForm()` returned an error");
                    console.log(e);
                });
        });
        <% } %>

        <% if (isEmbeddingApiAngular) { %>
            window.orbeonAngularConfig = {
            app          : "<%= selectedApp %>",
            form         : "<%= selectedForm %>",
            mode         : "new",
            orbeonContext: "<%= orbeonFormsContext %>"
        };

        window.addEventListener('DOMContentLoaded', function() {
            window.initializeAngular();
        });
        <% } %>

        <% if (isEmbeddingApiReact) { %>
        window.orbeonReactConfig = {
            app          : "<%= selectedApp %>",
            form         : "<%= selectedForm %>",
            mode         : "new",
            orbeonContext: "<%= orbeonFormsContext %>"
        };

        window.addEventListener('DOMContentLoaded', function() {
            window.initializeReact();
        });
        <% } %>
    </script>
</head>
<body>
<div class="navbar navbar-inverse navbar-fixed-top">
    <div class="navbar-inner">
        <div class="container">
            <button type="button" class="btn btn-navbar" data-toggle="collapse" data-target=".nav-collapse">
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
            </button>
            <a class="brand" href="#">Orbeon Forms Embedding Demo</a>
            <div class="nav-collapse collapse">
                <ul class="nav">
                    <li><a href="?form=bookshelf">Bookshelf</a></li>
                    <li><a href="?form=building-permit">Building Permit</a></li>
                    <li><a href="?form=emergency-medical-consent">Medical Treatment</a></li>
                    <li><a href="?form=feedback">Feedback</a></li>
                    <li><a href="?form=dmv-14">DMV-14</a></li>
                    <li><a href="?form=w9">W-9</a></li>
                    <% if (!isEmbeddingApiAngular && !isEmbeddingApiReact) { %>
                    <li><a href="?form=builder">Form Builder</a></li>
                    <% } %>
                </ul>
                <ul class="nav pull-right">
                    <li class="dropdown">
                        <a class="dropdown-toggle" data-toggle="dropdown" href="#">
                            <%= currentApiName %>
                            <b class="caret"></b>
                        </a>
                        <ul class="dropdown-menu" role="menu">
                            <li class="<%= disabledIfJava %>">
                                <a id="switch-to-java-api" tabindex="-1" href="#">Java API</a>
                            </li>
                            <li class="<%= disabledIfJS %>">
                                <a id="switch-to-js-api" tabindex="-1" href="#">JavaScript API</a>
                            </li>
                            <li class="<%= disabledIfAngular %>">
                                <a id="switch-to-angular-api" tabindex="-1" href="#">Angular Component</a>
                            </li>
                            <li class="<%= disabledIfReact %>">
                                <a id="switch-to-react-api" tabindex="-1" href="#">React Component</a>
                            </li>
                        </ul>
                    </li>
                </ul>
            </div>
        </div>
    </div>
</div>

<div id="my-form" class="container">
    <%
        if (isEmbeddingApiJava) {
            API.embedFormJava(
                    request,
                    out,
                    selectedApp,
                    selectedForm,
                    "new",
                    null,
                    null,
                    null
            );
        }
    %>

    <% if (isEmbeddingApiAngular) { %>
    <!-- Root element for Angular application -->
    <app-root></app-root>
    <% } %>

    <% if (isEmbeddingApiReact) { %>
    <!-- Root element for React application -->
    <div id="react-root"></div>
    <% } %>
</div>
</body>
</html>
