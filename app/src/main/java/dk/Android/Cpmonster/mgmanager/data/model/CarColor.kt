package dk.Android.Cpmonster.mgmanager.data.model

/** Paint colours used for the car drawing. Detected from the car's colour name, or picked in Settings. */
enum class CarColor(val label: String, val argb: Long, private val keywords: List<String>) {
    WHITE("White", 0xFFE8EAED, listOf("white", "bianco", "blanc", "weiss", "hvid")),
    SILVER("Silver", 0xFFA7ADB4, listOf("silver", "argent", "silber", "sølv")),
    GREY("Grey", 0xFF686E76, listOf("grey", "gray", "graphite", "titan", "gris", "grau", "grå")),
    BLACK("Black", 0xFF22252A, listOf("black", "noir", "schwarz", "sort", "nero")),
    RED("Red", 0xFFB3202A, listOf("red", "rosso", "rouge", "rot", "rød")),
    ORANGE("Orange", 0xFFE0701F, listOf("orange", "volcano", "copper")),
    YELLOW("Yellow", 0xFFE2B52B, listOf("yellow", "gold", "lime", "gelb", "gul")),
    GREEN("Green", 0xFF3E7A52, listOf("green", "grün", "grøn", "vert")),
    BLUE("Blue", 0xFF2B5CA8, listOf("blue", "bleu", "blau", "blå", "navy"));

    companion object {
        /** Best guess from the API's colour name, e.g. "Holborn Blue" → BLUE. */
        fun detect(colorName: String?): CarColor? {
            val n = colorName?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { c -> c.keywords.any { n.contains(it) } }
        }

        fun fromName(name: String?) = entries.firstOrNull { it.name == name }
    }
}
