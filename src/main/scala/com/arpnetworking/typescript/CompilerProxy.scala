package com.arpnetworking.typescript

import sbt.File
import com.mangofactory.typescript._
import play.PlayExceptions
import PlayExceptions.AssetCompilationException
import scala.sys.process._
import play.core.jscompile.JavascriptCompiler.CompilationException
import scala.util.control.Exception.catching

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

      val minified = catching(classOf[CompilationException]).opt(play.core.jscompile.JavascriptCompiler.minify(output, Some(source.getName)))
      (output, minified, Seq(source))
    } else {
      val tempFile = java.io.File.createTempFile("tsccomp", null)
      var logger = "\n"
      val cProcess = "tsc --out ".concat(tempFile.getAbsolutePath).concat(" ").concat(source.getAbsolutePath).run(ProcessLogger(line => logger = logger.concat(line).concat("\n")))
      val cOut = cProcess.exitValue()

      val absPath = source.getAbsolutePath
      if (cOut != 0) {
        var start = logger.indexOf(absPath)
        while (start >= 0) {
          val startLoc = start + absPath.length
          System.out.println("startLoc: " + startLoc)
          if (logger.substring(startLoc, startLoc + 1).equals("(")) {
            //We might have found the first (
            val stop = logger.indexOf(")", startLoc)
            if (stop > 0) {
              val spread = logger.substring(startLoc + 1, stop)
              System.out.println("spread: " + spread)
              val split = spread.split(",")
              val (row, col) = (split(0).toInt - 1, split(1).toInt - 1)
              throw AssetCompilationException(Some(source), logger, Some(row), Some(col))
            }
          }
          start = logger.indexOf(source.getAbsolutePath, start + 1)
        }
      }
      val fileSource = scala.io.Source.fromFile(tempFile.getAbsolutePath)
      val compiledCode = fileSource.mkString
      fileSource.close()
      catching(classOf[Exception]).opt(tempFile.delete())
      val minified = catching(classOf[CompilationException]).opt(play.core.jscompile.JavascriptCompiler.minify(compiledCode, Some(source.getName)))
      (compiledCode, minified, Seq(source))
    }
  }
}
