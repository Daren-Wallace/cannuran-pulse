package capital.cannuran.pulse

import android.app.Application
import capital.cannuran.pulse.notify.Notifier
import capital.cannuran.pulse.work.Scheduler

class PulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Scheduler.ensurePeriodic(this)
    }
}
