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

import lombok.NonNull;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.ResponseDecoderFactory;
import zone.gryphon.screech.exception.DecodeException;
import zone.gryphon.screech.model.ResponseHeaders;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.lang.reflect.Type;

public class JAXB2DecoderFactory implements ResponseDecoderFactory {

    private static SAXParserFactory createDefaultParserFactory() {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

        try {
            // attempt to prevent XXE attacks
            // https://www.owasp.org/index.php/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet#JAXB_Unmarshaller
            saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            saxParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            throw new DecodeException("Failed to configure SAX parser", e);
        }

        return saxParserFactory;
    }

    private final SAXParserFactory saxParserFactory;

    private final JAXBContextFactory jaxbContextFactory;

    public JAXB2DecoderFactory(JAXBContextFactory jaxbContextFactory) {
        this(jaxbContextFactory, createDefaultParserFactory());
    }

    public JAXB2DecoderFactory(@NonNull JAXBContextFactory jaxbContextFactory, @NonNull SAXParserFactory saxParserFactory) {
        this.jaxbContextFactory = jaxbContextFactory;
        this.saxParserFactory = saxParserFactory;
    }

    @Override
    public ResponseDecoder create(ResponseHeaders response, Type type, Callback<Object> callback) {
        return new JAXB2Decoder(jaxbContextFactory, saxParserFactory, type, callback, response);
    }

    @Override
    public String toString() {
        return "JAXB2DecoderFactory{JAXBContextFactory@" + jaxbContextFactory.hashCode() + '}';
    }
}
