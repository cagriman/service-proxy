/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.templating;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.util.*;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.file.StandardCopyOption.*;
import static javax.xml.xpath.XPathConstants.*;
import static org.junit.jupiter.api.Assertions.*;

public class TemplateInterceptorTest {

    private final ObjectMapper om = new ObjectMapper();

    TemplateInterceptor ti;
    Exchange exc = new Exchange(null);
    Request req;
    static Path copiedXml;
    static Path copiedJson;
    static Router router;
    static ResolverMap map;
    static final String separator = FileSystems.getDefault().getSeparator();

    @Test
    void accessJson() throws Exception {
        Exchange exchange = new Request.Builder().contentType(APPLICATION_JSON).body("""
                { "city": "Da Nang" }
                """).buildExchange();

        invokeInterceptor(exchange, """
                City: <%= json.city %>
                """, TEXT_PLAIN);

        assertEquals("City: Da Nang", exchange.getRequest().getBodyAsStringDecoded().trim());
    }

    @SuppressWarnings("unchecked")
    @Test
    void createson() throws Exception {
        Exchange exchange = new Request.Builder().contentType(APPLICATION_JSON).buildExchange();

        invokeInterceptor(exchange, """
                {"foo":7,"bar":"baz"}
                """, APPLICATION_JSON);

        assertEquals(APPLICATION_JSON, exchange.getRequest().getHeader().getContentType());

        Map<String,Object> m = om.readValue(exchange.getRequest().getBodyAsStringDecoded(),Map.class);
        assertEquals(7,m.get("foo"));
        assertEquals("baz",m.get("bar"));
    }

    @Test
    void accessBindings() throws Exception {
        Exchange exchange = new Request.Builder().url(new URIFactory(),"/foo?a=1&b=2").contentType(TEXT_PLAIN).body("vlinder").buildExchange();
        exchange.setProperty("baz",7);

        invokeInterceptor(exchange, """
<% for(h in header.allHeaderFields) { %>
   <%= h.headerName %> : <%= h.value %>
<% } %>
Exchange: <%= exc %>
Flow: <%= flow %>
Message.version: <%= message.version %>
Body: <%= body %>
Properties: <%= properties.baz %>
<% for(p in props) { %>
   Key: <%= p.key %> : <%= p.value %>
<% } %>
Old: <%= baz %> Exchange property as variable (Deprecated!)
New: <%= props.baz %> Exchange property new style
Query Params:
A: <%= params.a %>
B: <%= params.b %>

<% for(p in params) { %>
    <%= p.key %> : <%= p.value %>
<% } %>
                """, APPLICATION_JSON);
        
        String body = exchange.getRequest().getBodyAsStringDecoded();
        System.out.println("body = " + body);
        assertTrue(body.contains("/foo"));
        assertTrue(body.contains("Flow: REQUEST"));
        assertTrue(body.contains("Body: vlinder"));
        assertTrue(body.contains("Old: 7"));
        assertTrue(body.contains("New: 7"));
        assertTrue(body.contains("A: 1"));
        assertTrue(body.contains("B: 2"));
    }


    @BeforeAll
    public static void setupFiles() throws IOException {
        //user.dir returns current working directory
        copyFiles(Paths.get("src/test/resources/xml/project_template.xml"),Paths.get(System.getProperty("user.dir") +
                separator + "project_template.xml") );
        copyFiles(Paths.get("src/test/resources/json/template_test.json"), Paths.get(System.getProperty("user.dir") +
                separator + "template_test.json"));
        
        copiedXml = Paths.get(System.getProperty("user.dir") +
                separator + "project_template.xml");
        copiedJson = Paths.get(System.getProperty("user.dir") +
                separator + "template_test.json");
        router = Mockito.mock(Router.class);
        map = new ResolverMap();
        Mockito.when(router.getResolverMap()).thenReturn(map);
    }

    @BeforeEach
    public void setUp(){
        ti = new TemplateInterceptor();
        exc = new Exchange(null);
        req = new Request.Builder().build();
        exc.setRequest(req);

        exc.setProperty("title", "minister");
        List<String> lst = new ArrayList<>();
        lst.add("food1");
        lst.add("food2");
        exc.setProperty("items", lst);
        exc.setProperty("title", "minister");

    }

    @Test
    public void xmlFromFileTest() throws Exception {
        setAndHandleRequest("./project_template.xml");

        XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("/project/part[2]/title");
        String filled = ((NodeList) xpath.evaluate(DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(exc.getRequest().getBodyAsStream()), NODESET)).item(0).getFirstChild().getNodeValue();

        assertEquals("minister", filled.trim());
    }


    @Test
    public void nonXmlTemplateListTest() throws Exception {
        setAndHandleRequest("./template_test.json");

        assertEquals("food1",
                new JSONObject(exc.getRequest().getBodyAsStringDecoded()).getJSONArray("orders")
                        .getJSONObject(0).getJSONArray("items").getString(0));

        assertEquals("minister",
                new JSONObject(exc.getRequest().getBodyAsStringDecoded()).getJSONObject("meta").getString("title"));

    }

    @Test
    public void initTest() {
        assertThrows(IllegalStateException.class, () -> {
            ti.setLocation("./template_test.json");
            ti.setTextTemplate("${minister}");
            ti.init(router);
        });
    }

    @Test
    public void notFoundTemplateException() {
        assertThrows(ResourceRetrievalException.class, () -> {
            ti.setLocation("./not_existent_file");
            ti.init(router);
        });
    }

    @Test
    public void innerTagTest() throws Exception {
        ti.setTextTemplate("${title}");
        ti.init(router);
        ti.handleRequest(exc);

        assertEquals("minister", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    public void contentTypeTestXml() throws Exception {
        setAndHandleRequest("./project_template.xml");
        assertTrue(exc.getRequest().isXML());
    }

    @Test
    public void contentTypeTestOther() throws Exception {
        ti.setContentType("application/json");
        setAndHandleRequest("./template_test.json");
        assertTrue(exc.getRequest().isJSON());
    }

    @Test
    public void contentTypeTestNoXml() throws Exception {
        setAndHandleRequest("./template_test.json");
        assertEquals("text/plain",exc.getRequest().getHeader().getContentType());
    }

    private void setAndHandleRequest(String location) throws Exception {
        ti.setLocation(location);
        ti.init(router);
        ti.handleRequest(exc);
    }

    @AfterAll
    public static  void deleteFile() throws IOException {
        Files.delete(copiedXml);
        Files.delete(copiedJson);
    }



    private static void invokeInterceptor(Exchange exchange, String template, String mimeType) throws Exception {
        TemplateInterceptor interceptor = new TemplateInterceptor();
        interceptor.setTextTemplate(template);
        interceptor.setContentType(mimeType);
        interceptor.init();
        interceptor.handleRequest(exchange);
    }

    public static void copyFiles(Path orig, Path copy) throws IOException {
        Files.copy(orig, copy, REPLACE_EXISTING);
    }
}