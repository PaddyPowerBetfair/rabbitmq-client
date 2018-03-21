import sbt.url

sonatypeProfileName := "com.paddypowerbetfair"

// To sync with Maven central, you need to supply the following information:
publishMavenStyle := true

licenses := Seq("PPB" -> url("https://github.com/PaddyPowerBetfair/Standards/blob/master/LICENCE.md"))

homepage := Some(url("https://github.com/PaddyPowerBetfair/rabbitmq-client"))

description := "rabbitmq-client is an Akka actor-based wrapper for the standard java RabbitMQ API"

scmInfo := Some(
  ScmInfo(
    browseUrl = url("https://github.com/PaddyPowerBetfair/rabbitmq-client"),
    connection = "scm:git:ssh://github.com:PaddyPowerBetfair/rabbitmq-client.git",
    devConnection = "scm:git:ssh://github.com:PaddyPowerBetfair/rabbitmq-client.git"
  ))

developers := List(
  Developer(
    id="rodoherty1",
    name="Rob O'Doherty",
    email="opensource@paddypowerbetfair.com",
    url=url("https://www.paddypowerbetfair.com")
  ))
