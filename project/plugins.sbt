addSbtPlugin     ("org.portable-scala"  % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin     ("org.scala-js"        % "sbt-scalajs"              % "1.20.1")
addSbtPlugin     ("org.scala-js"        % "sbt-jsdependencies"       % "1.0.2")
addSbtPlugin     ("com.eed3si9n"        % "sbt-buildinfo"            % "0.13.1")
addSbtPlugin     ("com.github.sbt"      % "sbt-less"                 % "2.0.1")
addSbtPlugin     ("com.typesafe.sbt"    % "sbt-uglify"               % "2.0.0")
//addSbtPlugin     ("net.virtual-void"    % "sbt-dependency-graph"     % "0.9.2")
//addSbtPlugin     ("com.dwijnand"        % "sbt-project-graph"        % "0.4.0")
addSbtPlugin     ("com.codecommit"      % "sbt-github-packages"      % "0.5.3")
addCompilerPlugin("org.scalamacros"     % "paradise"                 % "2.1.1" cross CrossVersion.full)
addSbtPlugin     ("io.github.cquiroz"   % "sbt-tzdb"                 % "4.3.0")
//addSbtPlugin     ("io.github.cquiroz"   % "sbt-locales"              % "2.0.1")
addSbtPlugin     ("com.scalapenos"      % "sbt-prompt"               % "2.0.0")

libraryDependencies += "org.scala-js"              %% "scalajs-env-jsdom-nodejs" % "1.1.1" // 1.1.0 "drops support for jsdom 9.x"
libraryDependencies += "io.github.java-diff-utils"  % "java-diff-utils"          % "4.16"

// Apparently needed for sbt-web
resolvers += Resolver.typesafeRepo("releases")
resolvers += Resolver.sonatypeCentralSnapshots
