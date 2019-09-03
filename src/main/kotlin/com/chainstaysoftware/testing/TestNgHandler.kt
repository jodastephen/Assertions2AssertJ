package com.chainstaysoftware.testing

import com.chainstaysoftware.testing.Util.isQualifiedClass
import com.google.common.collect.ImmutableSet
import com.google.common.io.Files
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.JavaPsiFacade
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Handler to convert TestNG Assertions to AssertJ Assertions.
 */
class TestNgHandler : AssertHandler {
   private val refactorMap: Map<String, (Array<PsiExpression>) -> String?>
      = mapOf("assertEquals" to { expressions -> refactorAssertEquals(expressions) },
      "assertNotEquals" to { expressions -> refactorAssertNotEquals(expressions) },
      "assertSame" to { expressions -> refactorAssertSameAs(expressions) },
      "assertNotSame" to { expressions -> refactorAssertNotSameAs(expressions) },
      "assertArrayEquals" to { expressions -> refactorAssertArrayEquals(expressions) },
      "assertIterableEquals" to { expressions -> refactorAssertIterableEquals(expressions) },
      "assertLinesMatch" to { expressions -> refactorAssertIterableEquals(expressions) },
      "assertTrue" to { expressions -> refactorAssertTrue(expressions) },
      "assertFalse" to { expressions -> refactorAssertFalse(expressions) },
      "assertNull" to { expressions -> refactorAssertNull(expressions) },
      "assertNotNull" to { expressions -> refactorAssertNotNull(expressions) },
      "assertThrows" to { expressions -> refactorAssertThrows(expressions) },
      "fail" to { expressions -> refactorFail(expressions) })

    // could handle org.testng.AssertJUnit, but would really need a different handler class
   override fun canHandle(psiElement: PsiElement): Boolean =
       isQualifiedClass(psiElement, "org.assertj.core.api.Assertions") ||
       (isQualifiedClass(psiElement, "org.testng.Assert") &&
          "assertThat" != Util.getMethodName(psiElement))


   override fun handle(psiFile: PsiFile, psiElement: PsiElement): Set<Pair<String, String>> {
      try {
         Util.log(psiElement.text)
         return psiElement.children.map { child -> refactorTestNg(psiFile.project, psiElement, child) }
                 .flatten()
                 .toSet()
      } catch (ex: RuntimeException) {
         Util.log(ex)
         return ImmutableSet.of()
      }
   }

   override fun handleFile(psiFile: PsiFile) {
      try {
         Util.log(psiFile.name)
         handleFile0(psiFile)
      } catch (ex: RuntimeException) {
         Util.log(ex)
      }
   }

