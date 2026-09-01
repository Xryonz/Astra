package app.astra.desktop

object Canal {
    val ehDeDesenvolvimento: Boolean = System.getProperty("astra.canal") != "estavel"
}
