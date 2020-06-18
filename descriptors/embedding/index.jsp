<%@ page pageEncoding="utf-8" contentType="text/html; charset=UTF-8" import="org.orbeon.oxf.fr.embedding.servlet.API" %>
<!DOCTYPE HTML>
<%
  String ApiCookieName = "orbeon-embedding-api";
  Cookie[] cookies     = request.getCookies();
  String embeddingApi  = "java";
  for (int i = 0; i < cookies.length; i++) {
    Cookie cookie = cookies[i];
    if (cookie.getName().equals(ApiCookieName))
      embeddingApi = cookie.getValue();
  }
  boolean isEmbeddingApiJava = embeddingApi.equals("java");
  boolean isEmbeddingApiJS   = ! isEmbeddingApiJava;
  String  disabledIfJava     = isEmbeddingApiJava ? "disabled" : "";
  String  disabledIfJS       = isEmbeddingApiJava ? "" : "disabled";
  String  currentApiName     = isEmbeddingApiJava ? "Java API" : "JavaScript API";
  String  formParameter      = request.getParameter("form");
  String  selectedForm       = formParameter != null && !(isEmbeddingApiJS && formParameter.equals("builder")) ?
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
        <script type="text/javascript" src="/orbeon/xforms-server/baseline.js?updates=fr"></script>
        <% } %>
        <script type="text/javascript">

          var ApiCookieName = "<%= ApiCookieName %>";

          function getEmbeddingApi() {
            var cookie =
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
            if (event.target.id == "switch-to-js-api") {
              setEmbeddingApi("js");
              location.reload();
            } else if (event.target.id == "switch-to-java-api") {
              setEmbeddingApi("java");
              location.reload();
            }
          });
          <% if (isEmbeddingApiJS) { %>
          window.addEventListener('DOMContentLoaded', function() {
            ORBEON.fr.API.embedForm(
              document.getElementById("my-form"),
              "/orbeon",
              "orbeon",
              "<%= selectedForm %>",
              "new"
            );
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
                            <li><a href="?form=dmv-14">DMV-14</a></li>
                            <li><a href="?form=w9">W-9</a></li>
                            <li><a href="?form=controls">Controls</a></li>
                            <li><a href="?form=contact">Contact</a></li>
                            <% if (isEmbeddingApiJava) { %>
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
                    "orbeon",
                    selectedForm,
                    "new",
                    null,
                    null,
                    null
                );
              }
            %>
        </div>
    </body>
</html>
