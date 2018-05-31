package com.scienjus.spring.cloud.etcd;

import com.coreos.jetcd.Client;
import com.coreos.jetcd.KV;
import com.coreos.jetcd.data.ByteSequence;
import com.coreos.jetcd.data.KeyValue;
import com.coreos.jetcd.kv.GetResponse;
import com.scienjus.spring.cloud.etcd.properties.EtcdProperties;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class EtcdAutoConfigurationTests {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @Test
    public void testEtcdPropertiesWithNoneEndpoint() {
        this.context.register(EtcdAutoConfiguration.class);
        this.context.refresh();
        EtcdProperties etcdProperties = this.context.getBean(EtcdProperties.class);
        assertEquals(etcdProperties.getEndpoints(), Collections.singletonList("http://127.0.0.1:2379"));
    }

    @Test
    public void testEtcdPropertiesWithSingleEndpoint() {
        String endpoint = "http://10.0.0.1:2379";
        addEnvironment(this.context, "spring.cloud.etcd.endpoints[0]=" + endpoint);
        this.context.register(EtcdAutoConfiguration.class);
        this.context.refresh();
        EtcdProperties etcdProperties = this.context.getBean(EtcdProperties.class);
        assertEquals(etcdProperties.getEndpoints(), Collections.singletonList(endpoint));
    }

    @Test
    public void testEtcdPropertiesWithMultipleEndpoints() {
        List<String> endpoints = Arrays.asList("http://10.0.0.1:2379", "http://10.0.0.2:2379");
        for (int i = 0; i < endpoints.size(); i++) {
            addEnvironment(this.context, String.format("spring.cloud.etcd.endpoints[%d]=%s", i, endpoints.get(i)));
        }
        this.context.register(EtcdAutoConfiguration.class);
        this.context.refresh();
        EtcdProperties etcdProperties = this.context.getBean(EtcdProperties.class);
        assertEquals(etcdProperties.getEndpoints(), endpoints);
    }

    @Test
    public void testAfterConfigurationLoadedEtcdClientIsAvailable() throws ExecutionException, InterruptedException {
        ByteSequence key = ByteSequence.fromString("test_key");
        ByteSequence val = ByteSequence.fromString("test_val");

        // start an etcd container
        try (EtcdContainer container = new EtcdContainer()) {
            container.start();
            addEnvironment(this.context, "spring.cloud.etcd.endpoints[0]=" + container.clientEndpoint());
            this.context.register(EtcdAutoConfiguration.class);
            this.context.refresh();
            Client etcdClient = this.context.getBean(Client.class);
            KV kvClient = etcdClient.getKVClient();
            kvClient.put(key, val).get();

            GetResponse getResponse = kvClient.get(key).get();
            assertEquals(getResponse.getCount(), 1);

            KeyValue kv = getResponse.getKvs().get(0);
            assertEquals(key, kv.getKey());
            assertEquals(val, kv.getValue());
        }
    }

    @After
    public void closeContext() {
        this.context.close();
    }
}