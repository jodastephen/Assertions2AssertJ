package com.chainstaysoftware.testing

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiStatement

/**
 * Handler interface for conversion to AssertJ Assertions.
 */
interface AssertHandler {
   /**
    * Handler should return true if the Handler can convert the passed in {@link PsiElement}
    */
   fun canHandle(psiElement: PsiElement): Boolean

   /**
    * Converts the {@link PsiElement}. Will only be called if canHandle return true.
    * Implementers should return a list of static imports. The Pair that contains a single
    * import should contain the full package including the class name in the first element.
    * The second element of the pair should include the method name to import.
    */
   fun handle(psiFile: PsiFile, psiElement: PsiElement): Set<Pair<String, String>>

   fun handleFile(psiFile: PsiFile) : Unit {
   }

   /**
    * Returns the static imports required for the passed in AssertJ assert statement.
    */
   fun getStaticImports(newExpression: PsiStatement): Set<Pair<String, String>> {
      val imports = HashSet<Pair<String, String>>()
      imports.add(Pair("org.assertj.core.api.Assertions", "assertThat"))

      if (newExpression.text.contains("offset("))
         imports.add(Pair("org.assertj.core.data.Offset", "offset"))

      if (newExpression.text.contains("fail("))
         imports.add(Pair("org.assertj.core.api.Assertions", "fail"))

      if (newExpression.text.contains("assertThatExceptionOfType("))
         imports.add(Pair("org.assertj.core.api.Assertions", "assertThatExceptionOfType"))

      if (newExpression.text.contains("assertThatIllegalArgumentException("))
         imports.add(Pair("org.assertj.core.api.Assertions", "assertThatIllegalArgumentException"))

      return imports
   }
}