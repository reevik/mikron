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

import java.lang.reflect.Field;
import java.util.function.Predicate;
import net.reevik.mikron.annotation.Wire;
import net.reevik.mikron.configuration.PropertiesRepository;
import net.reevik.mikron.string.Str;

public class ComponentNameResolver {

    private final Field field;
    private final MikronContext context;

    public ComponentNameResolver(Field field, MikronContext context) {
        this.field = field;
        this.context = context;
    }

    public String getComponentName() {
        var classKey = getDependencyName();
        var propertyFilter = field.getAnnotation(Wire.class).filter();
        if (Str.isEmpty(propertyFilter)) {
            return classKey;
        }
        var filterArr = propertyFilter.split("=");
        var filterName = filterArr[0];
        var filterValue = filterArr[1];
        var propRepo = context.getPropertiesRepository();
        return propRepo.getPropertyClassNames().stream()
            .filter(className -> className.startsWith(classKey))
            .filter(hasFilteredProperty(filterName, filterValue, propRepo))
            .findFirst()
            .orElse(classKey);
    }

    private Predicate<String> hasFilteredProperty(String filterName, String filterValue,
        PropertiesRepository propRepo) {
        return className -> filterValue.equals(propRepo.getConfiguration(className)
            .map(b -> b.get(filterName))
            .orElse(null));
    }

    private String getDependencyName() {
        var name = field.getAnnotation(Wire.class).name();
        return Str.isEmpty(name) ? field.getType().getName() : name;
    }
}
