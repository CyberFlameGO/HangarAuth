package io.papermc.hangarauth;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;


@Configuration
public class JdbiBeanFactoryPostProcessor implements BeanFactoryPostProcessor, ResourceLoaderAware, EnvironmentAware, BeanClassLoaderAware, BeanFactoryAware {

    private BeanFactory beanFactory;
    private ResourceLoader resourceLoader;
    private Environment environment;
    private ClassLoader classLoader;

    @Override
    public void setBeanClassLoader(@NotNull ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setBeanFactory(@NotNull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    private void registerJdbiDaoBeanFactory(BeanDefinitionRegistry registry, BeanDefinition bd) {
        GenericBeanDefinition beanDefinition = (GenericBeanDefinition) bd;
        Class<?> jdbiDaoClass;
        try {
            jdbiDaoClass = beanDefinition.resolveBeanClass(classLoader);
        } catch (ClassNotFoundException e) {
            throw new FatalBeanException(beanDefinition.getBeanClassName() + " not found on classpath", e);
        }
        beanDefinition.setBeanClass(JdbiDAOBeanFactory.class);
        // Add dependency to your `Jdbi` bean by name
        beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(new RuntimeBeanReference("jdbi"));
        beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(Objects.requireNonNull(jdbiDaoClass));

        registry.registerBeanDefinition(jdbiDaoClass.getSimpleName(), beanDefinition);
    }

    @Override
    public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
        // not needed
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        scanner.setEnvironment(this.environment);
        scanner.setResourceLoader(this.resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));

        final List<String> basePackages = AutoConfigurationPackages.get(beanFactory);
        basePackages.stream()
            .map(scanner::findCandidateComponents)
            .flatMap(Collection::stream)
            .forEach(bd -> registerJdbiDaoBeanFactory((DefaultListableBeanFactory) configurableListableBeanFactory, bd));
    }

    @Override
    public void setEnvironment(@NotNull Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(@NotNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
