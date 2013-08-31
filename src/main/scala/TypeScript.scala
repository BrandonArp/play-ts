package typescript

import sbt._
import sbt.Keys._
import sbt.Project.Initialize
import com.mangofactory.typescript._

object TypeScriptPlugin extends Plugin {
  val TypeScript = config("typescript")
  
  val typescript = TaskKey[Seq[File]]("typescript", "Compiles typescript to javascript")

  val typescriptEntryPoints = SettingKey[PathFinder]("typescript-entry-points")

  val typescriptCompilerOptions = SettingKey[Seq[String]]("typescript-compiler-options")
  
  val typescriptSettings: Seq[sbt.Project.Setting[_]] = inConfig(TypeScript)(Seq(
    typescriptEntryPoints <<= (sourceDirectory in Compile)(base => ((base / "assets" ** "*.ts") --- base / "assets" ** "_*")),
    typescriptCompilerOptions := Seq.empty[String]
  )) ++ Seq( resourceGenerators in Compile <+= TypeScriptCompiler,
    libraryDependencies ++= Seq("com.mangofactory" % "typescript4j" % "0.4.0-SNAPSHOT" ))

  def CompileTSFile(source: File, options: Seq[String]): (String, Option[String], Seq[File]) = {

    val origin = Path(source).absolutePath



    ("COMPILED FILE", Some("MINIFIED FILE"), Nil)

  }

  def TypeScriptCompiler = AssetsCompiler("typescript",
    (_ ** "*.ts"),
    typescriptEntryPoints,
    { (name, min) => name.replace(".ts", if (min) ".min.js" else ".js") },
    { (tsFile: File, options) => CompileTSFile(tsFile, options) },
    typescriptCompilerOptions
  )

  def AssetsCompiler(name: String,
    watch: File => PathFinder,
    filesSetting: sbt.SettingKey[PathFinder],
    naming: (String, Boolean) => String,
    compile: (File, Seq[String]) => (String, Option[String], Seq[File]),
    optionsSettings: sbt.SettingKey[Seq[String]]) =
    (state, sourceDirectory in Compile, resourceManaged in Compile, cacheDirectory, optionsSettings, filesSetting) map { (state, src, resources, cache, options, files) =>

      import java.io._

      val cacheFile = cache / name
      val currentInfos = watch(src).get.map(f => f -> FileInfo.lastModified(f)).toMap

      val (previousRelation, previousInfo) = Sync.readInfo(cacheFile)(FileInfo.lastModified.format)

      if (previousInfo != currentInfos) {

        //a changed file can be either a new file, a deleted file or a modified one
        lazy val changedFiles: Seq[File] = currentInfos.filter(e => !previousInfo.get(e._1).isDefined || previousInfo(e._1).lastModified < e._2.lastModified).map(_._1).toSeq ++ previousInfo.filter(e => !currentInfos.get(e._1).isDefined).map(_._1).toSeq

        //erease dependencies that belong to changed files
        val dependencies = previousRelation.filter((original, compiled) => changedFiles.contains(original))._2s
        dependencies.foreach(IO.delete)

        /**
         * If the given file was changed or
         * if the given file was a dependency,
         * otherwise calculate dependencies based on previous relation graph
         */
        val generated: Seq[(File, java.io.File)] = (files x relativeTo(Seq(src / "assets"))).flatMap {
          case (sourceFile, name) => {
            if (changedFiles.contains(sourceFile) || dependencies.contains(new File(resources, "public/" + naming(name, false)))) {
              val (debug, min, dependencies) = try {
                compile(sourceFile, options)
              } catch {
                case e: TypescriptException => throw e
              }
              val out = new File(resources, "public/" + naming(name, false))
              IO.write(out, debug)
              (dependencies ++ Seq(sourceFile)).toSet[File].map(_ -> out) ++ min.map { minified =>
                val outMin = new File(resources, "public/" + naming(name, true))
                IO.write(outMin, minified)
                (dependencies ++ Seq(sourceFile)).map(_ -> outMin)
              }.getOrElse(Nil)
            } else {
              previousRelation.filter((original, compiled) => original == sourceFile)._2s.map(sourceFile -> _)
            }
          }
        }

        //write object graph to cache file 
        Sync.writeInfo(cacheFile,
          Relation.empty[File, File] ++ generated,
          currentInfos)(FileInfo.lastModified.format)

        // Return new files
        generated.map(_._2).distinct.toList

      } else {
        // Return previously generated files
        previousRelation._2s.toSeq
      }

    }

}
