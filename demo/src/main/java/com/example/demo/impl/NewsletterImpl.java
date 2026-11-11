package com.example.demo.impl;

import com.example.demo.Archive;
import com.example.demo.Clock;
import com.example.demo.Newsletter;

/**
 * Ohne {@code <set-service>} im Deskriptor: setClock belegt die BuilderFactory per Autowiring mit demo.Clock,
 * setArchive ruft niemand auf – für Archive gibt es keinen Service, das Plugin meldet den Setter als unbenutzt.
 */
public class NewsletterImpl implements Newsletter {
    private Clock clock;
    private Archive archive;

    public void setClock(Clock clock) { this.clock = clock; }
    public void setArchive(Archive archive) { this.archive = archive; }

    @Override
    public void publish(String text) {
    }
}
