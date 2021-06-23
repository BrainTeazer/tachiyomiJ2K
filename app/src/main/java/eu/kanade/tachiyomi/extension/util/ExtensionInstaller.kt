package eu.kanade.tachiyomi.extension.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.storage.getUriCompat
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The installer which installs, updates and uninstalls the extensions.
 *
 * @param context The application context.
 */
internal class ExtensionInstaller(private val context: Context) {

    /**
     * The system's download manager
     */
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * The broadcast receiver which listens to download completion events.
     */
    private val downloadReceiver = DownloadCompletionReceiver()

    /**
     * The currently requested downloads, with the package name (unique id) as key, and the id
     * returned by the download manager.
     */
    private val activeDownloads = hashMapOf<String, Long>()

    /**
     * Relay used to notify the installation step of every download.
     */
    private val downloadsRelay = PublishRelay.create<Pair<Long, InstallStep>>()

    /** Map of download id to installer session id */
    val downloadInstallerMap = hashMapOf<Long, Int>()

    data class DownloadSessionInfo(
        val downloadId: Long,
        val session: PackageInstaller.Session,
        val sessionId: Int
    )

    /**
     * Adds the given extension to the downloads queue and returns an observable containing its
     * step in the installation process.
     *
     * @param url The url of the apk.
     * @param extension The extension to install.
     */
    fun downloadAndInstall(url: String, extension: Extension) = Observable.defer {
        val pkgName = extension.pkgName

        val oldDownload = activeDownloads[pkgName]
        if (oldDownload != null) {
            deleteDownload(pkgName)
        }

        // Register the receiver after removing (and unregistering) the previous download
        downloadReceiver.register()

        val downloadUri = url.toUri()
        val request = DownloadManager.Request(downloadUri)
            .setTitle(extension.name)
            .setMimeType(APK_MIME)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, downloadUri.lastPathSegment)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val id = downloadManager.enqueue(request)
        activeDownloads[pkgName] = id

