import net.matthewrennie.sbt.autoprefixer.Import.AutoprefixerKeys

lazy val root = (project in file(".")).enablePlugins(SbtWeb)

JsEngineKeys.engineType := JsEngineKeys.EngineType.Node

AutoprefixerKeys.sourceMap := true

pipelineStages := Seq(autoprefixer)

val checkCSSFileContents = taskKey[Unit]("check that css contents are correct")

checkCSSFileContents := {
  val contents = IO.read(file("target/web/stage/css/test.css"))
  if (!contents.contains("-webkit-transition")) {
    sys.error(s"Unexpected contents: $contents")
  }
}