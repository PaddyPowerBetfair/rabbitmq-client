import com.typesafe.sbt.GitVersioning

organization := "com.paddypowerbetfair"

artifact := Artifact("rabbitmq-client")

name := artifact.value.name

version := "1.0.2"

scalaVersion := "2.12.4"

val scalazV = "7.1.11"
val scalazStreamV = "0.8.6"
val argonautV = "6.2"
val typesafeConfigV = "1.3.0"
val jodatimeV = "2.9.4"
val amqpClientV = "3.5.3"
val scalacheckV = "1.13.5"
val scalatestV = "3.0.4"
val mockitoV = "2.10.0"
val akkaV = "2.5.6"

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addCommandAlias("ci-all",  ";+clean ;+compile ;+test ;+package")
addCommandAlias("release", ";+publishSigned ;sonatypeReleaseAll")

val akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV
)

val typesafeConfig = Seq(
  "com.typesafe" % "config" % typesafeConfigV
)

val scalaz = Seq(
  "org.scalaz" %% "scalaz-core" % scalazV,
  "org.scalaz.stream" %% "scalaz-stream" % scalazStreamV
)

val argonaut = Seq(
  "io.argonaut" %% "argonaut" % argonautV
)

val scalacheck = Seq(
  "org.scalacheck" %% "scalacheck" % scalacheckV
)

val scalatest = Seq(
  "org.scalatest" %% "scalatest" % scalatestV
)

val amqpClient = Seq(
  "com.rabbitmq" % "amqp-client" % amqpClientV
)

val logging = Seq (
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3"
)

val mockito = Seq (
  "org.mockito" % "mockito-core" % mockitoV % "test"
)


libraryDependencies ++= akka ++ logging ++ scalacheck ++ scalatest ++ amqpClient ++ scalaz ++ argonaut ++ typesafeConfig ++ mockito

// For distribution to sonartype

useGpg := false
usePgpKeyHex("4BEF11849D8638711107EB75B76CCB046AAA0BF2")
pgpPublicRing := baseDirectory.value / "project" / ".gnupg" / "pubring.gpg"
pgpSecretRing := baseDirectory.value / "project" / ".gnupg" / "secring.gpg"
pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray)

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  sys.env.getOrElse("SONATYPE_USER", ""),
  sys.env.getOrElse("SONATYPE_PASS", "")
)

isSnapshot := version.value endsWith "SNAPSHOT"

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

enablePlugins(GitVersioning)

/* The BaseVersion setting represents the in-development (upcoming) version,
 * as an alternative to SNAPSHOTS.
 */
git.baseVersion := "3.0.0"

val ReleaseTag = """^v([\d\.]+)$""".r
git.gitTagToVersionNumber := {
  case ReleaseTag(v) => Some(v)
  case _ => None
}

git.formattedShaVersion := {
  val suffix = git.makeUncommittedSignifierSuffix(git.gitUncommittedChanges.value, git.uncommittedSignifier.value)

  git.gitHeadCommit.value map { _.substring(0, 7) } map { sha =>
    git.baseVersion.value + "-" + sha + suffix
  }
}
