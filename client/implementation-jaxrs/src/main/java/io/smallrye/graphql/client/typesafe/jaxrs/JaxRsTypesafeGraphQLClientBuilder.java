package io.smallrye.graphql.client.typesafe.jaxrs;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.eclipse.microprofile.config.ConfigProvider;

import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import io.smallrye.graphql.client.typesafe.api.TypesafeGraphQLClientBuilder;
import io.smallrye.graphql.client.typesafe.impl.reflection.MethodInvocation;

public class JaxRsTypesafeGraphQLClientBuilder implements TypesafeGraphQLClientBuilder {
    private String configKey = null;
    private Client client;
    private URI endpoint;

    @Override
    public TypesafeGraphQLClientBuilder configKey(String configKey) {
        this.configKey = configKey;
        return this;
    }

    public TypesafeGraphQLClientBuilder client(Client client) {
        this.client = client;
        return this;
    }

    private Client client() {
        if (client == null)
            client = ClientBuilder.newClient();
        return client;
    }

    @Override
    public TypesafeGraphQLClientBuilder endpoint(URI endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public TypesafeGraphQLClientBuilder register(Class<?> componentClass) {
        client().register(componentClass);
        return this;
    }

    public TypesafeGraphQLClientBuilder register(Object component) {
        client().register(component);
        return this;
    }

    @Override
    public <T> T build(Class<T> apiClass) {
        readConfig(apiClass.getAnnotation(GraphQLClientApi.class));

        WebTarget webTarget = client().target(resolveEndpoint(apiClass));
        JaxRsTypesafeGraphQLClientProxy graphQlClient = new JaxRsTypesafeGraphQLClientProxy(webTarget);
        return apiClass.cast(Proxy.newProxyInstance(getClassLoader(apiClass), new Class<?>[] { apiClass },
                (proxy, method, args) -> invoke(apiClass, graphQlClient, method, args)));
    }

    private Object invoke(Class<?> apiClass, JaxRsTypesafeGraphQLClientProxy graphQlClient, java.lang.reflect.Method method,
            Object... args) {
        MethodInvocation methodInvocation = MethodInvocation.of(method, args);
        if (methodInvocation.isDeclaredInCloseable()) {
            client().close();
            return null; // void
        }
        return graphQlClient.invoke(apiClass, methodInvocation);
    }

    private ClassLoader getClassLoader(Class<?> apiClass) {
        if (System.getSecurityManager() == null)
            return apiClass.getClassLoader();
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) apiClass::getClassLoader);
    }

    private void readConfig(GraphQLClientApi annotation) {
        if (annotation == null)
            return;
        if (this.endpoint == null && !annotation.endpoint().isEmpty())
            this.endpoint = URI.create(annotation.endpoint());
        if (this.configKey == null && !annotation.configKey().isEmpty())
            this.configKey = annotation.configKey();
    }

    private URI resolveEndpoint(Class<?> apiClass) {
        if (endpoint != null)
            return endpoint;
        return ConfigProvider.getConfig().getValue(configKey(apiClass) + "/mp-graphql/url", URI.class);
    }

    private String configKey(Class<?> apiClass) {
        return (configKey == null) ? apiClass.getName() : configKey;
    }
}
