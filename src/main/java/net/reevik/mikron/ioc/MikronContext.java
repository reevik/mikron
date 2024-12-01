/*
 * Copyright (c) 2024 Erhan Bagdemir. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.reevik.mikron.ioc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import net.reevik.mikron.annotation.CleanUp;
import net.reevik.mikron.annotation.Configurable;
import net.reevik.mikron.annotation.Initialize;
import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.annotation.ManagedApplication;
import net.reevik.mikron.annotation.ManagedDefinition;
import net.reevik.mikron.annotation.Prefer;
import net.reevik.mikron.annotation.Wire;
import net.reevik.mikron.configuration.PropertiesRepository;
import net.reevik.mikron.configuration.TypeConverter;
import net.reevik.mikron.reflection.ClasspathResourceRepository;
import net.reevik.mikron.string.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Mikron context is the inversion-of-control container, which control the life-cycle of the mikron
 * managed instances. {@link MikronContext} supplier is managed itself, even though it's not
 * annotated with {@link Managed} so it can be injected in your application code.
 *
 * @author Erhan Bagdemir
 */
public class MikronContext implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MikronContext.class);

    /**
     * The cache for the managed instances.
     */
    private final Map<String, ManagedInstance> managedInstances = new HashMap<>();
    private final PropertiesRepository propertiesRepository;
    private final ClasspathResourceRepository classpathResourceRepository;

    @Configurable(name = "key")
    private int key;

    private MikronContext(Class<?> applicationClass) {
        this.propertiesRepository = new PropertiesRepository();
        this.propertiesRepository.loadAllProperties();
        this.classpathResourceRepository = initializeClasspath(applicationClass);
    }

    public static MikronContext init(Class<?> clazz) {
        var managedContext = new MikronContext(clazz);
        managedContext.managedInstances.clear();
        managedContext.initializeContext();
        return managedContext;
    }

    /**
     * Registers a new managed object explicitly, which makes the context rescan the packages so
     * that the new managed instance can be wired. This type of instance registration is used by
     * jUnit extension for injecting managed instances into test classes.
     * <p>
     *
     * @param instance Which is registered manually in the current context.
     * @param name     The managedInstanceName of the managed supplier.
     */
    public void register(Object instance, String name) {
        managedInstances.clear();
        managedInstances.put(name, new ManagedInstance(instance, name, this));
        initializeContext();
        initializeWiring();
        initializeConfigurations();
    }

    private void initializeConfigurations() {
        managedInstances.values().forEach(ManagedInstance::configSetup);
    }

    private void initializeContext() {
        registerContext();
        LinkedHashMap<String, ManagedFactory> factories = new LinkedHashMap<>();
        var managedTypes = classpathResourceRepository.findClassesBy(Managed.class);
        for (var annotationResource : managedTypes) {
            var componentName = getName(annotationResource);
            var propBasedInstanceCreation = createInstanceByPropertyFile(factories, annotationResource, componentName);
            if (!propBasedInstanceCreation) {
                var supplier = getManagedInstanceSupplier(annotationResource, componentName);
                factories.put(componentName, supplier);
            }
        }

        for (Entry<String, ManagedFactory> entry : factories.entrySet()) {
            instantiateManagedInstance(factories, entry.getValue());
        }
    }

    private void instantiateManagedInstance(LinkedHashMap<String, ManagedFactory> factories, ManagedFactory managedFactory) {
        var managedManagedDefinition = managedFactory.annotationResource();
        var declaredFields = managedManagedDefinition.clazz().getDeclaredFields();
        for (var field : declaredFields) {
            if (field.isAnnotationPresent(Wire.class)) {
                var componentName = new ComponentNameResolver(field, this).getComponentName();
                if (!managedInstances.containsKey(componentName) && factories.containsKey(componentName)) {
                    instantiateManagedInstance(factories, factories.get(componentName));
                }
            }
        }
        var object = managedFactory.supplier().get();
        var managedInstance = new ManagedInstance(object, managedFactory.name(), this);
        managedInstance.configSetup();
        managedInstance.wire();
        managedInstance.postConstruct();
        managedInstances.put(managedFactory.name(), managedInstance);
    }

    private void initializeWiring() {
        managedInstances.values().forEach(ManagedInstance::wire);
    }

    private boolean createInstanceByPropertyFile(Map<String, ManagedFactory> factories,
        ManagedDefinition<Managed> annotationResource,
        String componentName) {
        var propBasedInstanceCreation = false;
        for (var propFile : propertiesRepository.getPropertyClassNames()) {
            if (propFile.startsWith(componentName) && !managedInstances.containsKey(propFile)) {
                factories.put(propFile, getManagedInstanceSupplier(annotationResource, propFile));
                propBasedInstanceCreation = true;
            }
        }
        return propBasedInstanceCreation;
    }

    private void registerContext() {
        managedInstances.put(MikronContext.class.getSimpleName(),
            new ManagedInstance(this, MikronContext.class.getSimpleName(), this));
    }

    private String getName(ManagedDefinition<Managed> annotationResource) {
        var name = annotationResource.annotation().name();
        if (Str.isEmpty(name)) {
            return annotationResource.clazz().getName();
        }
        return name;
    }

    private ClasspathResourceRepository initializeClasspath(Class<?> clazz) {
        final var declaredAnnotation = clazz.getAnnotation(ManagedApplication.class);
        if (declaredAnnotation == null) {
            throw new ApplicationInitializationException(
                "No managed application found with @ManagedApplication annotation.");
        }
        return ClasspathResourceRepository.of(declaredAnnotation.packages());
    }

    private ManagedFactory getManagedInstanceSupplier(ManagedDefinition<Managed> annotationResource,
        String name) {
        Class<?> managedDefiningClass = annotationResource.clazz();
        Constructor<?>[] constructors = managedDefiningClass.getConstructors();
        return Arrays.stream(constructors)
            .filter(constructor -> constructor.isAnnotationPresent(Prefer.class))
            .findFirst()
            .map(
                constructor -> (Supplier<Object>) () -> new ConstructorInjectionPoint<>(constructor,
                    this).inject())
            .map(supplier -> new ManagedFactory(supplier, annotationResource, name))
            .orElseGet(() -> new ManagedFactory(() -> instantiateByDefault(annotationResource),
                annotationResource, name));
    }

    private Object instantiateByDefault(ManagedDefinition<Managed> annotationResource) {
        try {
            var clazz = annotationResource.clazz();
            var constructor = clazz.getConstructor();
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    ManagedInstance initObjectByAccess(Class<?> targetType, String targetName) {
        try {
            if (!targetType.isInterface() && !targetType.isAnnotationPresent(Managed.class)) {
                throw new IllegalWiringException("You can only wire managed objects with @Managed "
                    + "annotation on them.");
            }

            Class<?> managedType = null;
            if (targetType.isInterface()) {

                Set<Class<?>> implementingManagedInstances = findImplementingManagedInstances(
                    targetType);
                if (implementingManagedInstances.size() == 1 && Str.isEmpty(targetName)) {
                    managedType = implementingManagedInstances.iterator().next();
                }

                if (!implementingManagedInstances.isEmpty() && Str.isNotEmpty(targetName)) {
                    Optional<Class<?>> matchingNamedManagedInstance =
                        implementingManagedInstances.stream().filter(managedClass ->
                                targetName.equals(managedClass.getAnnotation(Managed.class).name()))
                            .findFirst();
                    managedType =
                        matchingNamedManagedInstance.orElseThrow(
                            MatchingDependencyNotFoundException::new);
                }

                if (managedType == null) {
                    throw new MatchingDependencyNotFoundException();
                }

            } else {
                managedType = targetType;
            }
            var constructor = managedType.getConstructor();
            return new ManagedInstance(constructor.newInstance(), targetName, this);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, ManagedInstance> getManagedInstances() {
        return managedInstances;
    }

    public <T> Optional<T> getInstance(String name) {
        if (managedInstances.containsKey(name)) {
            ManagedInstance managedInstance = managedInstances.get(name);
            return Optional.of((T) managedInstance.getInstance());
        }
        return Optional.empty();
    }

    public PropertiesRepository getPropertiesRepository() {
        return propertiesRepository;
    }

    public Optional<Properties> getConfiguration(String configurationSourceKey) {
        return propertiesRepository.getConfiguration(configurationSourceKey);
    }

    private void postConstructAll() {
        executeIfAnnotated(Initialize.class);
    }

    @Override
    public void close() {
        executeIfAnnotated(CleanUp.class);
    }

    private void executeIfAnnotated(Class<? extends Annotation> annotation) {
        managedInstances.values()
            .forEach(managedInstance -> executeIfAnnotated(annotation, managedInstance));
    }

    private void executeIfAnnotated(Class<? extends Annotation> annotation,
        ManagedInstance managedInstance) {
        Class<?> aClass = managedInstance.getInstance().getClass();
        for (Method declaredMethod : aClass.getDeclaredMethods()) {
            if (declaredMethod.isAnnotationPresent(annotation)) {
                try {
                    if (declaredMethod.getParameterCount() > 0) {
                        throw new IllegalArgumentException(
                            "@Initialize/@CleanUp methods shouldn't take " + "parameters.");
                    }
                    declaredMethod.invoke(managedInstance.getInstance());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Find and return the implementing managed instances.
     *
     * @param parentType Parent type, e.g., an interface declaring a concrete managed supplier.
     * @return All implementing concrete managed instances.
     */
    public Set<Class<?>> findImplementingManagedInstances(Class<?> parentType) {
        return classpathResourceRepository.findImplementingClasses(parentType, Managed.class);
    }

    TypeConverter getConverter(Class<?> clazz, Class<? extends TypeConverter> converter)
        throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        try {
            return converter.getConstructor(Class.class).newInstance(clazz);
        } catch (NoSuchMethodException nsm) {
            return converter.getConstructor().newInstance();
        }
    }

    record ManagedFactory(Supplier<Object> supplier,
                          ManagedDefinition<Managed> annotationResource,
                          String name) {

    }
}
