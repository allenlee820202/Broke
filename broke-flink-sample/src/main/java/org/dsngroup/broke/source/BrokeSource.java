/*
 * Copyright (c) 2017 original authors and authors.
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

package org.dsngroup.broke.source;

import org.apache.flink.api.common.functions.StoppableFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.dsngroup.broke.client.BlockClient;
import org.dsngroup.broke.client.handler.callback.IMessageCallbackHandler;
import org.dsngroup.broke.protocol.MqttPublishMessage;
import org.dsngroup.broke.protocol.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class BrokeSource implements SourceFunction<String>, StoppableFunction {

    private static final Logger logger = LoggerFactory.getLogger(BrokeSource.class);

    private String serverAddress;

    private int serverPort;

    private String subscribeTopic;

    private int groupId;

    BlockClient blockClient = null;

    private boolean isRunning;

    class BrokeCallBack implements IMessageCallbackHandler {

        SourceContext<String> ctx;

        @Override
        public void messageArrive(MqttPublishMessage mqttPublishMessage) {
            ctx.collect(mqttPublishMessage.payload().toString(StandardCharsets.UTF_8));
        }

        @Override
        public void connectionLost(Throwable cause) {
            logger.error("Connection lost: " + cause.getMessage());
            System.exit(1);
        }

        BrokeCallBack(SourceContext<String> ctx) {
            this.ctx = ctx;
        }
    }

    @Override
    public void run(final SourceContext<String> ctx) {
        try {
            blockClient = new BlockClient(serverAddress, serverPort);
            blockClient.connect(MqttQoS.AT_LEAST_ONCE, 0);
            blockClient.setMessageCallbackHandler(new BrokeCallBack(ctx));
            blockClient.subscribe(subscribeTopic, MqttQoS.AT_LEAST_ONCE, 0, groupId);

            this.isRunning = true;

            while (this.isRunning) {
                Thread.sleep(3000);
            }

        } catch (Exception e) {
            // TODO e.getMessage();
            logger.error(e.getStackTrace().toString());
        }

    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public void cancel() {
        close();
    }

    public void close() {
        if (blockClient != null) {
            blockClient.disconnect();
        }
        this.isRunning = false;

    }

    public BrokeSource(String serverAddress, int serverPort, String subscribeTopic, int groupId) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.subscribeTopic = subscribeTopic;
        this.groupId = groupId;
    }
}
