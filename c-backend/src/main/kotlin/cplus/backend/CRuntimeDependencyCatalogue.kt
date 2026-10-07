package cplus.backend

object CRuntimeDependencyCatalogue {
    fun collect(unit: CTranslationUnit): List<String> = buildList {
        if (unit.requiresStringTemplateRuntime) add("__cplus_format")
    }.distinct().sorted()
}
