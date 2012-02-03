package com.moviejukebox.allocine;

import com.moviejukebox.allocine.jaxb.ObjectFactory;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamException;

public final class XMLAllocineAPIHelper {

    // Suppresses default constructor, ensuring non-instantiability.
    private XMLAllocineAPIHelper() {
    }

    private static final JAXBContext JAXB_CONTEXT = initContext();

    private static JAXBContext initContext() {
        try {
            return JAXBContext.newInstance("com.moviejukebox.allocine.jaxb");
        } catch (JAXBException error) {
            throw new Error("XMLAllocineAPIHelper: Got error during initialization", error);
        }
    }

    protected static Unmarshaller createAllocineUnmarshaller() throws JAXBException {
        Unmarshaller unmarshaller = JAXB_CONTEXT.createUnmarshaller();
        // Use our own ObjectFactory so we can add behaviors in our classes
        unmarshaller.setProperty("com.sun.xml.bind.ObjectFactory", new ObjectFactory());
        return unmarshaller;
    }

    protected static MovieInfos validMovieElement(Object rootElement) {
        if (rootElement instanceof MovieInfos) {
            return (MovieInfos) rootElement;
        }
        // Error
        return null;
    }

    public static MovieInfos getMovieInfos(String allocineId) throws IOException, JAXBException, XMLStreamException {
        URL url = new URL("http://api.allocine.fr/xml/movie?partner=3&profile=large&code=" + allocineId);
        Unmarshaller unmarshaller = createAllocineUnmarshaller();
        return validMovieElement(unmarshaller.unmarshal(url));
    }

    public static MovieInfos getMovieInfos(File file) throws IOException, JAXBException {
        Unmarshaller unmarshaller = createAllocineUnmarshaller();
        return validMovieElement(unmarshaller.unmarshal(file));
    }
}
