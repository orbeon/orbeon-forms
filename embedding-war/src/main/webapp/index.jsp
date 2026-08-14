<%@ page pageEncoding="utf-8" contentType="text/html; charset=UTF-8" import="org.orbeon.oxf.fr.embedding.servlet.API" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
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

    String  appParameter        = request.getParameter("app");
    String  formParameter       = request.getParameter("form");
    String  documentIdParameter = request.getParameter("document-id");
    String  selectedApp         = appParameter != null ? appParameter : "orbeon";
    String  selectedForm        = formParameter != null &&
            !((isEmbeddingApiAngular || isEmbeddingApiReact) && formParameter.equals("builder")) ?
            formParameter : "bookshelf";
    String  selectedMode        = documentIdParameter != null ? "edit" : "new";
%>
<html>
<head>
    <title>Orbeon Embedding Demo</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css">
    <style>
        body    { padding-top: 60px }
        .navbar { font-size: 13px }
        .navbar.fixed-top { z-index: 1040 } <%-- For the dropdown to show above the Form Builder navbar, at 1030 (Bootstrap default) --%>
    </style>

    <%-- This page's own Bootstrap 5 JavaScript, for its navbar dropdown; Orbeon's copy is namespaced and doesn't
         handle this page's markup (#7809) --%>
    <script type="text/javascript" src="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/js/bootstrap.bundle.min.js"></script>

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
                "<%= selectedMode %>",
                <% if (documentIdParameter != null) { %>"<%= documentIdParameter %>"<% } else { %>undefined<% } %>
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
<nav class="navbar navbar-expand-md navbar-dark bg-dark fixed-top">
    <div class="container">
        <a class="navbar-brand" href="#">Orbeon Forms Embedding Demo</a>
        <button type="button" class="navbar-toggler" data-bs-toggle="collapse" data-bs-target="#demo-navbar" aria-controls="demo-navbar" aria-expanded="false" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div id="demo-navbar" class="collapse navbar-collapse">
            <ul class="navbar-nav me-auto">
                <li class="nav-item"><a class="nav-link" href="?form=bookshelf">Bookshelf</a></li>
                <li class="nav-item"><a class="nav-link" href="?form=building-permit">Building Permit</a></li>
                <li class="nav-item"><a class="nav-link" href="?form=emergency-medical-consent">Medical Treatment</a></li>
                <li class="nav-item"><a class="nav-link" href="?form=feedback">Feedback</a></li>
                <li class="nav-item"><a class="nav-link" href="?form=dmv-14">DMV-14</a></li>
                <li class="nav-item"><a class="nav-link" href="?form=w9">W-9</a></li>
                <% if (!isEmbeddingApiAngular && !isEmbeddingApiReact) { %>
                <li class="nav-item"><a class="nav-link" href="?form=builder">Form Builder</a></li>
                <% } %>
            </ul>
            <ul class="navbar-nav ms-auto">
                <li class="nav-item dropdown">
                    <a class="nav-link dropdown-toggle" data-bs-toggle="dropdown" href="#" role="button" aria-expanded="false">
                        <%= currentApiName %>
                    </a>
                    <ul class="dropdown-menu dropdown-menu-end">
                        <li>
                            <a id="switch-to-java-api" class="dropdown-item <%= disabledIfJava %>" href="#">Java API</a>
                        </li>
                        <li>
                            <a id="switch-to-js-api" class="dropdown-item <%= disabledIfJS %>" href="#">JavaScript API</a>
                        </li>
                        <li>
                            <a id="switch-to-angular-api" class="dropdown-item <%= disabledIfAngular %>" href="#">Angular Component</a>
                        </li>
                        <li>
                            <a id="switch-to-react-api" class="dropdown-item <%= disabledIfReact %>" href="#">React Component</a>
                        </li>
                    </ul>
                </li>
            </ul>
        </div>
    </div>
</nav>

<div id="my-form" class="container">
    <%
        if (isEmbeddingApiJava) {
            Map<String, String> headers = new HashMap<String, String>();
            String username = request.getHeader("Orbeon-Username");
            String roles    = request.getHeader("Orbeon-Roles");
            if (username != null) headers.put("Orbeon-Username", username);
            if (roles    != null) headers.put("Orbeon-Roles",    roles);
            API.embedFormJava(
                    request,
                    out,
                    selectedApp,
                    selectedForm,
                    selectedMode,
                    documentIdParameter,
                    null,
                    headers.isEmpty() ? null : headers
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
