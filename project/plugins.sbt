addSbtPlugin     ("org.portable-scala"  % "sbt-scalajs-crossproject" % "1.3.0")
addSbtPlugin     ("org.scala-js"        % "sbt-scalajs"              % "1.13.0")
addSbtPlugin     ("org.scala-js"        % "sbt-jsdependencies"       % "1.0.2")
addSbtPlugin     ("com.eed3si9n"        % "sbt-buildinfo"            % "0.11.0")
addSbtPlugin     ("com.typesafe.sbt"    % "sbt-less"                 % "1.1.2")
addSbtPlugin     ("com.typesafe.sbt"    % "sbt-uglify"               % "2.0.0")
//addSbtPlugin     ("net.virtual-void"    % "sbt-dependency-graph"     % "0.9.2")
addSbtPlugin     ("com.codecommit"      % "sbt-github-packages"      % "0.5.3")
addCompilerPlugin("org.scalamacros"     % "paradise"                 % "2.1.1" cross CrossVersion.full)
addSbtPlugin     ("io.github.cquiroz"   % "sbt-tzdb"                 % "4.2.0")
//addSbtPlugin     ("io.github.cquiroz"   % "sbt-locales"              % "2.0.1")

libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0" // 1.1.0 "drops support for jsdom 9.x"

// Apparently needed for sbt-web
resolvers += Resolver.typesafeRepo("releases")
resolvers ++= Resolver.sonatypeOssRepos("releases")
