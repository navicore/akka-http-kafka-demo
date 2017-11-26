package onextent.iot.server.demo

import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, ProducerSettings}
import akka.pattern.AskTimeoutException
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}

import scala.concurrent.ExecutionContextExecutor

object Conf extends Conf with LazyLogging {

  implicit val actorSystem: ActorSystem = ActorSystem("akka_kafka_demo")
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher

  val decider: Supervision.Decider = {
    case _: AskTimeoutException =>
      // might want to try harder, retry w/backoff if the actor is really supposed to be there
      logger.warn(s"decider discarding message in order to resume")
      Supervision.Resume
    case e: Throwable =>
      logger.error(s"decider can not decide: $e", e)
      Supervision.Stop
    case e =>
      logger.error(s"decider can not decide: $e")
      Supervision.Stop
  }

  implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(actorSystem).withSupervisionStrategy(decider))

  val consumerSettings: ConsumerSettings[Array[Byte], String] =
    ConsumerSettings(actorSystem,
                     new ByteArrayDeserializer,
                     new StringDeserializer)
      .withBootstrapServers(bootstrap)
      .withGroupId(consumerGroup)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetResetDefault)

  val producerSettings: ProducerSettings[Array[Byte], String] =
    ProducerSettings(actorSystem, new ByteArraySerializer, new StringSerializer)
      .withBootstrapServers(bootstrap)

}

trait Conf {

  val conf: Config = ConfigFactory.load()

  val appName: String = conf.getString("main.appName")
  val bootstrap: String = conf.getString("kafka.bootstrap")
  val consumerGroup: String = conf.getString("kafka.consumerGroup")

  val observationsTopic: String = conf.getString("kafka.topics.observations")
  val deviceAssessmentsTopic: String = conf.getString("kafka.topics.deviceAssessments")
  val locationAssessmentsTopic: String = conf.getString("kafka.topics.locationAssessments")
  val fleetAssessmentsTopic: String = conf.getString("kafka.topics.fleetAssessments")

  val offsetResetDefault: String = conf.getString("kafka.offsetResetDefault")

  val parallelism: Int = conf.getInt("kafka.parallelism")

}
