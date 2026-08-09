package com.github.wcqtech.jakit.enumdict.autoconfigure;

import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.convert.EnumDictConverter;
import com.github.wcqtech.jakit.enumdict.i18n.DictValueResolver;
import com.github.wcqtech.jakit.enumdict.i18n.MessageSourceDictValueResolver;
import com.github.wcqtech.jakit.enumdict.scanner.EnumDictScanner;
import com.github.wcqtech.jakit.enumdict.service.DefaultEnumDictService;
import com.github.wcqtech.jakit.enumdict.service.EnumDictService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.MessageSource;
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
    public EnumDictService enumDictService(EnumDictRegistry registry, EnumDictConverter converter,
                                           ObjectProvider<DictValueResolver> valueResolverProvider) {
        return new DefaultEnumDictService(registry, converter, valueResolverProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public EnumDictConverter enumDictConverter(EnumDictRegistry registry, EnumDictProperties properties,
                                               ObjectProvider<DictValueResolver> valueResolverProvider) {
        return new EnumDictConverter(registry, valueResolverProvider.getIfAvailable(),
                properties.getConvert().getMissingPolicy());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MessageSource.class)
    public DictValueResolver dictValueResolver(MessageSource messageSource, EnumDictProperties properties) {
        return new MessageSourceDictValueResolver(messageSource, properties.getI18n().getMissingPolicy());
    }

    @Bean
    public EnumDictRegistrar enumDictRegistrar(EnumDictScanner scanner, EnumDictService service,
                                               EnumDictProperties properties, BeanFactory beanFactory) {
        return new EnumDictRegistrar(scanner, service, properties, beanFactory);
    }
}
