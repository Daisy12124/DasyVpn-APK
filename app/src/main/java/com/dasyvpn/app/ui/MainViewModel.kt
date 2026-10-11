package com.dasyvpn.app.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dasyvpn.app.DasyApp
import com.dasyvpn.app.core.XrayConfigBuilder
import com.dasyvpn.app.core.XrayCoreManager
import com.dasyvpn.app.data.ConfigStore
import com.dasyvpn.app.data.LinkParser
import com.dasyvpn.app.model.ServerProfile
import com.dasyvpn.app.model.Subscription
import com.dasyvpn.app.model.VpnState
import com.dasyvpn.app.vpn.DasyVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val store: ConfigStore = (app as DasyApp).store

    data class UiState(
        val vpnState: VpnState = VpnState.DISCONNECTED,
        val selectedServer: ServerProfile? = null,
        val servers: List<ServerProfile> = emptyList(),
        val subscriptions: List<Subscription> = emptyList(),
        val upSpeed: Long = 0,
        val downSpeed: Long = 0,
        val totalUp: Long = 0,
        val totalDown: Long = 0,
        val connectedSeconds: Long = 0,
        val errorMessage: String? = null,
        val bypassIran: Boolean = true,
        val perAppProxy: Boolean = false,
        val perAppBypassMode: Boolean = false,
        val perAppPackages: Set<String> = emptySet(),
        val latencyResults: Map<String, Long> = emptyMap(),
        val freeLoading: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var statsJob: Job? = null
    private var connectedAt = 0L
    private var lastUp = 0L
    private var lastDown = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        refreshFromStore()
        // وضعیت فعلی هسته را بازتاب بده
        if (XrayCoreManager.isRunning()) {
            _ui.update { it.copy(vpnState = VpnState.CONNECTED) }
            connectedAt = System.currentTimeMillis()
            startStatsLoop()
        }
        viewModelScope.launch {
            XrayCoreManager.statusEvents.collect { ev ->
                if (ev == "connected") {
                    _ui.update { it.copy(vpnState = VpnState.CONNECTED, errorMessage = null) }
                    connectedAt = System.currentTimeMillis()
                    startStatsLoop()
                }
            }
        }
    }

    fun refreshFromStore() {
        _ui.update {
            it.copy(
                servers = store.servers,
                subscriptions = store.subscriptions,
                selectedServer = store.selectedServer(),
                bypassIran = store.bypassIran,
                perAppProxy = store.perAppProxy,
                perAppBypassMode = store.perAppBypassMode,
                perAppPackages = store.perAppPackages
            )
        }
    }

    // ---------- اتصال ----------

    fun toggleConnect() {
        val state = _ui.value.vpnState
        when (state) {
            VpnState.DISCONNECTED, VpnState.ERROR -> connect()
            VpnState.CONNECTED, VpnState.CONNECTING, VpnState.STOPPING -> disconnect()
        }
    }

    private fun connect() {
        val profile = store.selectedServer()
        if (profile == null) {
            _ui.update { it.copy(errorMessage = "ابتدا یک سرور اضافه کنید") }
            return
        }
        val prepare = VpnService.prepare(getApplication())
        if (prepare != null) {
            _ui.update { it.copy(errorMessage = "مجوز VPN لازم است") }
            _prepareIntent.value = prepare
            return
        }
        doConnect()
    }

    private val _prepareIntent = MutableStateFlow<Intent?>(null)
    val prepareIntent: StateFlow<Intent?> = _prepareIntent.asStateFlow()

    fun consumePrepareIntent() {
        _prepareIntent.value = null
        doConnect()
    }

    private fun doConnect() {
        _ui.update { it.copy(vpnState = VpnState.CONNECTING, errorMessage = null) }
        store.wantConnected = true
        DasyVpnService.connect()
    }

    fun disconnect() {
        _ui.update { it.copy(vpnState = VpnState.STOPPING) }
        DasyVpnService.disconnect()
        _ui.update {
            it.copy(
                vpnState = VpnState.DISCONNECTED,
                upSpeed = 0, downSpeed = 0,
                connectedSeconds = 0
            )
        }
        stopStatsLoop()
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        lastUp = 0; lastDown = 0
        statsJob = viewModelScope.launch {
            while (true) {
                val t = XrayCoreManager.pollTraffic()
                // کتابخانه مقدار تجمعی را ریست می‌کند؛ هر نمونه = سرعت تقریبی همان بازه
                val upSpeed = t.uplink
                val downSpeed = t.downlink
                lastUp = t.uplink; lastDown = t.downlink
                _ui.update {
                    it.copy(
                        upSpeed = upSpeed,
                        downSpeed = downSpeed,
                        totalUp = it.totalUp + upSpeed,
                        totalDown = it.totalDown + downSpeed,
                        connectedSeconds = (System.currentTimeMillis() - connectedAt) / 1000,
                        vpnState = if (XrayCoreManager.isRunning()) VpnState.CONNECTED else VpnState.DISCONNECTED
                    )
                }
                if (!XrayCoreManager.isRunning() && _ui.value.vpnState == VpnState.CONNECTED) {
                    _ui.update { it.copy(vpnState = VpnState.DISCONNECTED) }
                    break
                }
                delay(1000)
            }
        }
    }

    private fun stopStatsLoop() {
        statsJob?.cancel()
        statsJob = null
    }

    // ---------- سرورها ----------

    fun addServerLink(link: String) {
        val profile = LinkParser.parseLink(link.trim())
        if (profile == null) {
            _ui.update { it.copy(errorMessage = "لینک نامعتبر است") }
            return
        }
        val list = store.servers + profile
        store.servers = list
        if (store.selectedServerId == null) {
            store.selectedServerId = profile.id
        }
        refreshFromStore()
        _ui.update { it.copy(errorMessage = null) }
    }

    fun selectServer(id: String) {
        store.selectedServerId = id
        refreshFromStore()
    }

    fun removeServer(id: String) {
        val list = store.servers.filterNot { it.id == id }
        store.servers = list
        if (store.selectedServerId == id) {
            store.selectedServerId = list.firstOrNull()?.id
        }
        refreshFromStore()
    }

    fun testLatency(profile: ServerProfile) {
        viewModelScope.launch {
            val config = try {
                XrayConfigBuilder.build(profile.raw, store.bypassIran)
            } catch (_: Exception) {
                return@launch
            }
            val delay = XrayCoreManager.measureDelay(config)
            _ui.update {
                it.copy(latencyResults = it.latencyResults + (profile.id to delay))
            }
        }
    }

    // ---------- اشتراک ----------

    fun addSubscription(name: String, url: String) {
        val sub = Subscription(id = UUID.randomUUID().toString(), name = name.ifBlank { "اشتراک" }, url = url.trim())
        store.subscriptions = store.subscriptions + sub
        refreshFromStore()
        updateSubscription(sub)
    }

    fun removeSubscription(id: String) {
        store.subscriptions = store.subscriptions.filterNot { it.id == id }
        store.servers = store.servers.filterNot { it.subscriptionId == id }
        refreshFromStore()
    }

    fun updateSubscription(sub: Subscription) {
        if (sub.id == freeSubId) {
            fetchFreeConfigs()
            return
        }
        viewModelScope.launch {
            try {
                val req = Request.Builder().url(sub.url).build()
                val body = withContext(Dispatchers.IO) {
                    http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) null else resp.body?.string()
                    }
                }
                if (body.isNullOrBlank()) {
                    _ui.update { it.copy(errorMessage = "اشتراک قابل دریافت نشد") }
                    return@launch
                }
                val parsed = LinkParser.parseSubscription(body)
                    .map { it.copy(subscriptionId = sub.id) }
                val others = store.servers.filterNot { it.subscriptionId == sub.id }
                store.servers = others + parsed
                refreshFromStore()
                _ui.update { it.copy(errorMessage = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(errorMessage = "خطا در دریافت اشتراک: ${e.message}") }
            }
        }
    }

    // ---------- کانفیگ رایگان عمومی ----------

    /** لیست‌های عمومی به‌روزرسانی‌شده در GitHub — بدون نیاز به VPS */
    private val freeSources = listOf(
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/sub.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge.txt",
        "https://raw.githubusercontent.com/roosterkid/openproxylist/main/V2RAY_RAW.txt"
    )

    private val freeSubId = "free_public"

    fun fetchFreeConfigs() {
        if (_ui.value.freeLoading) return
        viewModelScope.launch {
            _ui.update { it.copy(freeLoading = true) }
            val collected = LinkedHashMap<String, ServerProfile>()
            var okSources = 0

            for (url in freeSources) {
                try {
                    val body = withContext(Dispatchers.IO) {
                        val req = Request.Builder()
                            .url(url)
                            .header("User-Agent", "DASY-VPN/1.0")
                            .build()
                        http.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) null else resp.body?.string()
                        }
                    }
                    if (body.isNullOrBlank()) continue
                    okSources++
                    for (p in LinkParser.parseSubscription(body)) {
                        val key = "${p.protocol}|${p.host}|${p.port}|${p.raw.substringBefore('#')}"
                        if (key !in collected) {
                            collected[key] = p.copy(subscriptionId = freeSubId)
                        }
                    }
                } catch (_: Exception) {
                }
            }

            if (collected.isEmpty()) {
                _ui.update {
                    it.copy(
                        freeLoading = false,
                        errorMessage = "دریافت کانفیگ رایگان ناموفق بود؛ اینترنت را چک کنید"
                    )
                }
                return@launch
            }

            // اولویت با VLESS و Reality، سپس بقیه — حداکثر ۵۰ تا
            val priority = mapOf("vless" to 0, "trojan" to 1, "vmess" to 2, "ss" to 3)
            val picked = collected.values
                .sortedBy { priority[it.protocol] ?: 9 }
                .take(50)

            val others = store.servers.filterNot { it.subscriptionId == freeSubId }
            store.servers = others + picked

            if (store.selectedServerId == null) {
                store.selectedServerId = picked.firstOrNull()?.id
            }

            // ثبت به‌عنوان یک «اشتراک» تا بعداً قابل به‌روزرسانی/حذف باشد
            val subs = store.subscriptions.filterNot { it.id == freeSubId } +
                Subscription(
                    id = freeSubId,
                    name = "کانفیگ‌های رایگان عمومی",
                    url = "فهرست‌های عمومی GitHub"
                )
            store.subscriptions = subs

            refreshFromStore()
            _ui.update {
                it.copy(
                    freeLoading = false,
                    errorMessage = "${picked.size} کانفیگ رایگان از $okSources منبع دریافت شد"
                )
            }
        }
    }

    // ---------- تنظیمات ----------

    fun setBypassIran(v: Boolean) {
        store.bypassIran = v
        refreshFromStore()
    }

    fun setPerAppProxy(v: Boolean) {
        store.perAppProxy = v
        refreshFromStore()
    }

    fun setPerAppBypassMode(v: Boolean) {
        store.perAppBypassMode = v
        refreshFromStore()
    }

    fun togglePerAppPackage(pkg: String) {
        val cur = store.perAppPackages.toMutableSet()
        if (!cur.add(pkg)) cur.remove(pkg)
        store.perAppPackages = cur
        refreshFromStore()
    }

    fun installedApps(): List<ApplicationInfo> {
        val pm = getApplication<Application>().packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    fun clearError() {
        _ui.update { it.copy(errorMessage = null) }
    }

    val logs: StateFlow<List<String>> = XrayCoreManager.logs
}