   private fun handleFile0(psiFile: PsiFile) {
      psiFile.children
              .filterIsInstance<PsiClass>()
              .filter { it.isWritable }
              .forEach {
                 var classAnno = it.modifierList?.findAnnotation("org.testng.annotations.Test")
                 if (classAnno == null) {
                    classAnno = it.modifierList?.findAnnotation("org.junit.jupiter.api.Test")
                 }
                 if (classAnno == null) {
                    classAnno = it.modifierList?.findAnnotation("Test")
                 }
                 if (classAnno?.findAttributeValue("enabled") != null) {
                    val desc = classAnno.findAttributeValue("description")
                    val disAnno = it.modifierList?.addAnnotation("Disabled")
                    if (desc != null) {
                       val anno = JavaPsiFacade.getInstance(psiFile.project)
                               .elementFactory
                               .createAnnotationFromText("@Disabled(${desc.text})", psiFile.context)
                       disAnno?.setDeclaredAttributeValue("value", anno.findAttributeValue("value"))
                    }
                    Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.api.Disabled")
                 }
                 classAnno?.delete()
                 it.methods
                         .forEach { m ->
                            // single use of list because PSI is weird

                            // match full and short names to ensure match found
                            var testAnno = m.modifierList.findAnnotation("org.testng.annotations.Test")
                            if (testAnno == null) {
                               testAnno = m.modifierList.findAnnotation("org.junit.jupiter.api.Test")
                            }
                            if (testAnno == null) {
                               testAnno = m.modifierList.findAnnotation("Test")
                            }

                            // match full and short names to ensure match found
                            var dpAnno = m.modifierList.findAnnotation("org.testng.annotations.DataProvider")
                            if (dpAnno == null) {
                               dpAnno = m.modifierList.findAnnotation("DataProvider")
                            }

                            if (dpAnno != null) {
                               dpAnno.delete()
                            } else {
                               // add annotation to public static test methods
                               // match full and short names to ensure match found
                               if (m.hasModifier(JvmModifier.PUBLIC) &&
                                       !m.hasModifier(JvmModifier.STATIC) &&
                                       testAnno == null &&
                                       m.modifierList.findAnnotation("org.junit.jupiter.params.ParameterizedTest") == null &&
                                       m.modifierList.findAnnotation("org.junit.jupiter.api.Disabled") == null &&
                                       m.modifierList.findAnnotation("org.junit.jupiter.api.BeforeAll") == null &&
                                       m.modifierList.findAnnotation("org.junit.jupiter.api.AfterAll") == null &&
                                       m.modifierList.findAnnotation("org.junit.jupiter.api.BeforeEach") == null &&
                                       m.modifierList.findAnnotation("org.junit.jupiter.api.AfterEach") == null &&
                                       m.modifierList.findAnnotation("org.testng.annotations.BeforeTest") == null &&
                                       m.modifierList.findAnnotation("org.testng.annotations.AfterTest") == null &&
                                       m.modifierList.findAnnotation("org.testng.annotations.BeforeClass") == null &&
                                       m.modifierList.findAnnotation("org.testng.annotations.AfterClass") == null &&
                                       m.modifierList.findAnnotation("org.testng.annotations.BeforeMethod") == null &&
                                       m.modifierList.findAnnotation("org.testng.annotations.AfterMethod") == null) {
                                  if (m.parameterList.parametersCount == 0) {
                                     m.modifierList.addAnnotation("Test")
                                  }
                               } else if (m.modifierList.findAnnotation("org.testng.annotations.BeforeTest") != null) {
                                  m.modifierList.findAnnotation("org.testng.annotations.BeforeTest")?.delete()
                                  m.modifierList.addAnnotation("BeforeAll")
                                  Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.api.BeforeAll")
                               } else if (m.modifierList.findAnnotation("org.testng.annotations.AfterTest") != null) {
                                  m.modifierList.findAnnotation("org.testng.annotations.AfterTest")?.delete()
                                  m.modifierList.addAnnotation("AfterAll")
                                  Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.api.AfterAll")
                               } else if (m.modifierList.findAnnotation("org.testng.annotations.BeforeClass") != null) {
                                  m.modifierList.findAnnotation("org.testng.annotations.BeforeClass")?.delete()
                                  m.modifierList.addAnnotation("BeforeAll")
                                  Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.api.BeforeAll")
                               } else if (m.modifierList.findAnnotation("org.testng.annotations.AfterClass") != null) {
                                  m.modifierList.findAnnotation("org.testng.annotations.AfterClass")?.delete()
                                  m.modifierList.addAnnotation("AfterAll")
                                  Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.api.AfterAll")
                               } else if (m.modifierList.findAnnotation("org.testng.annotations.BeforeMethod") != null) {
                                  m.modifierList.findAnnotation("org.testng.annotations.BeforeMethod")?.delete()
                                  m.modifierList.addAnnotation("BeforeEach")
                                  Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.api.BeforeEach")
                               } else if (m.modifierList.findAnnotation("org.testng.annotations.AfterMethod") != null) {
                                  m.modifierList.findAnnotation("org.testng.annotations.AfterMethod")?.delete()
                                  m.modifierList.addAnnotation("AfterEach")
                                  Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.api.AfterEach")
                               } else if (m.modifierList.findAnnotation("ParameterizedTest") == null &&
                                       m.modifierList.findAnnotation("org.junit.jupiter.params.ParameterizedTest") == null) {
                                  // look for dataProvider annotations
                                  if (testAnno != null) {
                                     val data = testAnno.findAttributeValue("dataProvider")
                                     if (data != null) {
                                        val ptest = m.modifierList.addAnnotation("ParameterizedTest")
                                        val dataStr = StringUtil.unquoteString(data.text)
                                        val source = JavaPsiFacade.getInstance(psiFile.project)
                                                .elementFactory
                                                .createAnnotationFromText("@MethodSource(\"data_$dataStr\")", psiFile.context)
                                        m.modifierList.addAfter(source, ptest)
                                        testAnno.delete()
                                        Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.params.ParameterizedTest")
                                        Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.params.provider.MethodSource")
                                     }
                                  }
                               } else if (testAnno?.findAttributeValue("enabled") != null) {
                                  m.modifierList.addAnnotation("Disabled")
                                  testAnno.delete()
                                  Util.addImport(psiFile.project, psiFile,"org.junit.jupiter.api.Disabled")
                               }
                            }
                         }
              }
   }

