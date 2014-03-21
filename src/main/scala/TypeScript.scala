package com.arpnetworking.typescript

import sbt._
import sbt.Keys._
import com.mangofactory.typescript._
import java.net.URLClassLoader
import play.{PlayExceptions, Project}
import sbt.classpath.SelfFirstLoader
import play.PlayExceptions.AssetCompilationException
import java.lang.reflect.InvocationTargetException

object TypeScriptPlugin extends sbt.Plugin {
  val TypeScript = config("typescript")
  val typescript = TaskKey[Seq[File]]("typescript", "Compiles typescript to javascript")
  val ldeps = TaskKey[Unit]("ldeps", "Lists the dependencies for debugging")
  val ver = TaskKey[String]("ver", "Prints the version of the typescript plugin")
  val typescriptEntryPoints = SettingKey[PathFinder]("typescript-entry-points")
  val typescriptCompilerOptions = SettingKey[Seq[String]]("typescript-compiler-options")
  
  val typescriptSettings: Seq[Setting[_]] = inConfig(TypeScript)(Seq[Setting[_]](
    typescriptEntryPoints <<= (sourceDirectory in Compile)(base => ((base / "assets" ** "*.ts") --- (base / "assets" ** "_*") --- (base / "assets" ** "*.d.ts"))),
    typescriptCompilerOptions := Seq.empty[String],
    typescript <<= TypeScriptCompiler,
    ldeps := ListDeps,
    ver := Ver,
    resourceGenerators in Compile <+= typescript in TypeScript
  ))

  def ListDeps = libraryDependencies.in(Compile)

  def Ver = {
    "18.0"
  }

  lazy val compilerProxy = {
    val resourceUrl = getClass().getResource("").toString
    val filteredUrl = resourceUrl.substring(resourceUrl.indexOf("file:"), resourceUrl.lastIndexOf("!"))

    val klass = classOf[TypescriptCompiler]
    val origClassLoader = klass.getClassLoader()
    val existingJars = findJarsToReplace(origClassLoader).getOrElse(Seq())
    val toReplace = existingJars.union(Seq[URL](new URL(filteredUrl)))

    val newLoader = new SelfFirstLoader(toReplace, origClassLoader)

    val newCompilerClass = newLoader.loadClass(classOf[CompilerProxy].getCanonicalName(), true)

    newCompilerClass.newInstance()
  }

  def CompileTSFile(source: File, options: Seq[String]): (String, Option[String], Seq[File]) = {
    val compilerClass = compilerProxy.getClass

    val compileMethod = compilerClass.getMethod("compile", classOf[File], classOf[Seq[String]])

    try {
      compileMethod.invoke(compilerProxy, source, options).asInstanceOf[(String, Option[String], Seq[File])]
    } catch {
      case ite: InvocationTargetException => throw ite.getTargetException()
    }
  }

  def findJarsToReplace(classLoader :ClassLoader) :Option[Seq[URL]] = {
    if (classLoader == null) {
      return Option.empty
    }
    val urls:List[URL] = classLoader match {
      case ul:URLClassLoader => ul.getURLs().toList
      case _ => List.empty
    }
    val ret = Option.apply(urls.filter(f=>{f.toString.contains("org.mozilla/rhino") || f.toString.contains("com.mangofactory")}))
    return ret orElse findJarsToReplace(classLoader.getParent)
  }

  def TypeScriptCompiler = Project.AssetsCompiler("typescript",
    (_ ** "*.ts"),
    typescriptEntryPoints in TypeScript,
    { (name, min) => name.replace(".ts", if (min) ".min.js" else ".js") },
    { (tsFile: File, options) => CompileTSFile(tsFile, options) },
    typescriptCompilerOptions in TypeScript
  )

}
