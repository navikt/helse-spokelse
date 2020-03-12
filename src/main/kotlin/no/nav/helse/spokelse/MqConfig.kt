package no.nav.helse.spokelse

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import com.ibm.msg.client.wmq.compat.base.internal.MQC
import javax.jms.Connection

fun createConnection(
    hostname: String,
    port: Int,
    channel: String,
    queueManager: String,
    username: String,
    password: String
): Connection = MQConnectionFactory().let {
    it.hostName = hostname
    it.port = port
    it.channel = channel
    it.queueManager = queueManager
    it.transportType = WMQConstants.WMQ_CM_CLIENT
    it.ccsid = WMQConstants.CCSID_UTF8
    it.setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQC.MQENC_NATIVE)
    it.setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, WMQConstants.CCSID_UTF8)
    it.createConnection(username, password)
}