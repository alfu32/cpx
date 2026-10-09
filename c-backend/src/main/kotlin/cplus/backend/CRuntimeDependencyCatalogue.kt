package cplus.backend

object CRuntimeDependencyCatalogue {
    fun collect(unit: CTranslationUnit): List<String> = buildList {
        if (unit.requiresStringTemplateRuntime) add("__cplus_format")
        if (unit.functions.any { it.name == "__cplus_test_begin" }) {
            add("__cplus_test_begin")
            add("__cplus_test_finish")
            add("__cplus_test_dispatch_match")
        }
        if (unit.functions.any { it.name == "__cplus_test_report_truth" }) add("__cplus_test_report_truth")
        if (unit.functions.any { it.name == "__cplus_test_report_equality" }) add("__cplus_test_report_equality")
    }.distinct().sorted()
}
