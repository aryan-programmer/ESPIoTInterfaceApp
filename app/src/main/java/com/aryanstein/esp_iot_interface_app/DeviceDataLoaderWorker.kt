package com.aryanstein.esp_iot_interface_app

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

class DeviceDataLoaderWorker(
	context: Context,
	workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
	interface StateListener {
		fun onSuccess(data: String) {}
		fun whenRunning() {}
		fun onFailed() {}
	}

	companion object {
		private const val PERIOD_MILLIS: Long = 3000
		private const val MIN_BACKOFF_MILLIS: Long = WorkRequest.MIN_BACKOFF_MILLIS
		const val TAG = "DeviceDataLoaderWorker"

		private const val READ_URL_INPUT = "READ_URL_INPUT"
		private const val OUTPUT_DATA = "OUTPUT_DATA"

		private fun createInputData(url: String): Data {
			return Data.Builder().putString(READ_URL_INPUT, url).build()
		}

		private fun createOutputData(output: String): Data {
			return Data.Builder().putString(OUTPUT_DATA, output).build()
		}

		private fun parseOutputData(output: Data): String {
			return output.getString(OUTPUT_DATA).orEmpty()
		}

		fun scheduleLoadDataOnWorker(
			delayed: Boolean,
			uniquenessTag: String,
			ipAddr: String,
			workManager: WorkManager,
		): Operation {
			val reqBuilder = OneTimeWorkRequestBuilder<DeviceDataLoaderWorker>()
				.addTag(uniquenessTag)
				.addTag(TAG)
				.setBackoffCriteria(
					BackoffPolicy.LINEAR,
					MIN_BACKOFF_MILLIS,
					TimeUnit.MILLISECONDS
				)
				.setInputData(createInputData(ipAddr))

			if(delayed) {
				reqBuilder.setInitialDelay(PERIOD_MILLIS, TimeUnit.MILLISECONDS)
			}

			Log.d(TAG, "scheduleLoadData: Starting enqueuing")
			return workManager.beginUniqueWork(
				uniquenessTag,
				ExistingWorkPolicy.REPLACE,
				reqBuilder.build()
			).enqueue()
		}

		fun attachStateListener(
			uniquenessTag: String,
			workManager: WorkManager,
			lifecycleOwner: LifecycleOwner,
			stateListener: StateListener,
		) {
			workManager.getWorkInfosByTagLiveData(uniquenessTag).observe(lifecycleOwner) { list ->
				for(w in list) {
					Log.d(TAG, "Worker: $w")
					if(w.tags.contains(TAG)) {
						when(w.state) {
							WorkInfo.State.SUCCEEDED -> stateListener.onSuccess(parseOutputData(w.outputData))
							WorkInfo.State.RUNNING   -> stateListener.whenRunning()
							WorkInfo.State.FAILED    -> stateListener.onFailed()
							WorkInfo.State.ENQUEUED  -> {}
							WorkInfo.State.BLOCKED   -> {}
							WorkInfo.State.CANCELLED -> {}
						}
					}
				}
			}
		}
	}

	override suspend fun doWork(): Result {
		Log.d(TAG, "doWork: Started")

		return withContext(Dispatchers.IO) {
			try {
				val url = URI.create(inputData.getString(READ_URL_INPUT)).resolve(URL_STATUS_PATH).toURL()
				val urlConnection = url.openConnection() as HttpURLConnection
				urlConnection.readTimeout = TIMEOUT_MILLIS
				urlConnection.connectTimeout = TIMEOUT_MILLIS
				try {
					val stream = BufferedReader(InputStreamReader(urlConnection.inputStream))
					val response = StringBuffer()
					var inputLine = ""
					while((stream.readLine()?.also { inputLine = it }) != null) {
						response.append(inputLine)
					}
					Log.d(TAG, "doWork: Loaded data")
					stream.close()
					return@withContext Result.success(createOutputData(response.toString()))
				} finally {
					urlConnection.disconnect()
				}
			} catch(exception: Exception) {
				Log.e(TAG, "Failed to load data", exception)
				return@withContext Result.failure()
			}
		}
	}
}