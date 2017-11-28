import sbt.url

sonatypeProfileName := "com.paddypowerbetfair"

// To sync with Maven central, you need to supply the following information:
publishMavenStyle := true

licenses := Seq("PPB" -> url("https://github.com/PaddyPowerBetfair/Standards/blob/master/LICENCE.md"))
homepage := Some(url("https://github.com/PaddyPowerBetfair/rabbitmq-client"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/PaddyPowerBetfair/rabbitmq-client"),
    "scm:git@github.com:PaddyPowerBetfair/rabbitmq-client.git"
  ))

developers := List(
  Developer(
    id="rodoherty1",
    name="Paddy Power Betfair",
    email="opensource@paddypowerbetfair.com",
    url=url("https://www.paddypowerbetfair.com")
  ))

