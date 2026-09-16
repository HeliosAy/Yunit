package net.yaycraft.yunit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.yaycraft.yunit.api.YunitAPIImpl
import net.yaycraft.yunit.api.YunitProvider
import net.yaycraft.yunit.cache.CaffeineAccountCache
import net.yaycraft.yunit.command.PlayerResolver
import net.yaycraft.yunit.hook.HookManager
import net.yaycraft.yunit.command.admin.AdminCommandManager
import net.yaycraft.yunit.command.admin.GiveCommand
import net.yaycraft.yunit.command.admin.TakeCommand
import net.yaycraft.yunit.command.admin.SetCommand
import net.yaycraft.yunit.command.admin.LookupCommand
import net.yaycraft.yunit.command.player.PlayerCommandManager
import net.yaycraft.yunit.command.player.BalanceCommand
import net.yaycraft.yunit.config.ConfigManager
import net.yaycraft.yunit.config.LangManager
import net.yaycraft.yunit.database.HikariDatabaseProvider
import net.yaycraft.yunit.database.SQLMigrator
import net.yaycraft.yunit.listener.PlayerConnectionListener
import net.yaycraft.yunit.repository.MySQLAccountRepository
import net.yaycraft.yunit.repository.MySQLPendingDeliveryRepository
import net.yaycraft.yunit.repository.MySQLTransactionRepository
import net.yaycraft.yunit.redis.RedisManager
import net.yaycraft.yunit.service.BalanceChangeNotifier
import net.yaycraft.yunit.service.EconomyServiceImpl
import net.yaycraft.yunit.service.PlayerGuard
import net.yaycraft.yunit.service.SafePurchaseServiceImpl
import net.yaycraft.yunit.service.StartupRecoveryServiceImpl
import net.yaycraft.yunit.util.MessageUtil
import org.bukkit.plugin.java.JavaPlugin

class Yunit : JavaPlugin() {

    private lateinit var dbProvider: HikariDatabaseProvider
    private lateinit var pluginScope: CoroutineScope
    private var redisManager: RedisManager? = null
    private var healthCheckJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onEnable() {
        // Coroutine Scope oluştur
        pluginScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // Config Yükle
        val configManager = ConfigManager(this)
        val pluginConfig = configManager.load()

        // Veritabanı Başlat
        dbProvider = HikariDatabaseProvider(logger)
        try {
            dbProvider.initialize(pluginConfig.database)
        } catch (e: Exception) {
            server.pluginManager.disablePlugin(this)
            return
        }

        // Migration Çalıştır
        try {
            SQLMigrator(dbProvider, logger).migrate()
        } catch (e: Exception) {
            logger.severe("Veritabanı migration başarısız, Yunit devre dışı bırakılıyor.")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Dil Dosyası (LangManager)
        val langManager = LangManager(this)
        langManager.load()
        val messageUtil = MessageUtil(langManager)

        // Repositories
        val accountRepo = MySQLAccountRepository()
        val transactionRepo = MySQLTransactionRepository()
        val pendingRepo = MySQLPendingDeliveryRepository()

        // Cache
        val cache = CaffeineAccountCache(pluginConfig.cache)

        // Redis
        redisManager = RedisManager(pluginConfig.redis, logger) { uuid ->
            cache.invalidate(uuid)
        }
        redisManager?.connect()

        // Servislerin ortak kullandığı parçalar
        val dbDispatcher = Dispatchers.IO.limitedParallelism(pluginConfig.database.poolSize)
        val guard = PlayerGuard(pluginConfig.safety.transactionCooldownMs)
        val notifier = BalanceChangeNotifier(cache, redisManager)

        // Services
        val economyService = EconomyServiceImpl(
            dbProvider, accountRepo, transactionRepo, cache, guard, notifier, dbDispatcher, pluginConfig, logger
        )

        val purchaseService = SafePurchaseServiceImpl(
            dbProvider, accountRepo, transactionRepo, pendingRepo, guard, notifier, dbDispatcher, pluginConfig, logger
        )

        val recoveryService = StartupRecoveryServiceImpl(
            dbProvider, accountRepo, transactionRepo, pendingRepo, notifier, pluginConfig, logger
        )

        // Recovery, API açılmadan (yeni satın almalar gelmeden) önce tamamlanır
        recoveryService.recoverPendingDeliveries()

        // Periyodik veritabanı sağlık kontrolü
        healthCheckJob = pluginScope.launch(Dispatchers.IO) {
            val intervalMs = pluginConfig.safety.healthCheckIntervalSeconds * 1000
            while (isActive) {
                delay(intervalMs)
                dbProvider.checkHealth()
            }
        }

        // API Kayıt Et
        val apiImpl = YunitAPIImpl(economyService, purchaseService, dbProvider, pluginConfig, pluginScope)
        YunitProvider.register(apiImpl)

        // Dış Eklenti Entegrasyonları
        val hookManager = HookManager(server, logger, economyService, pluginConfig, pluginScope, pluginMeta.version)
        hookManager.registerHooks()

        // Dinleyiciler
        server.pluginManager.registerEvents(PlayerConnectionListener(economyService, cache, pluginScope), this)

        // Oyuncu Komutları (/yunit)
        val playerSubCommands = listOf(
            BalanceCommand(economyService, pluginConfig, messageUtil, pluginScope)
        )
        val playerCommandManager = PlayerCommandManager(messageUtil, playerSubCommands)
        getCommand("yunit")?.apply {
            setExecutor(playerCommandManager)
            tabCompleter = playerCommandManager
        }

        // Admin Komutları (/yunitadmin)
        val resolver = PlayerResolver(economyService)
        val adminSubCommands = listOf(
            GiveCommand(economyService, pluginConfig, messageUtil, resolver, pluginScope),
            TakeCommand(economyService, pluginConfig, messageUtil, resolver, pluginScope),
            SetCommand(economyService, pluginConfig, messageUtil, resolver, pluginScope),
            LookupCommand(economyService, pluginConfig, messageUtil, resolver, pluginScope)
        )
        val adminCommandManager = AdminCommandManager(messageUtil, adminSubCommands)
        getCommand("yunitadmin")?.apply {
            setExecutor(adminCommandManager)
            tabCompleter = adminCommandManager
        }

        if (!server.onlineMode) {
            logger.info("Offline mod algılandı: hesaplar sunucunun verdiği UUID ile tutulur...")
        }
        logger.info("Sunucu adı: '${pluginConfig.serverName}'")
        logger.info("Yunit başarıyla başlatıldı! (Sürüm: ${pluginMeta.version})")
    }

    override fun onDisable() {
        logger.info("Yunit kapatılıyor...")

        // API temizle
        YunitProvider.unregister()

        if (::pluginScope.isInitialized) {
            healthCheckJob?.cancel()

            // Devam eden işlemlerin (ör. satın alma iadesi) bitmesi için kısa süre bekle
            val running = pluginScope.coroutineContext[Job]?.children?.toList().orEmpty()
            if (running.isNotEmpty()) {
                runBlocking {
                    withTimeoutOrNull(3000) { running.joinAll() }
                }
            }
            pluginScope.cancel("Plugin disabled")
        }

        // Veritabanı bağlantılarını kapat
        if (::dbProvider.isInitialized) {
            dbProvider.shutdown()
        }

        // Redis bağlantılarını kapat
        redisManager?.shutdown()
    }
}
