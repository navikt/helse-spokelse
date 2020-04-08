package no.nav.helse.spokelse

class Environment(
    val raw: Map<String, String>,
    val mq: MQ,
    val db: DB,
    val auth: Auth
) {
    constructor(raw: Map<String, String>) : this(
        raw = raw,
        mq = MQ(
            hostname = raw.getValue("MQ_HOSTNAME"),
            port = raw.getValue("MQ_PORT").toInt(),
            channel = raw.getValue("MQ_CHANNEL"),
            queueManager = raw.getValue("MQ_QUEUE_MANAGER"),
            username = raw.getValue("MQ_USERNAME"),
            password = raw.getValue("MQ_PASSWORD"),
            arenaOutput = raw.getValue("MQ_ARENA_OUTPUT"),
            arenaInputKvittering = raw.getValue("MQ_ARENA_INPUT_KVITTERING")
        ),
        db = DB(
            name = raw.getValue("DATABASE_NAME"),
            host = raw.getValue("DATABASE_HOST"),
            port = raw.getValue("DATABASE_PORT").toInt(),
            vaultMountPath = raw.getValue("DATABASE_VAULT_MOUNT_PATH")
        ),
        auth = Auth(
            name = "ourissuer",
            acceptedAudience = raw.getValue("ACCEPTED_AUDIENCE"),
            discoveryUrl = raw.getValue("DISCOVERY_URL"),
            requiredGroup = raw.getValue("REQUIRED_GROUP")
        )
    )

    class MQ(
        val hostname: String,
        val port: Int,
        val channel: String,
        val queueManager: String,
        val username: String,
        val password: String,
        val arenaOutput: String,
        val arenaInputKvittering: String
    )

    class DB(
        val name: String,
        val host: String,
        val port: Int,
        val vaultMountPath: String
    )

    class Auth(
        val name: String,
        val acceptedAudience: String,
        val discoveryUrl: String,
        val requiredGroup: String
    )
}

