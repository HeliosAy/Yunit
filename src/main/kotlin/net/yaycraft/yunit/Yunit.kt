package net.yaycraft.yunit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.yaycraft.yunit.api.YunitAPIImpl
import net.yaycraft.yunit.api.YunitProvider
import net.yaycraft.yunit.cache.CaffeineAccountCache
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
import net.yaycraft.yunit.service.EconomyServiceImpl
import net.yaycraft.yunit.service.SafePurchaseServiceImpl
import net.yaycraft.yunit.service.StartupRecoveryServiceImpl
import net.yaycraft.yunit.util.MessageUtil
import org.bukkit.plugin.java.JavaPlugin

class Yunit : JavaPlugin() {

    private lateinit var dbProvider: HikariDatabaseProvider
    private lateinit var pluginScope: CoroutineScope

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
            logger.severe("Veritabanı başlatılamadı! Eklenti kapatılıyor...")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Migration Çalıştır
        val migrator = SQLMigrator(dbProvider, logger)
        migrator.migrate()

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

        // Services
        val economyService = EconomyServiceImpl(
            dbProvider, accountRepo, transactionRepo, cache, pluginConfig, logger
        )
        
        val purchaseService = SafePurchaseServiceImpl(
            dbProvider, accountRepo, transactionRepo, pendingRepo, cache, pluginConfig, logger
        )
        
        val recoveryService = StartupRecoveryServiceImpl(
            dbProvider, accountRepo, transactionRepo, pendingRepo, cache, pluginConfig, logger
        )

        // API Kayıt Et
        val apiImpl = YunitAPIImpl(economyService, purchaseService, dbProvider, pluginScope)
        YunitProvider.register(apiImpl)

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
        val adminSubCommands = listOf(
            GiveCommand(economyService, pluginConfig, messageUtil, pluginScope),
            TakeCommand(economyService, pluginConfig, messageUtil, pluginScope),
            SetCommand(economyService, pluginConfig, messageUtil, pluginScope),
            LookupCommand(economyService, pluginConfig, messageUtil, pluginScope)
        )
        val adminCommandManager = AdminCommandManager(messageUtil, adminSubCommands)
        getCommand("yunitadmin")?.apply {
            setExecutor(adminCommandManager)
            tabCompleter = adminCommandManager
        }

        // Startup Recovery Başlat
        recoveryService.recoverPendingDeliveries()

        logger.info("Yunit başarıyla başlatıldı! (Sürüm: ${pluginMeta.version})")
    }

    override fun onDisable() {
        logger.info("Yunit kapatılıyor...")
        
        // API temizle
        YunitProvider.unregister()
        
        // Coroutineleri iptal et
        if (::pluginScope.isInitialized) {
            pluginScope.cancel("Plugin disabled")
        }
        
        // Veritabanı bağlantılarını kapat
        if (::dbProvider.isInitialized) {
            dbProvider.shutdown()
        }
    }
}
