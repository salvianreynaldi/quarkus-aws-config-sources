package com.github.salvianreynaldi.quarkus.config.aws.runtime;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.MapBackedConfigValueConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.SsmException;

import javax.inject.Inject;
import java.util.*;

import static io.smallrye.config.Expressions.withoutExpansion;

// Inspired by https://itnext.io/how-to-create-a-configsource-for-quarkus-that-knows-about-existing-properties-1d6e95e7385e
public class SsmConfigSourceFactory implements ConfigSourceFactory {
    @Inject
    SsmClient ssmClient;

    Logger log = Logger.getLogger(getClass().getName());

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        Iterator<String> configKeys = context.iterateNames();

        Map<String, ConfigValue> ssmConfigKeys = new HashMap<>();

        withoutExpansion(() -> { // TODO
            while (configKeys.hasNext()) {
                String key = configKeys.next();
                if (key.startsWith("aws-ssm:")) {
                    ConfigValue value = context.getValue(key);
                    ssmConfigKeys.put(key.substring(8), value);
                }
            }
        });
        return List.of(new SsmConfigSource("ssm-config", ssmConfigKeys));
    }

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(261);
    }

    // https://docs.aws.amazon.com/code-samples/latest/catalog/javav2-ssm-src-main-java-com-example-ssm-GetParameter.java.html
    private List<Parameter> getParametersValue(
            Map<String, ConfigValue> ssmConfigKeys) {
        try {
            GetParametersRequest parameterRequest = GetParametersRequest.builder()
                    .names(new ArrayList<String>(ssmConfigKeys.values()).toArray()) // TODO
                    .withDecryption(true)
                    .build();
            GetParametersResponse parameterResponse = ssmClient.getParameters(parameterRequest);
            return new ArrayList<>(parameterResponse.parameters());

        } catch (SsmException e) {
            System.err.println(e.getMessage()); // TODO
        }
        return null;
    }
}

public static final class SsmConfigSource extends MapBackedConfigValueConfigSource {

    public SsmConfigSource(String name, Map<String, ConfigValue> ssmConfigKeys) {
        super(name, ssmConfigKeys);
        getParametersValue(ssmConfigKeys);
    }
}
