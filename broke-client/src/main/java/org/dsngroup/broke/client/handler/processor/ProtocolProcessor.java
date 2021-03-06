/*
 * Copyright (c) 2017-2018 Dependable Network and System Lab, National Taiwan University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dsngroup.broke.client.handler.processor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import org.dsngroup.broke.client.ClientContext;
import org.dsngroup.broke.client.handler.callback.IMessageCallbackHandler;
import org.dsngroup.broke.client.exception.ConnectLostException;
import org.dsngroup.broke.client.metadata.ClientSession;
import org.dsngroup.broke.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * The protocol processor of the client.
 * Process inbound messages from the broker.
 */
public class ProtocolProcessor {

    private ClientSession clientSession;

    private static final Logger logger = LoggerFactory.getLogger(ProtocolProcessor.class);

    private IMessageCallbackHandler messageCallbackHandler;

    /**
     * Setter for the message callback handler.
     * @param messageCallbackHandler User-defined message callback handler.
     */
    public void setMessageCallbackHandler(IMessageCallbackHandler messageCallbackHandler) {
        this.messageCallbackHandler = messageCallbackHandler;
    }

    /**
     * Handle CONNACK.
     * @param ctx {@see ChannelHandlerContext}
     * @param mqttConnAckMessage CONNACK message from broker
     */
    public void processConnAck(ChannelHandlerContext ctx, MqttConnAckMessage mqttConnAckMessage) throws Exception {
        if (mqttConnAckMessage.variableHeader().connectReturnCode() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
            // nop, currently.
        } else {
            logger.error("[Connect] Connection denied, close the channel.");
            ctx.channel().close();
            messageCallbackHandler.connectionLost(new ConnectLostException("CONNECT_DENIED"));
        }
    }

    /**
     * Handle PUBLISH and return a PUBACK.
     * @param ctx {@see ChannelHandlerContext}
     * @param mqttPublishMessage PUBLISH message from broker
     */
    public void processPublish(ChannelHandlerContext ctx, MqttPublishMessage mqttPublishMessage) throws Exception {
        // TODO: Undo the message type modification from String to ByteBuf
        ByteBuf payload = Unpooled.copiedBuffer(mqttPublishMessage.payload());
        messageCallbackHandler.messageArrive(payload);

        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBACK,
                        false,
                        MqttQoS.AT_LEAST_ONCE,
                        false,
                        0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader =
                MqttMessageIdVariableHeader.from(mqttPublishMessage.variableHeader().packetId());
        ctx.channel().writeAndFlush(new MqttPubAckMessage(mqttFixedHeader, mqttMessageIdVariableHeader));
    }

    /**
     * Handle PUBACK.
     * Remove the corresponding packet id in client session's unacked message queue
     * @param ctx {@see ChannelHandlerContext}
     * @param mqttPubAckMessage PUBACK message from broker
     */
    public void processPubAck(ChannelHandlerContext ctx, MqttPubAckMessage mqttPubAckMessage) throws Exception {
        // nop, currently.
    }

    /**
     * Handle SUBACK.
     * @param ctx {@see ChannelHandlerContext}
     * @param mqttSubAckMessage SUBACK message from broker
     */
    public void processSubAck(ChannelHandlerContext ctx, MqttSubAckMessage mqttSubAckMessage) throws Exception {
        // TODO: delete this.
        logger.debug(mqttSubAckMessage.payload().grantedQoSLevels().get(0).toString());
    }

    /**
     * Handle PINGREQ.
     * Respond with a PINGRESP back to broker server.
     * @param ctx {@see ChannelHandlerContext}
     * @param mqttPingReqMessage PINGREQ message from broker
     */
    public void processPingReq(ChannelHandlerContext ctx, MqttPingReqMessage mqttPingReqMessage) throws Exception {

        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0);

        // TODO: mechanism of consumption rate & queue capacity monitoring.
        MqttPingRespVariableHeader mqttPingRespVariableHeader =
                new MqttPingRespVariableHeader(clientSession.isBackPressured(),
                        100, // (int) clientSession.getConsumptionRate(),
                        100, // clientSession.getQueueCapacity(),
                        mqttPingReqMessage.variableHeader().packetId());

        MqttPingRespMessage mqttPingRespMessage =
                new MqttPingRespMessage(mqttFixedHeader, mqttPingRespVariableHeader);

        ctx.channel().writeAndFlush(mqttPingRespMessage);
    }

    public ProtocolProcessor(ClientContext clientContext) {
        this.clientSession = clientContext.getClientSession();
    }
}
