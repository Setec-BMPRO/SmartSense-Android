package com.smartsense.app.domain.model

enum class SortPreference(val displayName: String) {
    NAME("Name"),
    LEVEL("Level");
}


enum class AppTheme(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");
}

enum class ScanIntervals(val value: Int, val displayName: String) {
    ONE(1,"Immediately"),
    FIVE(5,"5 seconds"),
    TEN(10,"10 seconds"),
    FIFTEEN(15,"15 seconds"),
    TWENTY(20,"20 seconds");
    companion object{
        fun default():ScanIntervals=ONE
    }
}

enum class UnitSystem(val displayName: String) {
    METRIC("Metric"),
    IMPERIAL("Imperial");
}