   private fun refactorTestNg(project: Project,
                              testngAssertElement: PsiElement,
                              childElement: PsiElement?): Set<Pair<String, String>> {
      val emptyImports = hashSetOf<Pair<String, String>>()
      val istestng = Util.getClassName(testngAssertElement) == "Assert" || Util.getClassName(testngAssertElement) == "AssertJUnit"
      if (istestng && childElement is PsiExpressionList) {
         val methodName = Util.getMethodName(testngAssertElement) ?: return emptyImports

         val expressions = childElement.expressions
         val newExpressionStr = refactorMap.getOrDefault(methodName, { _ -> null })
                 .invoke(expressions) ?: return emptyImports

         val elementFactory = JavaPsiFacade.getElementFactory(project)
         val newExpression = elementFactory
                 .createStatementFromText(newExpressionStr, null)
         testngAssertElement.replace(newExpression)
         return getStaticImports(newExpression)
      } else {
         return emptyImports
      }
   }

   private fun refactorAssertEquals(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 4 -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            val delta = expressions[2].text
            val desc = expressions[3].text
            assertStr(actual, "isCloseTo(${expected.trim()}, offset(${delta.trim()}))", desc)
         }
         expressions.size == 3 -> {
            if ("PsiType:String" == expressions[2].type.toString()) {
               val actual = expressions[0].text
               val expected = expressions[1].text
               val desc = expressions[2].text
               assertStr(actual, "isEqualTo(" + expected.trim() + ")", desc)
            } else {
               val actual = expressions[0].text
               val expected = expressions[1].text
               val delta = expressions[2].text
               assertStr(actual, "isCloseTo(${expected.trim()}, offset(${delta.trim()}))")
            }
         }
         else -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            when (expected) {
               "true" -> {
                  if (actual.endsWith(".isEmpty()")) {
                     assertStr(actual.substringBeforeLast(".isEmpty()"), "isEmpty()")
                  } else if (actual.endsWith(".isPresent()")) {
                     assertStr(actual.substringBeforeLast(".isPresent()"), "isPresent()")
                  } else {
                     assertStr(actual, "isTrue()")
                  }
               }
               "false" -> {
                  if (actual.endsWith(".isEmpty()")) {
                     assertStr(actual.substringBeforeLast(".isEmpty()"), "isNotEmpty()")
                  } else if (actual.endsWith(".isPresent()")) {
                     assertStr(actual.substringBeforeLast(".isPresent()"), "isNotPresent()")
                  } else {
                     assertStr(actual, "isFalse()")
                  }
               }
               "null" -> assertStr(actual, "isNull()")
               "\"\"" -> assertStr(actual, "isEmpty()")
               "ImmutableList.of()" -> assertStr(actual, "isEmpty()")
               "ImmutableSet.of()" -> assertStr(actual, "isEmpty()")
               "ImmutableMap.of()" -> assertStr(actual, "isEmpty()")
               else -> {
                  if (expected.startsWith("ImmutableList.of(") && expected.endsWith(")")) {
                     val expectedContent = expected.substringAfter("ImmutableList.of(").substringBeforeLast(')')
                     assertStr(actual, "containsExactly($expectedContent)")
                  } else if (expected.startsWith("ImmutableSet.of(") && expected.endsWith(")")) {
                     val expectedContent = expected.substringAfter("ImmutableSet.of(").substringBeforeLast(')')
                     assertStr(actual, "containsOnly($expectedContent)")
                  } else if (actual.endsWith(".size()")) {
                     assertStr(actual.substringBeforeLast(".size()"), "hasSize($expected)")
                  } else {
                     assertStr(actual, "isEqualTo($expected)")
                  }
               }
            }
         }
      }
   }

   private fun refactorAssertNotSameAs(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 3 -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            val desc = expressions[2].text
            assertStr(actual, "isNotSameAs(" + expected.trim() + ")", desc)
         }
         else -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            assertStr(actual, "isNotSameAs($expected)")
         }
      }
   }


   private fun refactorAssertSameAs(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 3 -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            val desc = expressions[2].text
            assertStr(actual, "isSameAs(" + expected.trim() + ")", desc)
         }
         else -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            assertStr(actual, "isSameAs($expected)")
         }
      }
   }

   private fun refactorAssertNotEquals(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 3 -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            val desc = expressions[2].text
            assertStr(actual, "isNotEqualTo(" + expected.trim() + ")", desc)
         }
         else -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            assertStr(actual, "isNotEqualTo($expected)")
         }
      }
   }

   private fun refactorAssertArrayEquals(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 4 -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            val delta = expressions[2].text
            val desc = expressions[3].text
            assertStr(actual, "contains(${expected.trim()}, offset(${delta.trim()}))", desc)
         }
         expressions.size == 3 -> {
            if ("PsiType:String" == expressions[2].type.toString()) {
               val actual = expressions[0].text
               val expected = expressions[1].text
               val desc = expressions[2].text
               assertStr(actual, "isEqualTo(" + expected.trim() + ")", desc)
            } else {
               val actual = expressions[0].text
               val expected = expressions[1].text
               val delta = expressions[2].text
               assertStr(actual, "contains(${expected.trim()}, offset(${delta.trim()}))")
            }
         }
         else -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            assertStr(actual, "isEqualTo($expected)")
         }
      }
   }

   private fun refactorAssertIterableEquals(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 3 -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            val desc = expressions[2].text
            assertStr(actual, "isEqualTo(" + expected.trim() + ")", desc)
         }
         else -> {
            val actual = expressions[0].text
            val expected = expressions[1].text
            assertStr(actual, "isEqualTo($expected)")
         }
      }
   }

   private fun refactorAssertTrue(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 2 -> {
            val actual = expressions[0].text
            val desc = expressions[1].text
            assertStr(actual, "isTrue()", desc)
         }
         else -> {
            val actual = expressions[0].text
            assertStr(actual, "isTrue()")
         }
      }
   }

   private fun refactorAssertFalse(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 2 -> {
            val actual = expressions[0].text
            val desc = expressions[1].text
            assertStr(actual, "isFalse()", desc)
         }
         else -> {
            val actual = expressions[0].text
            assertStr(actual, "isFalse()")
         }
      }
   }

   private fun refactorAssertNull(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 2 -> {
            val actual = expressions[0].text
            val desc = expressions[1].text
            assertStr(actual, "isNull()", desc)
         }
         else -> {
            val actual = expressions[0].text
            assertStr(actual, "isNull()")
         }
      }
   }

   private fun refactorAssertNotNull(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 2 -> {
            val actual = expressions[0].text
            val desc = expressions[1].text
            assertStr(actual, "isNotNull()", desc)
         }
         else -> {
            val actual = expressions[0].text
            assertStr(actual, "isNotNull()")
         }
      }
   }

   private fun refactorAssertThrows(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 3 -> {
            val expected = expressions[0].text
            val actual = expressions[1].text
            val desc = expressions[2].text
            "assertThatExceptionOfType(${expected.trim()}).as(${desc.trim()}).isThrownBy(${actual.trim()})"
         }
         else -> {
            val expected = expressions[0].text
            val actual = expressions[1].text
            if (expected == "IllegalArgumentException.class") {
               "assertThatIllegalArgumentException().isThrownBy(${actual.trim()})"
            } else {
               "assertThatExceptionOfType(${expected.trim()}).isThrownBy(${actual.trim()})"
            }
         }
      }
   }

   private fun refactorFail(expressions: Array<PsiExpression>): String {
      return when {
         expressions.size == 2 -> {
            val desc = expressions[0].text
            val cause = expressions[1].text
            "fail($desc, ${cause.trim()})"
         }
         expressions.size == 1 -> "fail(${expressions[0].text.trim()})"
         else -> "fail()"
      }
   }

   private fun assertStr(actual: String,
                         assertExpression: String,
                         description: String? = null) =
      if (description == null)
         "assertThat(${actual.trim()}).$assertExpression"
      else
         "assertThat(${actual.trim()}).as(${description.trim()}).$assertExpression"
}