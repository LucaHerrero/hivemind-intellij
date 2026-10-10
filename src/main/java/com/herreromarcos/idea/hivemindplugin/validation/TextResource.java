package com.herreromarcos.idea.hivemindplugin.validation;

import org.apache.hivemind.Resource;
import org.apache.hivemind.util.AbstractResource;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * HiveMind-{@link Resource} für den aktuellen Editor-Inhalt (auch ungespeichert), damit der echte
 * {@code XmlResourceProcessor}/{@code DescriptorParser} von HiveMind ihn lesen kann.
 */
class TextResource extends AbstractResource {
    private final byte[] content;

    TextResource(@NotNull String path, @NotNull String text) {
        super(path);
        this.content = text.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected Resource newResource(String path) {
        return new TextResource(path, "");
    }

    @Override
    public Resource getLocalization(Locale locale) {
        return this;
    }

    @Override
    public URL getResourceURL() {
        try {
            return URL.of(new URI("hivemind-editor", null, "/" + getPath(), null), new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(content);
                        }
                    };
                }
            });
        } catch (final MalformedURLException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return getPath();
    }
}
