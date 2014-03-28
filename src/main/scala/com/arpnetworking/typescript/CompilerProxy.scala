package com.arpnetworking.typescript

import sbt.File
import com.mangofactory.typescript._
import play.PlayExceptions
import PlayExceptions.AssetCompilationException
import scala.sys.process.ProcessLogger
import scala.sys.process._

class CompilerProxy {

  lazy val javaBasedCompiler = {
    val compiler = new TypescriptCompiler()
    compiler.setEcmaScriptVersion(EcmaScriptVersion.ES5)

    compiler
  }

  def compile(source: File, options: Seq[String]): (String, Option[String], Seq[File]) = {
    val basePath = source.getParentFile

    val useTsc = "tsc".run(ProcessLogger(line => ())).exitValue() == 0

    if (!useTsc) {

      val context = CompilationContextRegistry.getNew(basePath)
      context.setThrowExceptionOnCompilationFailure(false)

      val output = javaBasedCompiler.compile(source, context)
      val problems = context.getProblems

      if (!problems.isEmpty) {
        val problem = problems.get(0)
        throw AssetCompilationException(Some(source), problem.getMessage, Some(problem.getLine - 1), Some(problem.getColumn - 1))
      }

      (output, Some("MINIFIED FILE"), Nil)
    } else {
      val tempFile = java.io.File.createTempFile("tsccomp", null)
      var logger = ""
      val cProcess = "tsc --out ".concat(tempFile.getAbsolutePath).concat(" ").concat(source.getAbsolutePath).run(ProcessLogger(line => logger = logger.concat(line)))
      val cOut = cProcess.exitValue()
      if (cOut != 0) {
        throw AssetCompilationException(Some(source), logger, None, None)
      }
      val fileSource = scala.io.Source.fromFile(tempFile.getAbsolutePath)
      val compiledCode = fileSource.mkString
      fileSource.close()
      (compiledCode, Some("MINIFIED FILE"), Nil)
    }
  }
}
