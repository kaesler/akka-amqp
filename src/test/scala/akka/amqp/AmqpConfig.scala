package akka.amqp

import com.typesafe.config.ConfigFactory

object AmqpConfig {

  object Valid {
    val configString = """
      akka.amqp.default {
       addresses           = ["localhost:5672"]
            user                = "guest"
            pass                = "guest"
            vhost               = "/"
            heartbeat           = 30s
            max-reconnect-delay = 60s
            channel-threads     = 5

            interaction-timeout         = 5000
            channel-creation-timeout    = 5000
            channel-reconnect-timeout   = 5000
            publisher-confirm-timeout   = 5000
  }
  """
    val config = ConfigFactory.parseString(configString)

    val settings = AmqpSettings(config.getConfig("akka.amqp.default"))
  }
  object Invalid {
    val config = ConfigFactory.parseString("""
      akka.amqp.default {
       addresses           = ["invalid-test-connection-no-op:1234"]
  }
        """).withFallback(Valid.config)
    val settings = AmqpSettings(config.getConfig("akka.amqp.default"))
  }
}
