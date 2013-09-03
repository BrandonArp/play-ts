package com.arpnetworking.typescript

import sbt._
import sbt.Keys._
import com.mangofactory.typescript._
import java.net.URLClassLoader
import play.Project
import sbt.classpath.SelfFirstLoader
import sbt.PlayExceptions.AssetCompilationException

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

    resourceGenerators in Compile <+= typescript in TypeScript,
    libraryDependencies ++= Seq("com.mangofactory" % "typescript4j" % "0.4.0-SNAPSHOT")
  ))

  def ListDeps = libraryDependencies.in(Compile)

  def Ver = {
    "6.0"
  }

  def CompileTSFile(source: File, options: Seq[String]): (String, Option[String], Seq[File]) = {

    val origin = Path(source).absolutePath

    val klass = classOf[TypescriptCompiler]
    val origClassLoader = klass.getClassLoader()
    val toReplace = findJarsToReplace(origClassLoader)


    val newLoader = new SelfFirstLoader(toReplace.orNull, origClassLoader)

    findJarsToReplace(newLoader )

    val newCompilerClass = newLoader.loadClass(klass.getCanonicalName(), true)

    val compiler = newCompilerClass.newInstance()
    val ecmaVersion = newLoader.loadClass(classOf[EcmaScriptVersion].getCanonicalName)
    val setEcmaVersionMethod = compiler.getClass.getMethod("setEcmaScriptVersion", ecmaVersion)
    val versions = ecmaVersion.getEnumConstants
    setEcmaVersionMethod.invoke(compiler, versions(1).asInstanceOf[AnyRef])

    val contextRegistryClass = newLoader.loadClass(classOf[CompilationContextRegistry].getCanonicalName)
    val contextFactoryMethod = contextRegistryClass.getMethod("getNew", classOf[sbt.File])

    val contextClass = newLoader.loadClass(classOf[CompilationContext].getCanonicalName)
    val setThrowsMethod = contextClass.getMethod("setThrowExceptionOnCompilationFailure", classOf[Boolean])


    val context = contextFactoryMethod.invoke(null, source)
    setThrowsMethod.invoke(context, Boolean.box(false))
    val compileMethod = newCompilerClass.getMethod("compile", classOf[File], contextClass)
    val output = compileMethod.invoke(compiler, source, context).asInstanceOf[String]
    val getProblemsMethod = contextClass.getMethod("getProblems")
    val problemClass = newLoader.loadClass(classOf[TypescriptCompilationProblem].getCanonicalName)
    val problems = getProblemsMethod.invoke(context).asInstanceOf[java.util.List[_]]
    val problemToString = problemClass.getMethod("toString")
    val problemGetLine = problemClass.getMethod("getLine")
    val problemGetColumn = problemClass.getMethod("getColumn")
    val problemGetMessage = problemClass.getMethod("getMessage")
    var problem = problems.toArray.head
    var problemMessage = problemGetMessage.invoke(problem).asInstanceOf[String]
    var problemLine = problemGetLine.invoke(problem).asInstanceOf[Int]
    var problemColumn = problemGetColumn.invoke(problem).asInstanceOf[Int]

    if (problems.size() > 0) {
      throw AssetCompilationException(Some(source), problemMessage, Some(problemLine - 1), Some(problemColumn - 1))
    }

    (output, Some("MINIFIED FILE"), Nil)
  }

  def findJarsToReplace(classLoader :ClassLoader) :Option[Seq[URL]] = {
    if (classLoader == null) {
      return Option.empty
    }
//    println("Classloader: " + classLoader)
    val urls:List[URL] = classLoader match {
      case ul:URLClassLoader => ul.getURLs().toList
      case _ => List.empty
    }
    val ret = Option.apply(urls.filter(f=>{f.toString.contains("org.mozilla/rhino") || f.toString.contains("com.mangofactory")}))
 //   println("urls = " + urls.mkString(" "))
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
