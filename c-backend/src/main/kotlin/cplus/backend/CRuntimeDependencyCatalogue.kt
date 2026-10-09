package cplus.backend

object CRuntimeDependencyCatalogue {
    fun collect(unit: CTranslationUnit): List<String> = buildList {
        if (unit.requiresStringTemplateRuntime) add("__cplus_format")
        if (unit.functions.any { it.name == "__cplus_test_report_truth" }) add("__cplus_test_report_truth")
        if (unit.functions.any { it.name == "__cplus_test_report_equality" }) add("__cplus_test_report_equality")
    }.distinct().sorted()
}
