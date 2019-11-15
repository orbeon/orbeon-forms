//addSbtPlugin     ("com.dwijnand"        % "sbt-compat"               % "1.2.6")
addSbtPlugin     ("org.portable-scala"  % "sbt-scalajs-crossproject" % "0.6.1")
addSbtPlugin     ("org.scala-js"        % "sbt-scalajs"              % "0.6.33")
addSbtPlugin     ("com.eed3si9n"        % "sbt-buildinfo"            % "0.9.0")
addSbtPlugin     ("com.typesafe.sbt"    % "sbt-less"                 % "1.1.2")
addSbtPlugin     ("com.typesafe.sbt"    % "sbt-uglify"               % "2.0.0")
//addSbtPlugin     ("net.virtual-void"    % "sbt-dependency-graph"     % "0.9.2")
addCompilerPlugin("org.scalamacros"     % "paradise"                 % "2.1.1" cross CrossVersion.full)

// Apparently needed for sbt-web
resolvers += Resolver.typesafeRepo("releases")
resolvers += Resolver.sonatypeRepo("releases")

// For artifacts published locally
// https://github.com/sbt/sbt-uglify/pull/23
resolvers += Resolver.file("ivy-local", file("ivy-local"))(Resolver.ivyStylePatterns)

// Needed for sbt-cross
//addSbtPlugin     ("org.scala-native"    % "sbt-cross"            % "0.1.0-SNAPSHOT")
//addSbtPlugin     ("org.scala-native"    % "sbt-scalajs-cross"    % "0.1.0-SNAPSHOT")
//resolvers += Resolver.sonatypeRepo("snapshots")
