package akka.amqp

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import com.typesafe.config.Config

case class AmqpSettings(
  addresses: Seq[String],
  user: String,
  pass: String,
  vhost: String,
  amqpHeartbeat: FiniteDuration,
  maxReconnectDelay: Duration,
  channelThreads: Int,
  interactionTimeout: Duration,
  channelCreationTimeout: Duration,
  channelReconnectTimeout: Duration,
  publisherConfirmTimeout: FiniteDuration)

object AmqpSettings {

  def apply(config: Config): AmqpSettings =
    AmqpSettings(
      addresses = config.getStringList("addresses").asScala.toSeq,
      user = config.getString("user"),
      pass = config.getString("pass"),
      vhost = config.getString("vhost"),
      amqpHeartbeat = DurationLong(config.getMilliseconds("heartbeat")).milli,
      maxReconnectDelay = DurationLong(config.getMilliseconds("max-reconnect-delay")).milli,
      channelThreads = config.getInt("channel-threads"),
      interactionTimeout = DurationLong(config.getMilliseconds("interaction-timeout")).milli,
      channelCreationTimeout = DurationLong(config.getMilliseconds("channel-creation-timeout")).milli,
      channelReconnectTimeout = DurationLong(config.getMilliseconds("channel-reconnect-timeout")).milli,
      publisherConfirmTimeout = DurationLong(config.getMilliseconds("publisher-confirm-timeout")).milli)
}
