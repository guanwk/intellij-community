package com.intellij.tools.launch.impl

import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.tools.launch.ModulesProvider
import com.intellij.tools.launch.PathsProvider
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.io.File
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.util.*

class ClassPathBuilder(private val paths: PathsProvider, private val modules: ModulesProvider) {

  companion object {
    fun createClassPathArgFile(paths: PathsProvider, classpath: List<String>): File {
      val launcherFolder = paths.launcherFolder
      if (!launcherFolder.exists()) {
        launcherFolder.mkdirs()
      }

      val classPathArgFile = launcherFolder.resolve("RiderLauncher_${UUID.randomUUID()}.classpath")
      CommandLineWrapperUtil.writeArgumentsFile(classPathArgFile, listOf("-classpath", classpath.distinct().joinToString(File.pathSeparator)), Charsets.UTF_8)
      return classPathArgFile
    }
  }

  private val model = JpsElementFactory.getInstance().createModel() ?: throw Exception("Couldn't create JpsModel")

  fun build(): File {
    val pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)

    val m2HomePath = File(SystemProperties.getUserHome())
      .resolve(".m2")
      .resolve("repository")
    pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", m2HomePath.canonicalPath)

    val kotlinPath = paths.communityRootFolder
      .resolve("build")
      .resolve("dependencies")
      .resolve("build")
      .resolve("kotlin")
      .resolve("Kotlin")
      .resolve("kotlinc")
    pathVariablesConfiguration.addPathVariable("KOTLIN_BUNDLED", kotlinPath.canonicalPath)

    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    JpsProjectLoader.loadProject(model.project, pathVariables, paths.projectRootFolder.canonicalPath)

    val productionOutput = paths.outputRootFolder.resolve("production")
    if (!productionOutput.isDirectory) {
      error("Production classes output directory is missing: $productionOutput")
    }

    JpsJavaExtensionService.getInstance().getProjectExtension(model.project)!!.outputUrl =
      "file://${FileUtil.toSystemIndependentName(paths.outputRootFolder.path)}"

    val modulesList = arrayListOf<String>()
    modulesList.add("intellij.platform.boot")
    modulesList.add(modules.mainModule)
    modulesList.addAll(modules.additionalModules)
    modulesList.add("intellij.configurationScript")

    return createClassPathArgFileForModules(modulesList)
  }

  private fun createClassPathArgFileForModules(modulesList: List<String>): File {
    val classpath = mutableListOf<String>()
    for (moduleName in modulesList) {
      val module = model.project.modules.singleOrNull { it.name == moduleName }
                   ?: throw Exception("Module $moduleName not found")
      if (isModuleExcluded(module)) continue

      classpath.addAll(getClasspathForModule(module))
    }

    // Uncomment for big debug output
    //println("Created classpath:")
    //for (path in classpath.distinct().sorted()) {
    //  println("  $path")
    //}
    //println("-- END")

    return createClassPathArgFile(paths, classpath)
  }

  private fun getClasspathForModule(module: JpsModule): List<String> {
    return JpsJavaExtensionService
      .dependencies(module)
      .recursively()
      .satisfying { if (it is JpsModuleDependency) !isModuleExcluded(it.module) else true }
      .includedIn(JpsJavaClasspathKind.runtime(modules.includeTestDependencies))
      .classes().roots.filter { it.exists() }.map { it.path }.toList()
  }

  private fun isModuleExcluded(module: JpsModule?): Boolean {
    if (module == null) return true
    return modules.excludedModules.contains(module.name)
  }
}