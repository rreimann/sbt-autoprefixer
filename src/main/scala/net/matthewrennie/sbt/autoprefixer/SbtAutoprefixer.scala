package net.matthewrennie.sbt.autoprefixer

import sbt._
import sbt.Keys._
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.jse.{SbtJsEngine, SbtJsTask}

object Import {

  val autoprefixer = TaskKey[Pipeline.Stage]("autoprefixer", "Parse CSS and adds vendor prefixes to rules by Can I Use")

  object AutoprefixerKeys {
    val buildDir = SettingKey[File]("autoprefixer-build-dir", "Where autoprefixer will read from.")
    val browsers = SettingKey[String]("autoprefixer-browsers", "Which browsers autoprefixer will support.")
  }

}

object SbtAutoprefixer extends AutoPlugin {

  override def requires = SbtJsTask

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtWeb.autoImport._
  import WebKeys._
  import SbtJsEngine.autoImport.JsEngineKeys._
  import SbtJsTask.autoImport.JsTaskKeys._
  import autoImport._
  import AutoprefixerKeys._

  override def projectSettings = Seq(
    buildDir := (resourceManaged in autoprefixer).value / "build",
    excludeFilter in autoprefixer := HiddenFileFilter,
    includeFilter in autoprefixer := GlobFilter("*.css"),
    resourceManaged in autoprefixer := webTarget.value / autoprefixer.key.label,
    browsers := "",
    autoprefixer := runAutoprefixer.dependsOn(WebKeys.nodeModules in Assets).value
  )

  private def runAutoprefixer: Def.Initialize[Task[Pipeline.Stage]] = Def.task {
    mappings =>

      val include = (includeFilter in autoprefixer).value
      val exclude = (excludeFilter in autoprefixer).value
      val autoprefixerMappings = mappings.filter(f => !f._1.isDirectory && include.accept(f._1) && !exclude.accept(f._1))

      SbtWeb.syncMappings(
        streams.value.cacheDirectory,
        autoprefixerMappings,
        buildDir.value
      )

      val buildMappings = autoprefixerMappings.map(o => buildDir.value / o._2)

      val cacheDirectory = streams.value.cacheDirectory / autoprefixer.key.label
      val runUpdate = FileFunction.cached(cacheDirectory, FilesInfo.hash) {
        inputFiles =>
          streams.value.log.info("Autoprefixing CSS")

          val inputFileArgs = inputFiles.map(_.getPath)

          val useAutoprefixerArg = Seq("--use", "autoprefixer", "--replace")

          val browsersArg = if (browsers.value.length > 0) Seq("--autoprefixer.browsers", browsers.value) else Nil

          val allArgs = Seq() ++
            useAutoprefixerArg ++
            browsersArg ++
            inputFileArgs

          SbtJsTask.executeJs(
            state.value,
            (engineType in autoprefixer).value,
            (command in autoprefixer).value,
            (nodeModuleDirectories in Assets).value.map(_.getPath),            
            (nodeModuleDirectories in Assets).value.last / "postcss-cli" / "bin" / "postcss",
            allArgs,
            (timeoutPerSource in autoprefixer).value * autoprefixerMappings.size
          )

          buildDir.value.***.get.filter(!_.isDirectory).toSet
      }

      val autoPrefixedMappings = runUpdate(buildMappings.toSet).pair(relativeTo(buildDir.value))
      (mappings.toSet -- autoprefixerMappings ++ autoPrefixedMappings).toSeq
  }

}