name := "Rabbitmq Client Samples"

organization := "com.paddypowerbetfair"

version := "1.0.0"

scalaVersion := "2.12.4"

val typesafeConfigV = "1.3.0"
val akkaV = "2.5.7"
val rabbitMqClientV = "1.0.3"

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/snapshots"

val akka = Seq (
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpV,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV
)

val typesafeConfig = Seq(
  "com.typesafe" % "config" % typesafeConfigV
)

val logging = Seq (
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3"
)

val rabbitMqClient = Seq (
  "com.paddypowerbetfair" %% "rabbitmq-client" % rabbitMqClientV
)
 
//libraryDependencies ++= akka ++ logging ++ scalacheck ++ scalatest ++ scalaz ++ typesafeConfig ++ rabbitMqClient
libraryDependencies ++= akka ++ logging ++ typesafeConfig ++ rabbitMqClient

