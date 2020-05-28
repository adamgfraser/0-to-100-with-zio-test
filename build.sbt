name := "0-to-100-with-zio-test"

Global / onChangedBuildSource := ReloadOnSourceChanges

val zioVersion = "1.0.0-RC19-2"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"               % zioVersion,
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.1.4",
  "com.softwaremill.sttp.client" %% "circe" % "2.1.4",
  "io.circe" %% "circe-generic" % "0.12.3",
  "dev.zio" %% "zio-test"          % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt"      % zioVersion % "test",
  "dev.zio" %% "zio-test-magnolia" % zioVersion % "test"
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")