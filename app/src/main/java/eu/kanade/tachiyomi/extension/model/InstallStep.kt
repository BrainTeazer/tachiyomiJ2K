package eu.kanade.tachiyomi.extension.model

enum class InstallStep {
    Pending, Downloading, Loading, Installing, Installed, Error;

    fun isCompleted(): Boolean {
        return this == Installed || this == Error
    }
}
