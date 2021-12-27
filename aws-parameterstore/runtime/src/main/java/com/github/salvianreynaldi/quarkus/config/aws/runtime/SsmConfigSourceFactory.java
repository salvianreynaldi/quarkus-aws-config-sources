package com.github.salvianreynaldi.quarkus.config.aws.runtime;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.common.MapBackedConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersResponse;

import javax.inject.Inject;
import java.util.*;

// Inspired by https://itnext.io/how-to-create-a-configsource-for-quarkus-that-knows-about-existing-properties-1d6e95e7385e
public class SsmConfigSourceFactory implements ConfigSourceFactory {
    private static final Logger logger = Logger.getLogger(SsmConfigSourceFactory.class);
    @Inject
    SsmClient ssmClient;

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        Iterator<String> configKeys = context.iterateNames();
        List<String> ssmParameterKeys = new ArrayList<>();
        while (configKeys.hasNext()) {
            String key = configKeys.next();
            ConfigValue value = context.getValue(key);
            // collect all ssm-parameterstore keys
            if (value.getValue().startsWith("aws-parameterstore:")) {
                ssmParameterKeys.add(value.getValue().substring(19));
                logger.info("Will retrieve '" + value.getValue().substring(19) + "' from AWS Parameter Store as the value for " + key);
            }
        }
        return List.of(new SsmConfigSource("aws-parameterstore-config", getSsmParameterMap(ssmParameterKeys), 400)); // TODO
    }

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(261);
    }

    // https://docs.aws.amazon.com/code-samples/latest/catalog/javav2-ssm-src-main-java-com-example-ssm-GetParameter.java.html
    private Map<String, String> getSsmParameterMap(List<String> ssmKeys) {
        GetParametersRequest parameterRequest = GetParametersRequest.builder().names(ssmKeys).withDecryption(true).build();
        GetParametersResponse parameterResponse = ssmClient.getParameters(parameterRequest);
        Map<String, String> result = new HashMap<>();
        parameterResponse.parameters().forEach(parameter -> result.put(parameter.name(), parameter.value()));
        return result;
    }

    private static final class SsmConfigSource extends MapBackedConfigSource {

        public SsmConfigSource(String name, Map<String, String> configMap, int defaultOrdinal) {
            super(name, configMap, defaultOrdinal);
        }
    }
}

