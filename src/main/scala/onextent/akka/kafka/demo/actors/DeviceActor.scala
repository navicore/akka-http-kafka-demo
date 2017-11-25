package onextent.akka.kafka.demo.actors

import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import onextent.akka.kafka.demo.actors.DeviceActor.{Ack, Get, GetAssessments}
import onextent.akka.kafka.demo.models.{Assessment, Device}

object DeviceActor {
  def props(device: Device)(implicit timeout: Timeout) =
    Props(new DeviceActor(device))
  final case class Get()
  final case class Ack(device: Device)
  final case class GetAssessments()
}

class DeviceActor(device: Device) extends Actor with LazyLogging {

  def receive: Receive = hasState(Map[String, Assessment]())

  def hasState(assessments: Map[String, Assessment]): Receive = {

    case assessment: Assessment =>
      context become hasState(assessments + (assessment.name -> assessment))
      sender() ! Ack(device)

    case Get =>
      sender() ! device

    case GetAssessments =>
      sender() ! assessments.values.toList
  }

}