/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange.snapshots;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class MessageSnapshot {

    Map<String,String> header;
    byte[] body = null;

    /**
     * @param msg the message to snapshot
     * @param bodyCopiedCallback will be called once the body has been filled. if null, the body stream will be read
     *                 into memory immediately.
     * @param aes parameter for the callback
     * @param strategy how to handle body lengths exceeding the {@code limit}.
     * @param limit maximum length of the body.
     */
    public MessageSnapshot(Message msg, Consumer<AbstractExchangeSnapshot> bodyCopiedCallback, AbstractExchangeSnapshot aes, BodyCollectingMessageObserver.Strategy strategy, long limit) throws IOException {
        header = new HashMap<>();
        Stream.of(msg.getHeader().getAllHeaderFields()).forEach(headerField -> {
            String key = headerField.getHeaderName().toString();
            String value = header.get(key);
            if (value != null)
                header.put(key, value + ", " + headerField.getValue());
            else
                header.put(key, headerField.getValue());
        });
        if (bodyCopiedCallback == null) {
            body = IOUtils.toByteArray(new CountingInputStream(msg.getBodyAsStreamDecoded()) {
                @Override
                public int read(byte[] b) throws IOException {
                    if (limit != -1 && getCount() > limit)
                        switch (strategy) {
                            case TRUNCATE:
                                return -1;
                            case ERROR:
                                throw new IOException("Body too large. (limit = " + limit + ")");
                        }
                    return super.read(b);
                }
            });
        } else {
            msg.addObserver(new BodyCollectingMessageObserver(strategy, limit) {
                @Override
                public void bodyRequested(AbstractBody body) {

                }

                @Override
                public void bodyComplete(AbstractBody body2) {
                    InputStream body1 = getBody(body2);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try {
                        IOUtils.copy(body1, baos);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    body = baos.toByteArray();
                    try {
                        bodyCopiedCallback.call(aes);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    public MessageSnapshot() {
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public void setHeader(Map<String, String> header) {
        this.header = header;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public Header convertHeader(){
        Header result = new Header();
        header.keySet().stream().forEach(key -> result.add(key,header.get(key)));
        return result;
    }

    public AbstractBody convertBody(){
        if(body == null)
            return new EmptyBody();
        return new Body(body);
    }
}
