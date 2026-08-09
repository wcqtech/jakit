package com.github.wcqtech.jakit.enumdict.autoconfigure;

import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.convert.EnumDictConverter;
import com.github.wcqtech.jakit.enumdict.scanner.EnumDictScanner;
import com.github.wcqtech.jakit.enumdict.service.DefaultEnumDictService;
import com.github.wcqtech.jakit.enumdict.service.EnumDictService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * Auto-configures enum dictionary scanning, registration and query facade.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "jakit.enum-dict", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EnumDictProperties.class)
public class EnumDictAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EnumDictRegistry enumDictRegistry() {
        return new EnumDictRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public EnumDictScanner enumDictScanner(EnumDictRegistry registry, ResourceLoader resourceLoader) {
        return new EnumDictScanner(registry, resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    public EnumDictService enumDictService(EnumDictRegistry registry, EnumDictConverter converter) {
        return new DefaultEnumDictService(registry, converter);
    }

    @Bean
    @ConditionalOnMissingBean
    public EnumDictConverter enumDictConverter(EnumDictRegistry registry, EnumDictProperties properties) {
        return new EnumDictConverter(registry, properties.getConvert().getMissingPolicy());
    }

    @Bean
    public EnumDictRegistrar enumDictRegistrar(EnumDictScanner scanner, EnumDictService service,
                                               EnumDictProperties properties, BeanFactory beanFactory) {
        return new EnumDictRegistrar(scanner, service, properties, beanFactory);
    }
}
