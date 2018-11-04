/*
 * Copyright 2018-2018 Gryphon Zone
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package zone.gryphon.screech.jaxb2;

import zone.gryphon.screech.exception.EncodeException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SimpleJAXBContextFactory implements JAXBContextFactory {

    private final int CACHE_DEFAULT_SIZE = 64;

    private final Map<Class<?>, JAXBContext> cache = new HashMap<>(CACHE_DEFAULT_SIZE);

    private final Map<String, ?> jaxbContextProperties;

    private final Map<String, ?> marshallerProperties;

    private final Map<String, ?> unmarshallerProperties;

    public SimpleJAXBContextFactory() {
        this(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    public SimpleJAXBContextFactory(
            Map<String, ?> jaxbContextProperties,
            Map<String, ?> marshallerProperties,
            Map<String, ?> unmarshallerProperties) {
        this.jaxbContextProperties = nullToEmptyMap(jaxbContextProperties);
        this.marshallerProperties = nullToEmptyMap(marshallerProperties);
        this.unmarshallerProperties = nullToEmptyMap(unmarshallerProperties);
    }

    private Map<String, ?> nullToEmptyMap(Map<String, ?> input) {
        return input == null ? Collections.emptyMap() : input;
    }

    private JAXBContext contextFor(Class<?> clazz) {
        try {
            return JAXBContext.newInstance(new Class[]{clazz}, jaxbContextProperties);
        } catch (JAXBException e) {
            throw new EncodeException(String.format("Failed to create JAXBContext for %s", clazz.getName()), e);
        }
    }

    private Marshaller setProperties(Marshaller input) {
        marshallerProperties.forEach((name, value) -> {
            try {
                input.setProperty(name, value);
            } catch (PropertyException e) {
                throw new EncodeException(String.format("Failed to set property \"%s\" with value \"%s\" on JAXB Marshaller", name, value), e);
            }
        });

        return input;
    }

    private Unmarshaller setProperties(Unmarshaller input) {
        unmarshallerProperties.forEach((name, value) -> {
            try {
                input.setProperty(name, value);
            } catch (PropertyException e) {
                throw new EncodeException(String.format("Failed to set property \"%s\" with value \"%s\" on JAXB Unmarshaller", name, value), e);
            }
        });

        return input;
    }

    @Override
    public Marshaller marshallerFor(Class<?> clazz) {
        try {
            return setProperties(cache.computeIfAbsent(clazz, this::contextFor).createMarshaller());
        } catch (JAXBException e) {
            throw new EncodeException(String.format("Failed to create JAXB Marshaller for %s", clazz.getName()), e);
        }
    }

    @Override
    public Unmarshaller unmarshallerFor(Class<?> clazz) {
        try {
            return setProperties(cache.computeIfAbsent(clazz, this::contextFor).createUnmarshaller());
        } catch (JAXBException e) {
            throw new EncodeException(String.format("Failed to create JAXB Unmarshaller for %s", clazz.getName()), e);
        }
    }
}
