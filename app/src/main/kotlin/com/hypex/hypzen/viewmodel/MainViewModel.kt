package com.hypex.hypzen.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.system.Os
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hypex.hypzen.data.AdbManager
import com.hypex.hypzen.data.FastbootManager
import com.hypex.hypzen.data.SandboxedPlatformToolsManager
import com.hypex.hypzen.data.UsbMonitor
import com.hypex.hypzen.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val usbMonitor = UsbMonitor(application)
    private val adbManager = AdbManager(application)
    private val fastbootManager = FastbootManager(application)
    // Runs adb/fastboot binaries in a separate process (":cmd_runner") and streams logs back over Binder.
    private val platformToolsManager = SandboxedPlatformToolsManager(application)

    private val prefs = getApplication<Application>().getSharedPreferences("hypzen_prefs", android.content.Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _logLines = MutableStateFlow<List<LogLine>>(emptyList())
    val logLines = _logLines.asStateFlow()

    private val _cmdLogs = MutableStateFlow<List<LogLine>>(emptyList())
    val cmdLogs = _cmdLogs.asStateFlow()

    private val _deviceState = usbMonitor.deviceState

    private val _isRebooting = MutableStateFlow(false)
    val isRebooting = _isRebooting.asStateFlow()


    private val _rebootStatus = MutableStateFlow<String?>(null)
    val rebootStatus = _rebootStatus.asStateFlow()
    private val _rebootToastEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val rebootToastEvent = _rebootToastEvent.asSharedFlow()
    val deviceState = _deviceState

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile = _selectedFile.asStateFlow()

    private val _selectedFileName = MutableStateFlow<String?>(null)
    val selectedFileName = _selectedFileName.asStateFlow()

    private val _selectedSideloadFile = MutableStateFlow<Uri?>(null)
    val selectedSideloadFile = _selectedSideloadFile.asStateFlow()

    private val _selectedSideloadFileName = MutableStateFlow<String?>(null)
    val selectedSideloadFileName = _selectedSideloadFileName.asStateFlow()

    private val _sideloadProgress = MutableStateFlow(0f)
    val sideloadProgress = _sideloadProgress.asStateFlow()

    private val _sideloadPrepareInProgress = MutableStateFlow(false)
    val sideloadPrepareInProgress = _sideloadPrepareInProgress.asStateFlow()

    private val _sideloadPrepareProgress = MutableStateFlow(0f)
    val sideloadPrepareProgress = _sideloadPrepareProgress.asStateFlow()

    private var preparedSideloadZip: File? = null
    private var sideloadPrepareJob: Job? = null

    private val _flashProgress = MutableStateFlow(0f)
    val flashProgress = _flashProgress.asStateFlow()

    private val _globalProgress = MutableStateFlow(0f)
    val globalProgress = _globalProgress.asStateFlow()

    private val _selectedPartition = MutableStateFlow<String?>(null)
    val selectedPartition = _selectedPartition.asStateFlow()

    private val _selectedDirectory = MutableStateFlow<Uri?>(null)
    val selectedDirectory = _selectedDirectory.asStateFlow()

    private val _batchOperations = MutableStateFlow<List<FlashOperation>>(emptyList())
    val batchOperations = _batchOperations.asStateFlow()

    private val _confirmSheetState = MutableStateFlow<ConfirmSheetState?>(null)
    val confirmSheetState = _confirmSheetState.asStateFlow()

    private val _stopOnError = MutableStateFlow(true)
    val stopOnError = _stopOnError.asStateFlow()

    private val _maxDownloadSize = MutableStateFlow<Long?>(null)
    val maxDownloadSize = _maxDownloadSize.asStateFlow()

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode = _themeMode.asStateFlow()

    private val _isDarkMode = MutableStateFlow(_themeMode.value == ThemeMode.DARK)
    val isDarkMode = _isDarkMode.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory = _commandHistory.asStateFlow()

    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites = _favorites.asStateFlow()

    private val _fastbootEngine = MutableStateFlow(
        runCatching { FastbootEngine.valueOf(prefs.getString("fastboot_engine", FastbootEngine.LIB_ANDROID.name)!!) }
            .getOrDefault(FastbootEngine.LIB_ANDROID)
    )
    val fastbootEngine = _fastbootEngine.asStateFlow()

    private val projectRootPath = "/storage/emulated/0/AndroidIDEProjects/HypZen"
    private val projectRootPlatformToolsPath = "$projectRootPath/platform-tools"
    private val projectRootAssetsPath = "$projectRootPath/assets"
    private val _platformToolsPath = MutableStateFlow(projectRootAssetsPath)
    val platformToolsPath = _platformToolsPath.asStateFlow()

    private val embeddedToolsDir: File by lazy {
        File(getApplication<Application>().codeCacheDir, "platform-tools")
    }

    init {
        _commandHistory.value = prefs.getStringSet("cmd_history", emptySet())?.toList() ?: emptyList()
        _favorites.value = prefs.getStringSet("cmd_favorites", emptySet())?.toList() ?: emptyList()
        usbMonitor.start()

        viewModelScope.launch {
            adbManager.shellOutput.collect { log ->
                _logLines.update { it + log }
                _cmdLogs.update { it + log }
            }
        }

        viewModelScope.launch {
            _deviceState.collect { state ->
                when (state) {
                    is DeviceState.ADB -> {
                        addLog("ADB Device Connected", LogType.OK)
                        if (_fastbootEngine.value == FastbootEngine.PLATFORM_TOOLS) {
                            addLog("Using platform-tools ADB engine (proxy-only mode)", LogType.INFO)
                            adbManager.startProxyOnly(state.device)
                        } else {
                            adbManager.connect(state.device)
                        }
                    }
                    is DeviceState.Fastboot -> {
                        addLog("Fastboot Device Connected", LogType.OK)
                        if (_fastbootEngine.value == FastbootEngine.LIB_ANDROID) {
                            fastbootManager.connect(state.device)
                            viewModelScope.launch {
                                val max = fastbootManager.getVar("max-download-size")
                                _maxDownloadSize.value = try {
                                    if (max?.startsWith("0x") == true) max.removePrefix("0x").toLong(16) else max?.toLongOrNull()
                                } catch (_: Exception) {
                                    null
                                }
                                _maxDownloadSize.value?.let { addLog("Max Download Size: ${it / 1024 / 1024}MB", LogType.INFO) }
                            }
                        } else {
                            _maxDownloadSize.value = null
                            addLog("Using platform-tools fastboot engine", LogType.INFO)
                        }
                    }
                    is DeviceState.Disconnected -> {
                        addLog("Device Disconnected", LogType.DIM)
                        adbManager.disconnect()
                        fastbootManager.disconnect()
                        _maxDownloadSize.value = null
                    }
                    is DeviceState.UsbPermissionRequired -> addLog("Please grant USB host permission on this device", LogType.WARN)
                }
            }
        }
    }

    fun addLog(text: String, type: LogType) {
        _logLines.update { it + LogLine(dateFormat.format(Date()), text, type) }
    }

    fun clearLogs() {
        _logLines.value = emptyList()
    }

    fun clearSideloadLogs() {
        _logLines.update { lines -> lines.filterNot { it.text.startsWith("[SIDELOAD]") || it.text.startsWith("[PT]") } }
        _cmdLogs.update { lines -> lines.filterNot { it.text.startsWith("[SIDELOAD]") || it.text.startsWith("[PT]") } }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        _isDarkMode.value = mode == ThemeMode.DARK
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun cycleThemeMode() {
        val next = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
    }

    fun setFastbootEngine(engine: FastbootEngine) {
        _fastbootEngine.value = engine
        prefs.edit().putString("fastboot_engine", engine.name).apply()
        if (_deviceState.value is DeviceState.Fastboot) {
            if (engine == FastbootEngine.LIB_ANDROID) {
                val state = _deviceState.value as DeviceState.Fastboot
                viewModelScope.launch {
                    fastbootManager.connect(state.device)
                    val max = fastbootManager.getVar("max-download-size")
                    _maxDownloadSize.value = try {
                        if (max?.startsWith("0x") == true) max.removePrefix("0x").toLong(16) else max?.toLongOrNull()
                    } catch (_: Exception) {
                        null
                    }
                }
            } else {
                fastbootManager.disconnect()
                _maxDownloadSize.value = null
            }
        }

        val adbState = _deviceState.value as? DeviceState.ADB
        if (adbState != null) {
            viewModelScope.launch {
                if (engine == FastbootEngine.PLATFORM_TOOLS) {
                    addLog("Switching ADB to platform-tools engine (proxy-only mode)", LogType.INFO)
                    adbManager.startProxyOnly(adbState.device)
                } else {
                    addLog("Switching ADB to embedded lib engine", LogType.INFO)
                    adbManager.connect(adbState.device)
                }
            }
        }
    }

    fun setPlatformToolsPath(path: String) {
        _platformToolsPath.value = projectRootAssetsPath
        addLog("Binaries path is fixed to app root assets (by-arch): $projectRootAssetsPath", LogType.INFO)
    }

    fun selectFile(uri: Uri) {
        _selectedFile.value = uri
        val name = getFileName(uri)
        _selectedFileName.value = name
        _selectedPartition.value = name?.let { detectPartition(it) }
    }

    fun selectSideloadFile(uri: Uri) {
        _selectedSideloadFile.value = uri
        _selectedSideloadFileName.value = getFileName(uri)
        prepareSideloadZip(uri)
    }

    private fun prepareSideloadZip(uri: Uri) {
        sideloadPrepareJob?.cancel()
        sideloadPrepareJob = viewModelScope.launch(Dispatchers.IO) {
            _sideloadPrepareInProgress.value = true
            _sideloadPrepareProgress.value = 0f
            preparedSideloadZip?.delete()
            preparedSideloadZip = null

            val context = getApplication<Application>()
            try {
                val totalSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                val input = context.contentResolver.openInputStream(uri)
                if (input == null) {
                    addLog("[SIDELOAD] Failed to open selected ZIP for preparation", LogType.ERROR)
                    return@launch
                }

                addLog("[SIDELOAD] Preparing package...", LogType.INFO)
                val temp = File.createTempFile("hypzen_", ".zip", context.cacheDir)
                var copied = 0L
                var lastBucket = -1
                val buffer = ByteArray(256 * 1024)

                input.use { ins ->
                    temp.outputStream().use { out ->
                        while (true) {
                            val read = ins.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            copied += read
                            if (totalSize > 0) {
                                val pct = ((copied * 100) / totalSize).toInt().coerceIn(0, 100)
                                val bucket = pct / 2 // 2% steps to reduce churn
                                if (bucket != lastBucket) {
                                    lastBucket = bucket
                                    _sideloadPrepareProgress.value = pct / 100f
                                }
                            }
                        }
                        out.flush()
                    }
                }

                if (temp.length() <= 0L) {
                    addLog("[SIDELOAD] Prepared ZIP is 0 bytes (copy failed)", LogType.ERROR)
                    temp.delete()
                    return@launch
                }

                preparedSideloadZip = temp
                _sideloadPrepareProgress.value = 1f
                addLog("[SIDELOAD] Package prepared: ${temp.length()} bytes", LogType.OK)
            } catch (e: Exception) {
                addLog("[SIDELOAD] Prepare error: ${e.message}", LogType.ERROR)
            } finally {
                _sideloadPrepareInProgress.value = false
            }
        }
    }

    fun selectDirectory(uri: Uri) {
        _selectedDirectory.value = uri
        matchFiles()
    }

    fun selectScript(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (content != null) parseFlashScript(content)
        }
    }

    private fun parseFlashScript(content: String) {
        val operations = mutableListOf<FlashOperation>()
        val variables = mutableMapOf<String, String>()

        val fastbootLineRegex = Regex("""(?i)(?:\.?[\/\\])?fastboot(?:\.exe)?\s+(?:-s\s+\S+\s+)?(.+)""")
        val setRegex = Regex("""(?i)set\s+([a-z0-9_-]+)=([a-z0-9._-]+)""")

        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("::")) return@forEach

            val setMatch = setRegex.find(trimmed)
            if (setMatch != null) {
                variables[setMatch.groupValues[1]] = setMatch.groupValues[2]
                return@forEach
            }

            var processedLine = trimmed
            variables.forEach { (k, v) ->
                processedLine = processedLine.replace("%$k%", v, ignoreCase = true)
            }

            val fastbootMatch = fastbootLineRegex.find(processedLine) ?: return@forEach
            val tokens = fastbootMatch.groupValues[1]
                .trim()
                .split(Regex("""\s+"""))
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@forEach

            val command = tokens[0].lowercase(Locale.ROOT)
            when (command) {
                "flash" -> {
                    val partition = tokens.getOrNull(1) ?: return@forEach
                    val fileName = tokens.getOrNull(2)
                    operations.add(FlashOperation(command = "flash", partition = partition, fileName = fileName))
                }
                "erase", "format" -> {
                    val partition = tokens.getOrNull(1) ?: return@forEach
                    operations.add(FlashOperation(command = command, partition = partition))
                }
                "set_active" -> {
                    val slot = tokens.getOrNull(1) ?: return@forEach
                    operations.add(FlashOperation(command = "set_active", partition = slot))
                }
                "reboot" -> {
                    val target = tokens.getOrNull(1)?.lowercase(Locale.ROOT) ?: "system"
                    operations.add(FlashOperation(command = "reboot", partition = target))
                }
                "reboot-bootloader" -> operations.add(FlashOperation(command = "reboot-bootloader", partition = "bootloader"))
            }
        }

        _batchOperations.value = operations
        matchFiles()
    }

    private fun matchFiles() {
        val dirUri = _selectedDirectory.value ?: return
        val currentOps = _batchOperations.value
        if (currentOps.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                dirUri,
                DocumentsContract.getTreeDocumentId(dirUri)
            )

            val filesMap = mutableMapOf<String, Uri>()
            resolver.query(childrenUri, arrayOf("_display_name", "document_id"), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val id = cursor.getString(1)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(dirUri, id)
                    filesMap[name.lowercase()] = uri
                }
            }

            val updatedOps = currentOps.map { op ->
                if (op.command != "flash" || op.fileName == null) {
                    op.copy(status = OperationStatus.READY)
                } else {
                    val matchingUri = filesMap[op.fileName.lowercase()]
                    if (matchingUri != null) op.copy(fileUri = matchingUri, status = OperationStatus.READY)
                    else op.copy(status = OperationStatus.MISSING)
                }
            }
            _batchOperations.value = updatedOps
        }
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex("_display_name")
                if (index != -1) it.getString(index) else null
            } else null
        }
    }

    private fun detectPartition(fileName: String): String? {
        val n = fileName.lowercase(Locale.ROOT)
        return when {
            n.contains("vendor_boot") -> "vendor_boot"
            n.contains("init_boot") -> "init_boot"
            n.contains("recovery") -> "recovery"
            n.contains("dtbo") -> "dtbo"
            n.contains("vbmeta") -> "vbmeta"
            n.contains("super") -> "super"
            n.contains("userdata") -> "userdata"
            n.contains("cache") -> "cache"
            n.contains("vendor") -> "vendor"
            n.contains("system") -> "system"
            n.contains("odm") -> "odm"
            n.contains("product") -> "product"
            n.contains("boot") -> "boot"
            else -> null
        }
    }

    fun setPartition(partition: String) {
        _selectedPartition.value = partition
    }

    fun flashPartition() {
        val uri = _selectedFile.value ?: return
        val partition = _selectedPartition.value ?: return
        val fileName = _selectedFileName.value ?: "unknown"

        showConfirm("Flash Partition", "Flash $fileName to $partition?", "fastboot flash $partition", true) {
            viewModelScope.launch {
                flashWithEngine(partition, uri, true) { _flashProgress.value = it }
            }
        }
    }

    fun runBatchFlash() {
        val ops = _batchOperations.value.filter { it.status == OperationStatus.READY }
        if (ops.isEmpty()) return

        showConfirm("Batch Flash", "Flash ${ops.size} operations from script?", "Multiple Fastboot Commands", true) {
            viewModelScope.launch {
                val currentBatch = _batchOperations.value
                var hasError = false
                _globalProgress.value = 0f

                val readyOpsCount = ops.size
                var completedCount = 0

                for (index in currentBatch.indices) {
                    val op = currentBatch[index]
                    addLog("[BATCH] Step ${completedCount + 1}/$readyOpsCount: ${op.command} ${op.partition}", LogType.INFO)
                    if (op.status != OperationStatus.READY) continue
                    if (hasError && _stopOnError.value) break

                    _batchOperations.update { list ->
                        list.mapIndexed { i, o -> if (i == index) o.copy(status = OperationStatus.FLASHING) else o }
                    }

                    var opSuccess = true
                    val fileUri = op.fileUri
                    if (op.command == "flash" && fileUri != null) {
                        val ok = flashWithEngine(op.partition, fileUri, false) { progress ->
                            _flashProgress.value = progress
                            _globalProgress.value = (completedCount + progress) / readyOpsCount
                        }
                        if (!ok) opSuccess = false
                    } else {
                        val cmd = when (op.command) {
                            "reboot" -> when (op.partition.lowercase(Locale.ROOT)) {
                                "", "system" -> "reboot"
                                "bootloader", "fastboot" -> "reboot-bootloader"
                                else -> "reboot"
                            }
                            "reboot-bootloader" -> "reboot-bootloader"
                            "set_active" -> "set_active:${op.partition}"
                            else -> "${op.command}:${op.partition}"
                        }
                        val ok = executeFastbootCommands(listOf(cmd), mirrorToCmdLogs = false)
                        if (!ok) opSuccess = false
                        _globalProgress.value = (completedCount + 1f) / readyOpsCount
                    }

                    if (!opSuccess) {
                        hasError = true
                        _batchOperations.update { list ->
                            list.mapIndexed { i, o -> if (i == index) o.copy(status = OperationStatus.ERROR) else o }
                        }
                        if (_stopOnError.value) {
                            addLog("Batch stopped due to error", LogType.WARN)
                            break
                        }
                    } else {
                        completedCount++
                        _globalProgress.value = completedCount.toFloat() / readyOpsCount
                        _batchOperations.update { list ->
                            list.mapIndexed { i, o -> if (i == index) o.copy(status = OperationStatus.DONE) else o }
                        }
                    }
                }
            }
        }
    }

    fun setStopOnError(value: Boolean) {
        _stopOnError.value = value
    }

    fun runCustomCommand(cmd: String) {
        if (cmd.isBlank()) return
        val sanitizedCmd = cmd.trim()
            .removePrefix("adb shell ")
            .removePrefix("adb ")
            .removePrefix("fastboot ")

        val currentHistory = _commandHistory.value.toMutableList()
        currentHistory.remove(sanitizedCmd)
        currentHistory.add(0, sanitizedCmd)
        val limitedHistory = currentHistory.take(50)
        _commandHistory.value = limitedHistory
        prefs.edit().putStringSet("cmd_history", limitedHistory.toSet()).apply()

        val state = _deviceState.value

        viewModelScope.launch {
            _cmdLogs.update { it + LogLine(dateFormat.format(Date()), "> $sanitizedCmd", LogType.CMD) }

            when (state) {
                is DeviceState.ADB -> {
                    if (_fastbootEngine.value == FastbootEngine.PLATFORM_TOOLS) {
                        _cmdLogs.update { it + LogLine(dateFormat.format(Date()), "[CMD] Using platform-tools engine (adb shell)", LogType.DIM) }
                        executePlatformAdbCommand(listOf("shell", sanitizedCmd), mirrorToCmdLogs = true)
                    } else {
                        adbManager.runInteractiveCommand(sanitizedCmd)
                    }
                }
                is DeviceState.Fastboot -> {
                    val fbCmd = normalizeFastbootCommand(sanitizedCmd)
                    if (fbCmd == null) {
                        val err = LogLine(
                            dateFormat.format(Date()),
                            "Unsupported fastboot command format. Use Flash tab for image flashing.",
                            LogType.ERROR
                        )
                        _logLines.update { it + err }
                        _cmdLogs.update { it + err }
                        return@launch
                    }
                    executeFastbootCommands(listOf(fbCmd), mirrorToCmdLogs = true)
                }
                else -> {
                    val err = LogLine(dateFormat.format(Date()), "No device connected", LogType.ERROR)
                    _logLines.update { it + err }
                    _cmdLogs.update { it + err }
                }
            }
        }
    }


    fun startSideload() {
        if (_selectedSideloadFile.value == null) return
        showConfirm(
            title = "ADB Sideload",
            subtitle = "Start adb sideload for selected ZIP? Device must be in sideload mode.",
            command = "adb sideload <zip>",
            isDangerous = false
        ) {
            viewModelScope.launch {
                val adbState = _deviceState.value as? DeviceState.ADB
                if (adbState == null) {
                    val err = LogLine(dateFormat.format(Date()), "ADB mode required for sideload", LogType.ERROR)
                    _logLines.update { it + err }
                    _cmdLogs.update { it + err }
                    return@launch
                }

                if (_sideloadPrepareInProgress.value) {
                    addLog("[SIDELOAD] Please wait: still preparing the ZIP", LogType.WARN)
                    return@launch
                }
                val tempZip = preparedSideloadZip
                if (tempZip == null || !tempZip.exists() || tempZip.length() <= 0L) {
                    addLog("[SIDELOAD] ZIP not prepared yet. Re-select the file to prepare it.", LogType.ERROR)
                    return@launch
                }
                addLog("[SIDELOAD] Using prepared ZIP: ${tempZip.length()} bytes", LogType.INFO)

                _sideloadProgress.value = 0f
                val engine = _fastbootEngine.value
                if (engine == FastbootEngine.PLATFORM_TOOLS) {
                    addLog("[SIDELOAD] Using platform-tools engine (adb binary)", LogType.INFO)
                    val ok = executePlatformAdbCommand(listOf("sideload", tempZip.absolutePath), mirrorToCmdLogs = true) { line ->
                        val percent = Regex("""(\d{1,3})%""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        if (percent != null) _sideloadProgress.value = (percent.coerceIn(0, 100) / 100f)
                    }
                    if (!ok) {
                        addLog("[SIDELOAD] Platform-tools sideload failed; trying embedded engine", LogType.WARN)
                        if (!adbManager.isConnected()) {
                            addLog("[SIDELOAD] ADB not connected; reconnecting...", LogType.WARN)
                            val reOk = adbManager.connect(adbState.device)
                            if (!reOk) {
                                addLog("[SIDELOAD] Reconnect failed. Unplug/replug OTG and re-enter sideload mode.", LogType.ERROR)
                                return@launch
                            }
                        }
                        var sawUpload = false
                        adbManager.sideload(tempZip).collect { log ->
                            _logLines.update { it + log }
                            _cmdLogs.update { it + log }
                            if (log.text.startsWith("[SIDELOAD] Upload ")) sawUpload = true
                        }
                        if (!sawUpload) addLog("[SIDELOAD] Embedded sideload did not start uploading. Check recovery sideload mode.", LogType.ERROR)
                    }
                } else {
                    addLog("[SIDELOAD] Using libadb-android path", LogType.INFO)
                    if (!adbManager.isConnected()) {
                        addLog("[SIDELOAD] ADB not connected; reconnecting...", LogType.WARN)
                        val ok = adbManager.connect(adbState.device)
                        if (!ok) {
                            addLog("[SIDELOAD] Reconnect failed. Unplug/replug OTG and re-enter sideload mode.", LogType.ERROR)
                            return@launch
                        }
                    }
                    var hadError = false
                    var sawUpload = false
                    adbManager.sideload(tempZip).collect { log ->
                        _logLines.update { it + log }
                        _cmdLogs.update { it + log }
                        if (log.type == LogType.ERROR) hadError = true

                        if (log.text.startsWith("[SIDELOAD] Upload ")) {
                            sawUpload = true
                            val percent = log.text.substringAfter("Upload ").substringBefore("%").trim().toIntOrNull()
                            if (percent != null) {
                                _sideloadProgress.value = percent / 100f
                            }
                        } else if (log.text.contains("DONE")) {
                            _sideloadProgress.value = 1f
                        }
                    }
                    if (hadError && !sawUpload) {
                        addLog("[SIDELOAD] Embedded sideload failed early; trying platform-tools engine", LogType.WARN)
                        executePlatformAdbCommand(listOf("sideload", tempZip.absolutePath), mirrorToCmdLogs = true) { line ->
                            val percent = Regex("""(\d{1,3})%""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            if (percent != null) _sideloadProgress.value = (percent.coerceIn(0, 100) / 100f)
                        }
                    }
                }
                _sideloadProgress.value = 0f
                runCatching { tempZip.delete() }
                preparedSideloadZip = null
            }
        }
    }

    fun toggleFavorite(cmd: String) {
        val current = _favorites.value.toMutableList()
        val sanitized = cmd.trim()
        if (current.contains(sanitized)) current.remove(sanitized) else current.add(sanitized)
        _favorites.value = current
        prefs.edit().putStringSet("cmd_favorites", current.toSet()).apply()
    }

    fun clearCmdLogs() {
        _cmdLogs.value = emptyList()
    }

    fun reboot(mode: String) {
        if (_isRebooting.value) return

        val state = _deviceState.value
        viewModelScope.launch {
            _isRebooting.value = true
            _rebootStatus.value = "Rebooting to ${mode}..."
            var success = false
            var message = "Reboot command failed"
            try {
                if (state is DeviceState.ADB) {
                    val engine = _fastbootEngine.value
                    val platformArgs = when (mode) {
                        "system" -> listOf("reboot")
                        "bootloader" -> listOf("reboot", "bootloader")
                        "recovery" -> listOf("reboot", "recovery")
                        "fastboot" -> listOf("reboot", "fastboot")
                        "sideload" -> listOf("reboot", "sideload")
                        "edl" -> listOf("reboot", "edl")
                        else -> listOf("reboot")
                    }
                    val shellCmd = when (mode) {
                        "system" -> "reboot"
                        "bootloader" -> "reboot bootloader"
                        "recovery" -> "reboot recovery"
                        "fastboot" -> "reboot fastboot"
                        "sideload" -> "reboot sideload"
                        "edl" -> "reboot edl"
                        else -> "reboot"
                    }

                    if (engine == FastbootEngine.PLATFORM_TOOLS) {
                        addLog("[REBOOT] Using platform-tools engine (adb binary)", LogType.INFO)
                        success = executePlatformAdbCommand(platformArgs, mirrorToCmdLogs = false)
                    } else {
                        var hadError = false
                        adbManager.shell(shellCmd).collect { log ->
                            _logLines.update { it + log }
                            if (log.type == LogType.ERROR) hadError = true
                        }
                        success = !hadError
                    }

                    message = if (success) "Reboot command sent ($mode)" else "ADB reboot failed ($mode)"
                } else if (state is DeviceState.Fastboot) {
                    when (mode) {
                        "system" -> {
                            success = executeFastbootCommands(listOf("reboot"), mirrorToCmdLogs = false)
                        }
                        "bootloader", "fastboot" -> {
                            success = executeFastbootCommands(listOf("reboot-bootloader"), mirrorToCmdLogs = false)
                        }
                        "recovery" -> {
                            addLog("Trying fastboot reboot to recovery", LogType.INFO)
                            val candidates = listOf("reboot recovery", "reboot-recovery")
                            for (candidate in candidates) {
                                if (executeFastbootCommands(listOf(candidate), mirrorToCmdLogs = false)) {
                                    success = true
                                    break
                                }
                            }
                            if (!success) {
                                addLog("Recovery reboot is not supported by this device fastboot. Reboot to system, then use ADB reboot recovery.", LogType.WARN)
                            }
                        }
                        else -> {
                            addLog("Reboot mode '$mode' is not broadly supported over fastboot", LogType.WARN)
                            success = false
                        }
                    }
                    message = if (success) "Fastboot reboot command sent ($mode)" else "Fastboot reboot failed ($mode)"
                } else {
                    addLog("No device connected", LogType.ERROR)
                    success = false
                    message = "No device connected"
                }
            } catch (e: Exception) {
                success = false
                message = "Reboot error: ${e.message ?: "unknown"}"
                addLog(message, LogType.ERROR)
            } finally {
                _isRebooting.value = false
                _rebootStatus.value = null
                _rebootToastEvent.tryEmit(message)
            }
        }
    }

    private suspend fun executePlatformAdbCommand(
        args: List<String>,
        mirrorToCmdLogs: Boolean,
        onOutputLine: ((String) -> Unit)? = null
    ): Boolean {
        val adbPath = adbBinaryPath() ?: run {
            val err = LogLine(
                dateFormat.format(Date()),
                "adb binary unavailable. Provide jniLibs/<abi>/libadb_exec.so (recommended) or app assets binaries/<abi>/adb.",
                LogType.ERROR
            )
            _logLines.update { it + err }
            if (mirrorToCmdLogs) _cmdLogs.update { it + err }
            return false
        }

        val port = adbManager.currentProxyPort()
        if (port == null) {
            val state = _deviceState.value as? DeviceState.ADB
            if (state != null) {
                addLog("[ADB] Proxy not running; restarting proxy-only mode", LogType.WARN)
                adbManager.startProxyOnly(state.device)
            }
        }

        val finalPort = adbManager.currentProxyPort()
        if (finalPort == null) {
            val err = LogLine(dateFormat.format(Date()), "ADB proxy not running; reconnect the device and grant USB permission", LogType.ERROR)
            _logLines.update { it + err }
            if (mirrorToCmdLogs) _cmdLogs.update { it + err }
            return false
        }

        val serial = "127.0.0.1:$finalPort"
        val env = platformToolsEnv()

        // Ensure platform-tools adb uses our TCP proxy (OTG device) instead of libusb.
        runPlatformAdbRaw(adbPath, listOf("disconnect", serial), env, mirrorToCmdLogs = false, onOutputLine = null)
        val connected = runPlatformAdbRaw(adbPath, listOf("connect", serial), env, mirrorToCmdLogs = mirrorToCmdLogs, onOutputLine = onOutputLine)
        if (!connected) return false

        val ok = runPlatformAdbRaw(adbPath, listOf("-s", serial) + args, env, mirrorToCmdLogs = mirrorToCmdLogs, onOutputLine = onOutputLine)
        runPlatformAdbRaw(adbPath, listOf("disconnect", serial), env, mirrorToCmdLogs = false, onOutputLine = null)
        return ok
    }

    private suspend fun runPlatformAdbRaw(
        adbPath: String,
        args: List<String>,
        env: Map<String, String>,
        mirrorToCmdLogs: Boolean,
        onOutputLine: ((String) -> Unit)?
    ): Boolean {
        var ok = true
        platformToolsManager.runCommand(listOf(adbPath) + args, env).collect { log ->
            _logLines.update { it + log }
            if (mirrorToCmdLogs) _cmdLogs.update { it + log }

            if (log.text.startsWith("[PT] ")) {
                val line = log.text.removePrefix("[PT] ").trim()
                if (!line.startsWith(">>>") && !line.startsWith("env:")) {
                    onOutputLine?.invoke(line)
                }
            }
            if (log.text.contains("Command failed", ignoreCase = true)) ok = false
        }
        return ok
    }

    fun wipePartitions(partitions: List<String>) {
        val clean = partitions.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return
        val preview = clean.joinToString(separator = ", ") { "erase $it" }
        showConfirm(
            title = "Wipe Partitions",
            subtitle = "This will erase ${clean.size} partition(s).",
            command = preview,
            isDangerous = true
        ) {
            viewModelScope.launch {
                executeFastbootCommands(clean.map { "erase:$it" }, mirrorToCmdLogs = true)
            }
        }
    }

    fun factoryReset() {
        showConfirm(
            title = "Factory Reset",
            subtitle = "This erases userdata and cache. This cannot be undone.",
            command = "fastboot erase userdata && fastboot erase cache",
            isDangerous = true
        ) {
            viewModelScope.launch {
                executeFastbootCommands(listOf("erase:userdata", "erase:cache"), mirrorToCmdLogs = true)
            }
        }
    }

    fun oemUnlock() {
        showConfirm(
            title = "OEM Unlock",
            subtitle = "This may wipe all data and can permanently affect device security state.",
            command = "fastboot oem unlock",
            isDangerous = true
        ) {
            viewModelScope.launch {
                executeFastbootCommands(listOf("oem unlock"), mirrorToCmdLogs = true)
            }
        }
    }

    private suspend fun flashWithEngine(
        partition: String,
        fileUri: Uri,
        mirrorToCmdLogs: Boolean,
        onProgress: (Float) -> Unit
    ): Boolean {
        val engine = _fastbootEngine.value
        _logLines.update { it + LogLine(dateFormat.format(Date()), "[FLASH] Starting flash using ${engine.name}", LogType.INFO) }
        if (mirrorToCmdLogs) _cmdLogs.update { it + LogLine(dateFormat.format(Date()), "[FLASH] Starting flash using ${engine.name}", LogType.INFO) }

        return when (engine) {
            FastbootEngine.LIB_ANDROID -> {
                val state = _deviceState.value as? DeviceState.Fastboot
                if (state == null) {
                    val err = LogLine(dateFormat.format(Date()), "Flash failed preflight: device is not in fastboot mode", LogType.ERROR)
                    _logLines.update { it + err }
                    if (mirrorToCmdLogs) _cmdLogs.update { it + err }
                    return false
                }
                if (!fastbootManager.connect(state.device)) {
                    val err = LogLine(dateFormat.format(Date()), "Flash failed preflight: could not claim fastboot USB interface", LogType.ERROR)
                    _logLines.update { it + err }
                    if (mirrorToCmdLogs) _cmdLogs.update { it + err }
                    return false
                }

                val context = getApplication<Application>()
                val input = context.contentResolver.openInputStream(fileUri)
                if (input == null) {
                    val err = LogLine(dateFormat.format(Date()), "Cannot open selected image from content resolver", LogType.ERROR)
                    _logLines.update { it + err }
                    if (mirrorToCmdLogs) _cmdLogs.update { it + err }
                    return false
                }
                val size = context.contentResolver.openFileDescriptor(fileUri, "r")?.use { it.statSize } ?: 0L
                _logLines.update { it + LogLine(dateFormat.format(Date()), "[FLASH] Preflight: image size=$size bytes, partition=$partition", LogType.INFO) }
                if (size <= 0L) {
                    val err = LogLine(dateFormat.format(Date()), "Flash failed preflight: image size is 0 bytes", LogType.ERROR)
                    _logLines.update { it + err }
                    if (mirrorToCmdLogs) _cmdLogs.update { it + err }
                    return false
                }
                _maxDownloadSize.value?.let { max ->
                    if (size > max) addLog("Warning: Image exceeds max-download-size ($size > $max)", LogType.WARN)
                }

                var ok = true
                fastbootManager.flash(partition, input, size, onProgress).collect { log ->
                    _logLines.update { it + log }
                    if (mirrorToCmdLogs) _cmdLogs.update { it + log }
                    if (log.type == LogType.ERROR) ok = false
                }
                ok
            }
            FastbootEngine.PLATFORM_TOOLS -> {
                onProgress(0f)
                _logLines.update { it + LogLine(dateFormat.format(Date()), "[FLASH] Step 1/3: preparing temporary image copy", LogType.INFO) }
                val temp = copyUriToTempFile(fileUri, ".img")
                if (temp == null) {
                    val err = LogLine(dateFormat.format(Date()), "Failed to read selected image to temporary file", LogType.ERROR)
                    _logLines.update { it + err }
                    if (mirrorToCmdLogs) _cmdLogs.update { it + err }
                    return false
                }

                val fastbootPath = fastbootBinaryPath()
                if (fastbootPath == null) {
                    val err = LogLine(dateFormat.format(Date()), "fastboot binary unavailable. Add it to app root assets binaries by ABI.", LogType.ERROR)
                    _logLines.update { it + err }
                    if (mirrorToCmdLogs) _cmdLogs.update { it + err }
                    temp.delete()
                    return false
                }

                _logLines.update { it + LogLine(dateFormat.format(Date()), "[FLASH] Step 2/3: executing platform fastboot from $fastbootPath", LogType.INFO) }
                _logLines.update { it + LogLine(dateFormat.format(Date()), "[FLASH] Note: platform-tools fastboot often cannot access OTG USB on Android; if it hangs at 'waiting for devices', use Embedded Lib engine.", LogType.WARN) }
                var ok = true
                platformToolsManager.runCommand(listOf(fastbootPath, "flash", partition, temp.absolutePath), platformToolsEnv()).collect { log ->
                    _logLines.update { it + log }
                    if (mirrorToCmdLogs) _cmdLogs.update { it + log }
                    if (log.type == LogType.ERROR) ok = false
                }

                temp.delete()
                onProgress(if (ok) 1f else 0f)
                _logLines.update { it + LogLine(dateFormat.format(Date()), if (ok) "[FLASH] Step 3/3: completed" else "[FLASH] Step 3/3: failed", if (ok) LogType.OK else LogType.ERROR) }
                ok
            }
        }
    }

    private suspend fun executeFastbootCommands(commands: List<String>, mirrorToCmdLogs: Boolean): Boolean {
        val state = _deviceState.value as? DeviceState.Fastboot
        if (state == null) {
            val err = LogLine(dateFormat.format(Date()), "Fastboot mode required", LogType.ERROR)
            _logLines.update { it + err }
            if (mirrorToCmdLogs) _cmdLogs.update { it + err }
            return false
        }

        var ok = true
        for (command in commands) {
            when (_fastbootEngine.value) {
                FastbootEngine.LIB_ANDROID -> {
                    if (!fastbootManager.connect(state.device)) {
                        val err = LogLine(dateFormat.format(Date()), "Fastboot command failed: unable to claim USB interface", LogType.ERROR)
                        _logLines.update { it + err }
                        if (mirrorToCmdLogs) _cmdLogs.update { it + err }
                        return false
                    }
                    fastbootManager.execute(command).collect { log ->
                        _logLines.update { it + log }
                        if (mirrorToCmdLogs) _cmdLogs.update { it + log }
                        if (log.type == LogType.ERROR) ok = false
                    }
                }
                FastbootEngine.PLATFORM_TOOLS -> {
                    val fastbootPath = fastbootBinaryPath()
                    if (fastbootPath == null) {
                        val err = LogLine(dateFormat.format(Date()), "fastboot binary unavailable. Add it to app root assets binaries by ABI.", LogType.ERROR)
                        _logLines.update { it + err }
                        if (mirrorToCmdLogs) _cmdLogs.update { it + err }
                        return false
                    }

                    val args = toPlatformFastbootArgs(command)
                    var cmdOk = true
                    _logLines.update { it + LogLine(dateFormat.format(Date()), "[FASTBOOT] Note: platform-tools fastboot often cannot access OTG USB on Android; if it hangs at 'waiting for devices', use Embedded Lib engine.", LogType.WARN) }
                    platformToolsManager.runCommand(listOf(fastbootPath) + args, platformToolsEnv()).collect { log ->
                        _logLines.update { it + log }
                        if (mirrorToCmdLogs) _cmdLogs.update { it + log }
                        if (log.type == LogType.ERROR) cmdOk = false
                    }
                    if (!cmdOk) ok = false
                }
            }
            if (!ok) break
        }
        return ok
    }

    private fun toPlatformFastbootArgs(command: String): List<String> {
        if (command.contains(":")) {
            val split = command.split(":", limit = 2)
            return if (split.size == 2) listOf(split[0], split[1]) else listOf(command)
        }
        return command.split(Regex("""\s+""")).filter { it.isNotBlank() }
    }

    private fun normalizeFastbootCommand(input: String): String? {
        val cmd = input.trim().removePrefix("fastboot ").trim()
        if (cmd.isBlank()) return null
        if (":" in cmd) return cmd

        val lower = cmd.lowercase(Locale.ROOT)
        val firstArg = cmd.substringAfter(' ', "").trim()

        return when {
            lower.startsWith("getvar ") && firstArg.isNotEmpty() -> "getvar:$firstArg"
            lower.startsWith("erase ") && firstArg.isNotEmpty() -> "erase:$firstArg"
            lower.startsWith("format ") && firstArg.isNotEmpty() -> "format:$firstArg"
            lower.startsWith("set_active ") && firstArg.isNotEmpty() -> "set_active:$firstArg"
            lower == "reboot bootloader" -> "reboot-bootloader"
            lower == "reboot" -> "reboot"
            lower == "continue" -> "continue"
            lower.startsWith("flash ") -> null
            else -> cmd
        }
    }

    private fun copyUriToTempFile(uri: Uri, suffix: String): File? {
        return try {
            val context = getApplication<Application>()
            val input = context.contentResolver.openInputStream(uri) ?: run {
                addLog("Failed to open URI stream for temp copy", LogType.ERROR)
                return null
            }
            val temp = File.createTempFile("hypzen_", suffix, context.cacheDir)
            input.use { inputStream ->
                temp.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
            }
            temp
        } catch (e: Exception) {
            addLog("Temp file copy error: ${e.message}", LogType.ERROR)
            null
        }
    }


    private fun abiAliases(abi: String): List<String> {
        return when (abi) {
            "arm64-v8a" -> listOf("arm64-v8a", "arm64", "aarch64")
            "armeabi-v7a" -> listOf("armeabi-v7a", "armv7", "arm")
            else -> listOf(abi)
        }
    }


    private fun platformToolSearchOrder(name: String): List<Pair<String, String>> {
        val search = mutableListOf<Pair<String, String>>()
        val abis = Build.SUPPORTED_ABIS.toList()

        for (abi in abis) {
            for (alias in abiAliases(abi)) {
                search += Pair("$projectRootAssetsPath/$alias/$name", "binaries/$alias/$name")
                search += Pair("$projectRootAssetsPath/binaries/$alias/$name", "binaries/$alias/$name")
                search += Pair("$projectRootPlatformToolsPath/$alias/$name", "platform-tools/$alias/$name")
            }

            search += Pair("$projectRootAssetsPath/${name}_$abi", "binaries/${name}_$abi")
            search += Pair("$projectRootAssetsPath/binaries/${name}_$abi", "binaries/${name}_$abi")
            search += Pair("$projectRootPlatformToolsPath/${name}_$abi", "platform-tools/${name}_$abi")

            search += Pair("$projectRootAssetsPath/$name.$abi", "binaries/$name.$abi")
            search += Pair("$projectRootAssetsPath/binaries/$name.$abi", "binaries/$name.$abi")
            search += Pair("$projectRootPlatformToolsPath/$name.$abi", "platform-tools/$name.$abi")
        }

        search += Pair("$projectRootAssetsPath/$name", "binaries/$name")
        search += Pair("$projectRootAssetsPath/binaries/$name", "binaries/$name")
        search += Pair("$projectRootPlatformToolsPath/$name", "platform-tools/$name")
        return search.distinct()
    }

    private fun detectElfArch(file: File): String {
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(20)
                val read = input.read(header)
                if (read < 20) return "unknown"
                if (
                    header[0] != 0x7F.toByte() ||
                    header[1] != 'E'.code.toByte() ||
                    header[2] != 'L'.code.toByte() ||
                    header[3] != 'F'.code.toByte()
                ) return "non-elf"

                val elfClass = header[4].toInt() and 0xFF
                val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
                when {
                    machine == 183 && elfClass == 2 -> "arm64-v8a"
                    machine == 40 && elfClass == 1 -> "armeabi-v7a"
                    machine == 40 && elfClass == 2 -> "arm"
                    else -> "unknown"
                }
            }
        } catch (_: Exception) {
            "unknown"
        }
    }


    private fun validateBinaryCompatibility(name: String, path: String): Boolean {
        val arch = detectElfArch(File(path))
        val supported32 = Build.SUPPORTED_32_BIT_ABIS.toList()
        val supported64 = Build.SUPPORTED_64_BIT_ABIS.toList()
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        addLog("$name binary selected: $path (arch=$arch, devicePrimaryAbi=$primaryAbi)", LogType.INFO)
        return when (arch) {
            "arm64-v8a" -> {
                if (supported64.isEmpty()) {
                    addLog("$name binary is arm64 but device reports no 64-bit ABI support", LogType.ERROR)
                    false
                } else true
            }
            "armeabi-v7a", "arm" -> {
                if (supported32.isEmpty()) {
                    addLog("$name binary is 32-bit ARM but device is likely 64-bit-only", LogType.ERROR)
                    false
                } else {
                    if (primaryAbi.contains("arm64")) {
                        addLog("$name is using 32-bit binary on 64-bit device; this can be unstable on some ROMs", LogType.WARN)
                    }
                    true
                }
            }
            "non-elf" -> {
                addLog("$name binary is not an ELF executable: $path", LogType.ERROR)
                false
            }
            else -> {
                addLog("Could not verify architecture for $name binary: $path", LogType.WARN)
                true
            }
        }
    }

    private fun platformLibSearchDirs(): List<File> {
        val dirs = mutableListOf<File>()
        for (abi in Build.SUPPORTED_ABIS) {
            for (alias in abiAliases(abi)) {
                dirs += File("$projectRootAssetsPath/$alias/lib")
                dirs += File("$projectRootAssetsPath/binaries/$alias/lib")
                dirs += File("$projectRootPlatformToolsPath/$alias/lib")
            }
        }
        dirs += File("$projectRootAssetsPath/lib")
        dirs += File("$projectRootAssetsPath/binaries/lib")
        dirs += File("$projectRootPlatformToolsPath/lib")
        return dirs.distinctBy { it.absolutePath }
    }

    private fun ensureLibAlias(dir: File, alias: String, target: String) {
        val aliasFile = File(dir, alias)
        val targetFile = File(dir, target)
        if (!aliasFile.exists() && targetFile.exists() && targetFile.isFile) {
            runCatching {
                targetFile.inputStream().use { input ->
                    aliasFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }


    private fun copyProjectRootLibsToInternal(): String? {
        return try {
            val outDir = File(embeddedToolsDir, "lib")
            if (!outDir.exists()) outDir.mkdirs()

            var copiedCount = 0
            for (srcDir in platformLibSearchDirs()) {
                if (!srcDir.exists() || !srcDir.isDirectory) continue
                val libs = srcDir.listFiles()?.filter { it.isFile && (it.name.endsWith(".so") || it.name.contains(".so.")) } ?: emptyList()
                for (src in libs) {
                    val outFile = File(outDir, src.name)
                    val shouldCopy = !outFile.exists() || outFile.length() != src.length() || src.lastModified() > outFile.lastModified()
                    if (shouldCopy) {
                        src.inputStream().use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        copiedCount++
                    }
                }
            }

            ensureLibAlias(outDir, "libz.so.1", "libz.so.1.3.2")
            ensureLibAlias(outDir, "libzstd.so.1", "libzstd.so.1.5.7")

            val finalCount = outDir.listFiles()?.count { it.isFile } ?: 0
            if (finalCount > 0) {
                if (copiedCount > 0) {
                    addLog("Prepared native libs from project root: $finalCount files in ${outDir.absolutePath}", LogType.INFO)
                }
                outDir.absolutePath
            } else null
        } catch (e: Exception) {
            addLog("Failed to prepare native libs from project root: ${e.message}", LogType.ERROR)
            null
        }
    }

    private fun extractAssetLibsToInternal(): String? {
        return try {
            val context = getApplication<Application>()
            val outDir = File(embeddedToolsDir, "lib")
            if (!outDir.exists()) outDir.mkdirs()

            var copiedCount = 0
            for (abi in Build.SUPPORTED_ABIS) {
                for (alias in abiAliases(abi)) {
                    val assetDir = "binaries/$alias/lib"
                    val names = runCatching { context.assets.list(assetDir)?.toList() ?: emptyList() }.getOrDefault(emptyList())
                    for (name in names) {
                        if (!(name.endsWith(".so") || name.contains(".so."))) continue
                        val outFile = File(outDir, name)
                        val shouldCopy = !outFile.exists() || outFile.length() == 0L
                        if (shouldCopy) {
                            context.assets.open("$assetDir/$name").use { input ->
                                outFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            copiedCount++
                        }
                    }
                }
            }

            ensureLibAlias(outDir, "libz.so.1", "libz.so.1.3.2")
            ensureLibAlias(outDir, "libzstd.so.1", "libzstd.so.1.5.7")

            val finalCount = outDir.listFiles()?.count { it.isFile } ?: 0
            if (finalCount > 0) {
                if (copiedCount > 0) {
                    addLog("Prepared native libs from app assets: $finalCount files in ${outDir.absolutePath}", LogType.INFO)
                }
                outDir.absolutePath
            } else null
        } catch (e: Exception) {
            addLog("Failed to prepare native libs from app assets: ${e.message}", LogType.ERROR)
            null
        }
    }

    private fun platformToolsEnv(): Map<String, String> {
        val context = getApplication<Application>()
        val env = mutableMapOf(
            "HOME" to context.filesDir.absolutePath,
            "ANDROID_SDK_HOME" to context.filesDir.absolutePath,
            "TMPDIR" to context.cacheDir.absolutePath,
            // Avoid conflicts with other adb servers (Termux/AndroidIDE) by using a private port.
            "ANDROID_ADB_SERVER_PORT" to "5038",
            "ADB_SERVER_PORT" to "5038"
        )

        val libDir = copyProjectRootLibsToInternal() ?: extractAssetLibsToInternal()
        if (libDir != null) {
            env["LD_LIBRARY_PATH"] = libDir
        } else {
            addLog("No native libs found for platform-tools; command may fail", LogType.WARN)
        }
        return env
    }
    private fun copyProjectRootBinaryToInternal(name: String): String? {
        return try {
            val outDir = embeddedToolsDir
            if (!outDir.exists()) outDir.mkdirs()

            val source = platformToolSearchOrder(name).firstOrNull { File(it.first).exists() && File(it.first).isFile }
                ?: return null

            val src = File(source.first)
            val outFile = File(outDir, name)
            val shouldCopy = !outFile.exists() || outFile.length() != src.length() || src.lastModified() > outFile.lastModified()
            if (shouldCopy) {
                src.inputStream().use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            runCatching { Os.chmod(outFile.absolutePath, 448) } // 0700
            outFile.setExecutable(true, true)
            outFile.setReadable(true, true)
            outFile.setWritable(true, true)
            if (!outFile.canExecute()) {
                addLog("Prepared $name at ${outFile.absolutePath} but it is not executable on this device", LogType.ERROR)
                return null
            }
            if (outFile.exists()) outFile.absolutePath else null
        } catch (e: Exception) {
            addLog("Failed to prepare internal binary from app root ($name): ${e.message}", LogType.ERROR)
            null
        }
    }

    private fun extractAssetBinaryIfNeeded(name: String): String? {
        return try {
            val context = getApplication<Application>()
            val outDir = embeddedToolsDir
            if (!outDir.exists()) outDir.mkdirs()

            val source = platformToolSearchOrder(name).firstOrNull { candidate ->
                runCatching { context.assets.open(candidate.second).close(); true }.getOrDefault(false)
            } ?: return null

            val outFile = File(outDir, name)
            val shouldCopy = !outFile.exists() || outFile.length() == 0L
            if (shouldCopy) {
                context.assets.open(source.second).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            runCatching { Os.chmod(outFile.absolutePath, 448) } // 0700
            outFile.setExecutable(true, true)
            outFile.setReadable(true, true)
            outFile.setWritable(true, true)
            if (!outFile.canExecute()) {
                addLog("Extracted $name at ${outFile.absolutePath} but it is not executable on this device", LogType.ERROR)
                return null
            }
            if (outFile.exists()) outFile.absolutePath else null
        } catch (e: Exception) {
            addLog("Failed to extract asset binary ($name): ${e.message}", LogType.ERROR)
            null
        }
    }

    private fun fastbootBinaryPath(): String? {
        val nativePath = nativeExecBinaryPath("fastboot")
        val path = nativePath ?: copyProjectRootBinaryToInternal("fastboot") ?: extractAssetBinaryIfNeeded("fastboot")
        if (path == null) {
            addLog("No compatible fastboot binary found for ABIs: ${Build.SUPPORTED_ABIS.joinToString()}", LogType.ERROR)
            return null
        }
        if (!validateBinaryCompatibility("fastboot", path)) return null
        return path
    }

    private fun adbBinaryPath(): String? {
        val nativePath = nativeExecBinaryPath("adb")
        val path = nativePath ?: copyProjectRootBinaryToInternal("adb") ?: extractAssetBinaryIfNeeded("adb")
        if (path == null) {
            addLog("No compatible adb binary found for ABIs: ${Build.SUPPORTED_ABIS.joinToString()}", LogType.ERROR)
            return null
        }
        if (!validateBinaryCompatibility("adb", path)) return null
        return path
    }

    private fun nativeExecBinaryPath(tool: String): String? {
        val context = getApplication<Application>()
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return null
        val name = when (tool) {
            "adb" -> "libadb_exec.so"
            "fastboot" -> "libfastboot_exec.so"
            else -> return null
        }
        val file = File(nativeDir, name)
        return if (file.exists() && file.isFile && file.canExecute() && file.length() > 0L) {
            file.absolutePath
        } else null
    }

    fun toggleTheme() {
        cycleThemeMode()
    }

    fun showConfirm(title: String, subtitle: String, command: String, isDangerous: Boolean, onConfirm: () -> Unit) {
        _confirmSheetState.value = ConfirmSheetState(title, subtitle, command, isDangerous, onConfirm)
    }

    fun dismissConfirm() {
        _confirmSheetState.value = null
    }

    override fun onCleared() {
        super.onCleared()
        usbMonitor.stop()
        adbManager.disconnect()
        fastbootManager.disconnect()
    }

    data class ConfirmSheetState(
        val title: String,
        val subtitle: String,
        val command: String,
        val isDangerous: Boolean,
        val onConfirm: () -> Unit
    )
}
