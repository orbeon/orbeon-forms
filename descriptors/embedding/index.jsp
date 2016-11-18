<%@ page pageEncoding="utf-8" contentType="text/html; charset=UTF-8" import="org.orbeon.oxf.fr.embedding.servlet.API" %>
<!DOCTYPE HTML>
<html>
    <head>
        <title>Orbeon Embedding Demo</title>
        <link rel="stylesheet" href="//getbootstrap.com/2.3.2/assets/css/bootstrap.css">
        <style>
            body {
                padding-top: 50px;
            }
        </style>
        <link rel="stylesheet" href="//getbootstrap.com/2.3.2/assets/css/bootstrap-responsive.css">
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
                            <li><a href="?form=builder">Form Builder</a></li>

                        </ul>
                    </div>
                </div>
            </div>
        </div>

        <div class="container">
            <%
                API.embedFormJava(
                    request,
                    out,
                    "orbeon",
                    request.getParameter("form") != null ? request.getParameter("form") : "bookshelf",
                    "new",
                    null,
                    null,
                    null
                );
            %>
        </div>
    </body>
</html>
