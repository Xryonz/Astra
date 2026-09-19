package app.astra.mobile.core.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.concurrent.thread

class TrabalhoDeInstalar : JobService() {

    @Volatile
    private var espera: Thread? = null

    override fun onStartJob(parametros: JobParameters): Boolean {
        espera = thread(name = "astra-instalar") {
            try {
                Thread.sleep(ESPERA_MS)
            } catch (e: InterruptedException) {
                return@thread
            }
            CuidadorDeAtualizacao.instalarSeForHora(applicationContext)
            jobFinished(parametros, false)
        }
        return true
    }

    override fun onStopJob(parametros: JobParameters): Boolean {
        espera?.interrupt()
        return false
    }

    companion object {
        private const val ID = 0x417374
        private const val ESPERA_MS = 60_000L

        @RequiresApi(Build.VERSION_CODES.S)
        fun agendar(contexto: Context) {
            val trabalho = JobInfo.Builder(ID, ComponentName(contexto, TrabalhoDeInstalar::class.java))
                .setExpedited(true)
                .build()
            contexto.getSystemService(JobScheduler::class.java).schedule(trabalho)
        }

        fun cancelar(contexto: Context) {
            contexto.getSystemService(JobScheduler::class.java).cancel(ID)
        }
    }
}
