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

import org.xml.sax.InputSource;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.exception.DecodeException;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.util.BufferingResponseDecoder;
import zone.gryphon.screech.util.ExpandableByteBuffer;

import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.io.InputStream;
import java.lang.reflect.Type;

public class JAXB2Decoder extends BufferingResponseDecoder {

    private final Callback<Object> callback;

    private final Unmarshaller unmarshaller;

    private final Class<?> clazz;

    private final SAXParserFactory saxParserFactory;

    public JAXB2Decoder(JAXBContextFactory jaxbContextFactory, SAXParserFactory saxParserFactory, ResponseHeaders responseHeaders, Type type, Callback<Object> callback) {
        super(responseHeaders);
        this.saxParserFactory = saxParserFactory;

        if (!(type instanceof Class)) {
            throw new DecodeException(String.format("Unable to build JAXB context for %s, only raw Class objects are supported", type));
        }

        this.clazz = (Class) type;
        this.unmarshaller = jaxbContextFactory.unmarshallerFor(this.clazz);
        this.callback = callback;
    }

    @Override
    protected void complete(ExpandableByteBuffer byteBuffer) {

        try (InputStream stream = byteBuffer.createInputStream()) {
            callback.onSuccess(unmarshaller.unmarshal(new SAXSource(saxParserFactory.newSAXParser().getXMLReader(), new InputSource(stream))));
        } catch (Throwable e) {
            callback.onFailure(new DecodeException(String.format("Failed to decode object of type \"%s\"", clazz.getName()), e));
        }
    }

    @Override
    public String toString() {
        return "JAXB2Decoder{Unmarshaller@" + unmarshaller.hashCode() + '}';
    }
}
