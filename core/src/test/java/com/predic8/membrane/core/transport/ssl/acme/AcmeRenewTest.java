package com.predic8.membrane.core.transport.ssl.acme;

import com.google.common.collect.ImmutableList;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.config.security.Certificate;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.Trust;
import com.predic8.membrane.core.config.security.acme.Acme;
import com.predic8.membrane.core.config.security.acme.MemoryStorage;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.AcmeHttpChallengeInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.ssl.AcmeSSLContext;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.Security;
import java.util.Arrays;

import static com.predic8.membrane.core.transport.ssl.acme.Authorization.AUTHORIZATION_STATUS_PENDING;
import static com.predic8.membrane.core.transport.ssl.acme.Authorization.AUTHORIZATION_STATUS_VALID;
import static com.predic8.membrane.core.transport.ssl.acme.Order.ORDER_STATUS_PROCESSING;
import static com.predic8.membrane.core.transport.ssl.acme.Order.ORDER_STATUS_READY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AcmeRenewTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private AcmeServerSimulator sim;
    public String baseUrl = "http://localhost:3050/directory";

    @Before
    public void init() throws IOException {

        sim = new AcmeServerSimulator(3050, 3052, true);
        sim.start();
    }

    @After
    public void done() {
        sim.stop();
    }

    @Test
    public void all() throws Exception {



        Acme acme = new Acme();
        acme.setDirectoryUrl(baseUrl);
        acme.setTermsOfServiceAgreed(true);
        acme.setContacts("mailto:jsmith@example.com");
        acme.setAcmeSynchronizedStorage(new MemoryStorage());

        HttpRouter router = new HttpRouter();
        router.setHotDeploy(false);
        SSLParser sslParser = new SSLParser();
        sslParser.setAcme(acme);
        ServiceProxy sp1 = new ServiceProxy(new ServiceProxyKey(3051), "localhost", 80);
        sp1.setHost("localhost example.com");
        sp1.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.setResponse(Response.ok().status(234, "Successful test.").build());
                return Outcome.RETURN;
            }
        });
        sp1.setSslInboundParser(sslParser);
        ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey(3052), "localhost", 80);
        AcmeHttpChallengeInterceptor acmeHttpChallengeInterceptor = new AcmeHttpChallengeInterceptor();
        acmeHttpChallengeInterceptor.setIgnorePort(true);
        sp2.getInterceptors().add(acmeHttpChallengeInterceptor);
        router.setRules(ImmutableList.of(sp1, sp2));
        router.start();

        AcmeSSLContext acmeSSLContext = (AcmeSSLContext) sp1.getSslInboundContext();

        // ---

        while (!acmeSSLContext.isReady()) {
            Thread.sleep(100);
        }

        HttpClient hc = new HttpClient();
        Exchange e = new Request.Builder().get("https://localhost:3051/").buildExchange();
        SSLParser sslParser1 = new SSLParser();
        Trust trust = new Trust();
        Certificate certificate = new Certificate();
        certificate.setContent(sim.getCA().getCertificate());
        trust.setCertificateList(ImmutableList.of(certificate));
        sslParser1.setTrust(trust);
        e.setProperty(Exchange.SSL_CONTEXT, new StaticSSLContext(sslParser1, router.getResolverMap(), router.getBaseLocation()));
        hc.call(e);

        assertEquals(234, e.getResponse().getStatusCode());


    }

}
