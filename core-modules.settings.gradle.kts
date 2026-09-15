// The core modules, shared by clavier-tuner-foss and clavier-tuner-pro.
// Both settings files apply this; a new core module is added here only.
// In foss the modules sit at the root. In pro the whole core is merged
// under core/, recognised by this file being present there.
val coreRoot = java.io.File(settingsDir, "core").takeIf {
    java.io.File(it, "core-modules.settings.gradle.kts").isFile
} ?: settingsDir

for (module in listOf("shared", "ui")) {
    include(":$module")
    project(":$module").projectDir = java.io.File(coreRoot, module)
}
