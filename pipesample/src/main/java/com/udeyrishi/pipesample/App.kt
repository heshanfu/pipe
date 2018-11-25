package com.udeyrishi.pipesample

import android.app.Application
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.repository.MutableRepository
import com.udeyrishi.pipe.util.AndroidLogger

class App : Application() {
    companion object {
        val jobsRepo: MutableRepository<Job<ImagePipelineMember>> = InMemoryRepository()
        val logger = AndroidLogger("Pipe Sample App")
    }

    override fun onTerminate() {
        jobsRepo.apply {
            items.forEach { (job, _, _) -> job.interrupt() }

            clear()
            close()
        }
        super.onTerminate()
    }
}
