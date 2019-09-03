package com.chainstaysoftware.testing

import com.google.common.io.Files
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant


/**
 * Handle converting a single Java file from TestNG to JUnit 5 and AssertJ.
 */
class FileHandler {
   private val handlers = listOf(TestNgHandler())

   fun handle(psiFile: PsiFile) {
      Util.log(psiFile.name)
      var codeModified = false
      val imports = mutableSetOf<Pair<String, String>>()

      psiFile.children
         .filterIsInstance<PsiClass>()
         .forEach {

            it.allMethods.forEach { psiMethod ->
               Util.log(psiMethod.name)
               psiMethod.accept(object : PsiRecursiveElementVisitor() {
                  override fun visitElement(psiElement: PsiElement) {
                     val handler = handlers.firstOrNull { handler -> handler.canHandle(psiElement) }
                     when {
                        handler != null -> {
                           val imps = handler.handle(psiFile, psiElement)
                           imports.addAll(imps)
                           codeModified = true
                        }
                        else -> super.visitElement(psiElement)
                     }
                  }
               })
            }
         }

      if (codeModified) {
         imports.forEach { import ->
            Util.addStaticImport(psiFile.project, psiFile, import.first, import.second)
         }
         Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.api.Test")
         Util.removeImportIf(psiFile) {
            it.startsWith("org.testng.Assert")  ||
                    (it.startsWith("org.junit.jupiter.api.Assertions") &&
                            !it.startsWith("org.junit.jupiter.api.Assertions.assertAll")) ||
                    it.startsWith("org.testng.annotations.DataProvider") ||
                    it.startsWith("org.testng.annotations.Test")
         }
         handleFile(psiFile)
//         Util.removeImportIf(psiFile) {
//            it.startsWith("org.testng.annotations")
//         }
      }
   }

   private fun handleFile(psiFile: PsiFile) {
      val handler = TestNgHandler()
      handler.handleFile(psiFile)
   }
}
