package net.yaycraft.yunit.database

interface IMigrationRunner {
    /** Tüm bekleyen migrationları çalıştırır */
    fun migrate()
}
