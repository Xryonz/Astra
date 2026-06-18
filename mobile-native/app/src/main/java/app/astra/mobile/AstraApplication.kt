package app.astra.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Entry point do Hilt — gera o container de DI da aplicacao inteira. */
@HiltAndroidApp
class AstraApplication : Application()