        downloadsRelay.filter { it.first == id }
            .map {
                val sessionId = downloadInstallerMap[it.first] ?: return@map it.second to null
                val session = context.packageManager.packageInstaller.getSessionInfo(sessionId)
                it.second to session
            }
            // Poll download status
            .mergeWith(pollStatus(id))
            // Poll installation status
            .mergeWith(pollInstallStatus(id))
            // Force an error if the download takes more than 3 minutes
            .mergeWith(Observable.timer(3, TimeUnit.MINUTES).map { InstallStep.Error to null })
            // Stop when the application is installed or errors
            .takeUntil { it.first.isCompleted() }
            // Always notify on main thread
            .observeOn(AndroidSchedulers.mainThread())
            // Always remove the download when unsubscribed
            .doOnUnsubscribe { deleteDownload(pkgName) }
    }

    /**
     * Returns an observable that polls the given download id for its status every second, as the
     * manager doesn't have any notification system. It'll stop once the download finishes.
     *
     * @param id The id of the download to poll.
     */
    private fun pollStatus(id: Long): Observable<Pair<InstallStep, PackageInstaller.SessionInfo?>> {
        val query = DownloadManager.Query().setFilterById(id)

        return Observable.interval(0, 1, TimeUnit.SECONDS)
            // Get the current download status
            .map {
                downloadManager.query(query).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                }
            }
            // Ignore duplicate results
            .distinctUntilChanged()
            // Stop polling when the download fails or finishes
            .takeUntil { it == DownloadManager.STATUS_SUCCESSFUL || it == DownloadManager.STATUS_FAILED }
            // Map to our model
            .flatMap { status ->
                val step = when (status) {
                    DownloadManager.STATUS_PENDING -> InstallStep.Pending
                    DownloadManager.STATUS_RUNNING -> InstallStep.Downloading
                    else -> return@flatMap Observable.empty()
                }
                Observable.just(step to null as PackageInstaller.SessionInfo?)
            }
            .doOnError {
                Timber.e(it)
            }
    }

    private fun pollInstallStatus(id: Long): Observable<Pair<InstallStep, PackageInstaller.SessionInfo?>> {
        return Observable.interval(0, 250, TimeUnit.MILLISECONDS)
            .flatMap {
                val sessionId = downloadInstallerMap[id] ?: return@flatMap Observable.empty()
                val session = context.packageManager.packageInstaller.getSessionInfo(sessionId)
                Observable.just(InstallStep.Installing to session)
            }
            .doOnError {
                Timber.e(it)
            }
    }

    /**
     * Starts an intent to install the extension at the given uri.
     *
     * @param uri The uri of the extension to install.
     */
    fun installApk(downloadId: Long, uri: Uri) {
        val intent = Intent(context, ExtensionInstallActivity::class.java)
            .setDataAndType(uri, APK_MIME)
            .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(intent)
    }

    /**
     * Starts an intent to uninstall the extension by the given package name.
     *
     * @param pkgName The package name of the extension to uninstall
     */
    fun uninstallApk(pkgName: String) {
        val packageUri = "package:$pkgName".toUri()
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    /**
     * Sets the result of the installation of an extension.
     *
     * @param downloadId The id of the download.
     */
    fun setInstalling(downloadId: Long, sessionId: Int) {
        downloadsRelay.call(downloadId to InstallStep.Installing)
        downloadInstallerMap[downloadId] = sessionId
    }

    fun cancelInstallation(sessionId: Int) {
        val downloadId = downloadInstallerMap.entries.find { it.value == sessionId }?.key ?: return
        setInstallationResult(downloadId, false)
        context.packageManager.packageInstaller.abandonSession(sessionId)
    }

    /**
     * Sets the result of the installation of an extension.
     *
     * @param downloadId The id of the download.
     * @param result Whether the extension was installed or not.
     */
    fun setInstallationResult(downloadId: Long, result: Boolean) {
        val step = if (result) InstallStep.Installed else InstallStep.Error
        downloadInstallerMap.remove(downloadId)
        downloadsRelay.call(downloadId to step)
    }

    /**
     * Deletes the download for the given package name.
     *
     * @param pkgName The package name of the download to delete.
     */
    private fun deleteDownload(pkgName: String) {
        val downloadId = activeDownloads.remove(pkgName)
        if (downloadId != null) {
            downloadManager.remove(downloadId)
        }
        if (activeDownloads.isEmpty()) {
            downloadReceiver.unregister()
        }
    }

    /**
     * Receiver that listens to download status events.
     */
    private inner class DownloadCompletionReceiver : BroadcastReceiver() {

        /**
         * Whether this receiver is currently registered.
         */
        private var isRegistered = false

        /**
         * Registers this receiver if it's not already.
         */
        fun register() {
            if (isRegistered) return
            isRegistered = true

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            context.registerReceiver(this, filter)
        }

        /**
         * Unregisters this receiver if it's not already.
         */
        fun unregister() {
            if (!isRegistered) return
            isRegistered = false

            context.unregisterReceiver(this)
        }

        /**
         * Called when a download event is received. It looks for the download in the current active
         * downloads and notifies its installation step.
         */
        override fun onReceive(context: Context, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0) ?: return

            // Avoid events for downloads we didn't request
            if (id !in activeDownloads.values) return

            val uri = downloadManager.getUriForDownloadedFile(id)

            // Set next installation step
            if (uri != null) {
                downloadsRelay.call(id to InstallStep.Loading)
            } else {
                Timber.e("Couldn't locate downloaded APK")
                downloadsRelay.call(id to InstallStep.Error)
                return
            }

            val query = DownloadManager.Query().setFilterById(id)
            downloadManager.query(query).use { cursor ->
                if (cursor.moveToFirst()) {
                    val localUri = cursor.getString(
                        cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    ).removePrefix(FILE_SCHEME)

                    installApk(id, File(localUri).getUriCompat(context))
                }
            }
        }
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val EXTRA_DOWNLOAD_ID = "ExtensionInstaller.extra.DOWNLOAD_ID"
        const val FILE_SCHEME = "file://"
    }
}
