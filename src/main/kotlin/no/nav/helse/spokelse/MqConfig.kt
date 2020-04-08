package no.nav.helse.spokelse

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import com.ibm.msg.client.wmq.compat.base.internal.MQC
import javax.jms.Connection

fun createConnection(env: Environment.MQ): Connection = MQConnectionFactory().run {
    hostName = env.hostname
    port = env.port
    channel = env.channel
    queueManager = env.queueManager
    transportType = WMQConstants.WMQ_CM_CLIENT
    ccsid = WMQConstants.CCSID_UTF8
    setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQC.MQENC_NATIVE)
    setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, WMQConstants.CCSID_UTF8)
    createConnection(env.username, env.password)
}
