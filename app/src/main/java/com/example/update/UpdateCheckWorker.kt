package com.example.update
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
/** Legacy persisted worker never networks, downloads, posts an installer notice or installs. */
class UpdateCheckWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p){
 override suspend fun doWork():Result=Result.success()
 companion object { fun schedule(c:Context){AutomaticUpdateNotice.start(c)} }
}
