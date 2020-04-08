package no.nav.helse.spokelse

import com.zaxxer.hikari.HikariConfig
import org.flywaydb.core.Flyway
import javax.sql.DataSource
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration as createDataSource

internal class DataSourceBuilder(private val env: Environment.DB) {

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://${env.host}:${env.port}/${env.name}"
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }

    fun getDataSource(role: Role = Role.User) =
        createDataSource(hikariConfig, env.vaultMountPath, role.asRole(env.name))

    fun migrate() {
        runMigration(getDataSource(Role.Admin), "SET ROLE \"${Role.Admin.asRole(env.name)}\"")
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
