package app.astra.shared

// Primeiro tipo compartilhado entre Android (:app) e desktop (:desktopApp) --
// prova a fiacao do modulo :shared ponta a ponta. Dominio, DTOs e repos migram
// pra ca em incrementos seguintes do D1.
object AstraShared {
    const val BASE_URL = "https://astra-kwzc.onrender.com/"
    const val VERSION = "0.1.0"
}
