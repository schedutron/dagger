package io.odpf.dagger.core.config.system;

import io.odpf.dagger.common.configuration.UserConfiguration;
import io.odpf.dagger.core.config.EnvironmentConfigurationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class EnvironmentConfigurationProviderTest {

    @Test
    public void shouldProvideSystemConfiguration() {
        HashMap<String, String> environmentParameters = new HashMap<String, String>() {{
            put("key", "value");
            put("key2", "value2");
        }};

        UserConfiguration userConf = new EnvironmentConfigurationProvider(environmentParameters).getUserConf();
        assertEquals("value", userConf.getParam().get("key", ""));
        assertEquals("value2", userConf.getParam().get("key2", ""));
    }
}