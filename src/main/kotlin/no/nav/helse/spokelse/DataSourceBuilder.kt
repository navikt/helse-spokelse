package no.nav.helse.spokelse

import com.zaxxer.hikari.HikariConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration as createDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

internal class DataSourceBuilder(env: Map<String, String>) {
    private val databaseName =
        requireNotNull(env["DATABASE_NAME"]) { "database name must be set if jdbc url is not provided" }
    private val databaseHost =
        requireNotNull(env["DATABASE_HOST"]) { "database host must be set if jdbc url is not provided" }
    private val databasePort =
        requireNotNull(env["DATABASE_PORT"]) { "database port must be set if jdbc url is not provided" }
    private val vaultMountPath = env["VAULT_MOUNTPATH"]

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = env["DATABASE_JDBC_URL"] ?: String.format(
            "jdbc:postgresql://%s:%s/%s", databaseHost, databasePort,
            databaseName
        )

        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }

    fun getDataSource(role: Role = Role.User) =
        createDataSource(hikariConfig, vaultMountPath, role.asRole(databaseName))

    fun migrate() {
        runMigration(getDataSource(Role.Admin), "SET ROLE \"${Role.Admin.asRole(databaseName)}\"")
    }

    private fun runMigration(dataSource: DataSource, initSql: String? = null) =
        Flyway.configure()
            .dataSource(dataSource)
            .initSql(initSql)
            .load()
            .migrate()

    enum class Role {
        Admin, User, ReadOnly;

        fun asRole(databaseName: String) = "$databaseName-${name.toLowerCase()}"
    }
}
