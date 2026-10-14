package com.atlaes.lifeos.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.room.InvalidationTracker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.atlaes.lifeos.LifeOsApplication
import com.atlaes.lifeos.data.database.LifeOsDatabase
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ExternalBackupState(
    val directory: String? = null,
    val lastSuccessAt: Long? = null,
    val status: String = "尚未设置备份目录",
    val isRunning: Boolean = false,
    val needsAuthorization: Boolean = true,
)

class ExternalBackupManager(context: Context, private val database: LifeOsDatabase) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("external_backup", Context.MODE_PRIVATE)
    private val backupService = LifeOsBackupService(database)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val directoryLock = Any()
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(readState())
    val state: StateFlow<ExternalBackupState> = mutableState

    private val observer = object : InvalidationTracker.Observer(
        "habits", "habit_records", "goals", "notes", "tags", "note_tag_relations", "journal_entries",
    ) {
        override fun onInvalidated(tables: Set<String>) {
            if (preferences.getBoolean(KEY_ARMED, false) && savedTreeUri() != null) scheduleChangedDataBackup()
        }
    }

    fun start() {
        database.invalidationTracker.addObserver(observer)
        if (savedTreeUri() != null) scope.launch {
            runCatching { requireDirectory() }
                .onSuccess {
                    if (preferences.getBoolean(KEY_ARMED, false)) schedulePeriodicBackup()
                    mutableState.value = readState()
                }
                .onFailure(::reportFailure)
        }
        mutableState.value = readState()
    }

    fun shouldPromptForDirectory(): Boolean = savedTreeUri() == null &&
        (preferences.getString(KEY_TREE_URI, null) != null || !preferences.getBoolean(KEY_PROMPT_SEEN, false))

    fun markPromptSeen() {
        preferences.edit().putBoolean(KEY_PROMPT_SEEN, true).apply()
    }

    fun initialPickerUri(): Uri? = savedTreeUri()

    suspend fun selectDirectory(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val id = DocumentsContract.getTreeDocumentId(uri)
            require(id == PARENT_TREE_ID || id == TARGET_TREE_ID) {
                "请选择手机共享存储的 Data 文件夹或其中的 LifeOSData 文件夹"
            }
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val selected = DocumentFile.fromTreeUri(appContext, uri) ?: error("无法打开所选目录")
            val target = if (id == PARENT_TREE_ID) synchronized(directoryLock) { ensureTargetDirectory(selected) } else selected
            require(target.isDirectory && target.canWrite()) { "目标目录不可写，请重新选择 Data/LifeOSData" }
            val existing = backupFiles(target).isNotEmpty()
            val alreadyArmed = uri.toString() == preferences.getString(KEY_TREE_URI, null) &&
                preferences.getBoolean(KEY_ARMED, false)
            val armed = !existing || alreadyArmed
            check(preferences.edit()
                .putString(KEY_TREE_URI, uri.toString())
                .putBoolean(KEY_PROMPT_SEEN, true)
                .putBoolean(KEY_ARMED, armed)
                .remove(KEY_ERROR)
                .commit()) { "无法保存目录授权状态" }
            mutableState.value = readState(
                if (!armed) "检测到已有备份，请先导入恢复，或选择立即备份" else "目录已就绪，等待备份",
            )
            if (armed) {
                schedulePeriodicBackup()
                scheduleChangedDataBackup()
            }
        }.onFailure(::reportFailure)
    }

    suspend fun backupNow(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val directory = requireDirectory()
                mutableState.value = readState("正在备份", isRunning = true)
                val json = backupService.exportJson()
                backupService.preview(json)
                val bytes = json.toByteArray(Charsets.UTF_8)
                val stem = "lifeos-backup-${LocalDateTime.now().format(FILE_TIME)}-${UUID.randomUUID().toString().take(8)}"
                val pending = directory.createFile("application/json", "$stem.pending.json")
                    ?: throw IOException("无法创建临时备份文件，可能空间不足或目录不可写")
                try {
                    appContext.contentResolver.openOutputStream(pending.uri, "wt")?.use { it.write(bytes); it.flush() }
                        ?: throw IOException("无法写入临时备份文件")
                    verifyBytes(pending, bytes)
                    if (!pending.renameTo("$stem.json")) throw IOException("无法将临时文件保存为正式备份")
                    val complete = directory.findFile("$stem.json") ?: throw IOException("无法找到正式备份文件")
                    verifyBytes(complete, bytes)
                    val finishedAt = System.currentTimeMillis()
                    check(preferences.edit()
                        .putLong(KEY_LAST_SUCCESS, finishedAt)
                        .putBoolean(KEY_ARMED, true)
                        .remove(KEY_ERROR)
                        .commit()) { "备份文件已写入，但无法保存备份状态" }
                    mutableState.value = readState("备份成功")
                    schedulePeriodicBackup()
                    runCatching { pruneOldBackups(directory) }
                    Unit
                } catch (error: Exception) {
                    runCatching { pending.delete() }
                    throw error
                }
            }.onFailure(::reportFailure)
        }
    }

    suspend fun latestBackup(): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val file = backupFiles(requireDirectory()).firstOrNull() ?: error("目录中没有 LifeOS JSON 备份")
            val bytes = appContext.contentResolver.openInputStream(file.uri)?.use {
                it.readNBytes(MAX_IMPORT_BYTES + 1)
            } ?: error("无法读取备份文件")
            require(bytes.size <= MAX_IMPORT_BYTES) { "备份文件超过 100 MB，无法在手机上解析" }
            val json = bytes.toString(Charsets.UTF_8)
            backupService.preview(json)
            (file.name ?: "LifeOS 备份") to json
        }.onFailure(::reportFailure)
    }

    suspend fun restore(json: String, replaceExisting: Boolean): Result<ImportPreview> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val result = backupService.restore(json, replaceExisting)
                preferences.edit().putBoolean(KEY_ARMED, true).remove(KEY_ERROR).apply()
                mutableState.value = readState("恢复成功，等待下次备份")
                if (savedTreeUri() != null) {
                    schedulePeriodicBackup()
                    scheduleChangedDataBackup()
                }
                result
            }.onFailure(::reportFailure)
        }
    }

    private fun requireDirectory(): DocumentFile {
        val uri = savedTreeUri() ?: error(
            if (isLegacyChildSelected()) "旧目录 LifeOSDat 的授权不能用于新目录，请重新选择 Data 文件夹"
            else "目录授权不可用，请重新选择 Data/LifeOSData",
        )
        val root = DocumentFile.fromTreeUri(appContext, uri) ?: error("无法访问备份目录，请重新授权")
        val id = DocumentsContract.getTreeDocumentId(uri)
        val target = if (id == PARENT_TREE_ID) synchronized(directoryLock) { ensureTargetDirectory(root) } else root
        require(target.isDirectory && target.canWrite()) { "备份目录不可访问或不可写，请重新授权" }
        return target
    }

    private fun ensureTargetDirectory(parent: DocumentFile): DocumentFile {
        val legacy = parent.findFile(LEGACY_FOLDER)?.takeIf { it.isDirectory }
        var target = parent.findFile(TARGET_FOLDER)
        if (target == null && legacy != null && legacy.renameTo(TARGET_FOLDER)) {
            target = parent.findFile(TARGET_FOLDER)
        }
        if (target == null) {
            target = parent.createDirectory(TARGET_FOLDER)
                ?: error("无法自动创建 $TARGET_FOLDER，请在系统文件选择器中手动创建并选择它")
        }
        require(target.isDirectory) { "$TARGET_FOLDER 已存在，但不是文件夹" }
        if (legacy != null && !preferences.getBoolean(KEY_LEGACY_MIGRATED, false)) {
            if (legacy.uri != target.uri) {
                backupFiles(legacy).forEach { source ->
                    if (target.findFile(source.name.orEmpty()) == null) copyLegacyBackup(source, target)
                }
            }
            check(preferences.edit().putBoolean(KEY_LEGACY_MIGRATED, true).commit()) { "无法保存旧备份迁移状态" }
        }
        return target
    }

    private fun copyLegacyBackup(source: DocumentFile, target: DocumentFile) {
        val name = source.name ?: error("旧备份没有文件名")
        val pending = target.createFile("application/json", name.removeSuffix(".json") + ".pending.json")
            ?: throw IOException("无法复制旧备份到 $TARGET_FOLDER")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = appContext.contentResolver.openInputStream(source.uri) ?: throw IOException("无法读取旧备份 $name")
            val output = appContext.contentResolver.openOutputStream(pending.uri, "wt")
                ?: throw IOException("无法写入迁移中的备份 $name")
            input.use { reader -> output.use { writer ->
                val buffer = ByteArray(8192)
                while (true) {
                    val length = reader.read(buffer)
                    if (length < 0) break
                    digest.update(buffer, 0, length)
                    writer.write(buffer, 0, length)
                }
                writer.flush()
            } }
            val expectedHash = digest.digest()
            verifyHash(pending, expectedHash)
            if (!pending.renameTo(name)) throw IOException("无法完成旧备份迁移：$name")
            val copied = target.findFile(name) ?: throw IOException("迁移后找不到备份：$name")
            verifyHash(copied, expectedHash)
        } catch (error: Exception) {
            runCatching { pending.delete() }
            throw error
        }
    }

    private fun savedTreeUri(): Uri? {
        val uri = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse) ?: return null
        return uri.takeIf { saved ->
            runCatching { DocumentsContract.getTreeDocumentId(saved) in setOf(PARENT_TREE_ID, TARGET_TREE_ID) }.getOrDefault(false) &&
            appContext.contentResolver.persistedUriPermissions.any {
                it.uri == saved && it.isReadPermission && it.isWritePermission
            }
        }
    }

    private fun readState(message: String? = null, isRunning: Boolean = false): ExternalBackupState {
        val authorized = savedTreeUri() != null
        val lastSuccess = preferences.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L }
        val status = message ?: preferences.getString(KEY_ERROR, null)?.let { "备份失败：$it" }
            ?: when {
                !authorized && isLegacyChildSelected() -> "旧目录 LifeOSDat 的授权不能用于新目录，请重新选择 Data 文件夹"
                !authorized -> "请选择并授权 Data/LifeOSData 目录"
                !preferences.getBoolean(KEY_ARMED, false) -> "检测到已有备份，请先导入恢复，或选择立即备份"
                lastSuccess != null -> "最近一次备份成功"
                else -> "等待首次备份"
            }
        return ExternalBackupState(
            directory = if (authorized) "手机共享存储/Data/LifeOSData/" else null,
            lastSuccessAt = lastSuccess,
            status = status,
            isRunning = isRunning,
            needsAuthorization = !authorized,
        )
    }

    private fun setError(error: Throwable) {
        val message = error.message ?: "未知错误"
        preferences.edit().putString(KEY_ERROR, message).apply()
        mutableState.value = readState()
    }

    private fun reportFailure(error: Throwable) {
        if (error is CancellationException) throw error
        setError(error)
    }

    private fun isLegacyChildSelected(): Boolean = preferences.getString(KEY_TREE_URI, null)
        ?.let { runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(it)) == LEGACY_TREE_ID }.getOrDefault(false) }
        ?: false

    private fun verifyBytes(file: DocumentFile, expected: ByteArray) {
        verifyHash(file, MessageDigest.getInstance("SHA-256").digest(expected))
    }

    private fun verifyHash(file: DocumentFile, expectedHash: ByteArray) {
        val actualHash = appContext.contentResolver.openInputStream(file.uri)?.use { input ->
            val verifier = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                verifier.update(buffer, 0, length)
            }
            verifier.digest()
        } ?: throw IOException("无法回读备份文件")
        if (!expectedHash.contentEquals(actualHash)) throw IOException("备份文件校验失败")
    }

    private fun backupFiles(directory: DocumentFile): List<DocumentFile> = directory.listFiles()
        .filter { it.isFile && it.name?.matches(BACKUP_NAME) == true }
        .sortedByDescending { it.name }

    private fun pruneOldBackups(directory: DocumentFile) {
        backupFiles(directory).drop(5).forEach { it.delete() }
    }

    private fun scheduleChangedDataBackup() {
        val request = OneTimeWorkRequestBuilder<ExternalBackupWorker>()
            .setInitialDelay(60, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(CHANGED_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun schedulePeriodicBackup() {
        val request = PeriodicWorkRequestBuilder<ExternalBackupWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(12, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        private const val PARENT_TREE_ID = "primary:Data"
        private const val TARGET_TREE_ID = "primary:Data/LifeOSData"
        private const val LEGACY_TREE_ID = "primary:Data/LifeOSDat"
        private const val TARGET_FOLDER = "LifeOSData"
        private const val LEGACY_FOLDER = "LifeOSDat"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_LAST_SUCCESS = "last_success"
        private const val KEY_ERROR = "last_error"
        private const val KEY_ARMED = "armed"
        private const val KEY_PROMPT_SEEN = "prompt_seen"
        private const val KEY_LEGACY_MIGRATED = "legacy_migrated"
        private const val CHANGED_WORK = "lifeos_changed_data_backup"
        private const val PERIODIC_WORK = "lifeos_periodic_backup"
        private const val MAX_IMPORT_BYTES = 100 * 1024 * 1024
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val BACKUP_NAME = Regex("lifeos-backup-\\d{8}-\\d{6}-[a-f0-9]{8}\\.json")
    }
}

class ExternalBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as LifeOsApplication).externalBackupManager
        val outcome = manager.backupNow()
        return when {
            outcome.isSuccess -> Result.success()
            manager.state.value.needsAuthorization -> Result.failure()
            runAttemptCount >= 2 -> Result.failure()
            else -> Result.retry()
        }
    }
}
