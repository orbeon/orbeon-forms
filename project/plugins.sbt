addSbtPlugin     ("org.scala-js"     % "sbt-scalajs"      % "0.6.13")
addSbtPlugin     ("com.orrsella"     % "sbt-sound"        % "1.0.4")
addSbtPlugin     ("com.eed3si9n"     % "sbt-buildinfo"    % "0.6.1")
addSbtPlugin     ("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.0")
addCompilerPlugin("org.scalamacros"  % "paradise"         % "2.1.0" cross CrossVersion.full)

// Apparently needed for sbt-web
resolvers += Resolver.typesafeRepo("releases")