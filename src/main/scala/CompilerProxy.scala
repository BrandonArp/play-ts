package com.arpnetworking.typescript

import sbt._
import com.mangofactory.typescript._
import play.PlayExceptions
import PlayExceptions.AssetCompilationException

class CompilerProxy {

  lazy val compiler = {
    val compiler = new TypescriptCompiler()
    compiler.setEcmaScriptVersion(EcmaScriptVersion.ES5)

    compiler
  }

  def compile(source: File, options: Seq[String]): (String, Option[String], Seq[File]) = {
    val basePath = source.getParentFile

    val context = CompilationContextRegistry.getNew(basePath)
    context.setThrowExceptionOnCompilationFailure(false)

    val output = compiler.compile(source, context)
    val problems = context.getProblems()

    if (!problems.isEmpty()) {
      val problem = problems.get(0)
      throw AssetCompilationException(Some(source), problem.getMessage, Some(problem.getLine - 1), Some(problem.getColumn - 1))
    }

    (output, Some("MINIFIED FILE"), Nil)
  }
}
