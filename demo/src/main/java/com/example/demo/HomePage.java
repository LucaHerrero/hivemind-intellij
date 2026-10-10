package com.example.demo;

import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.html.BasePage;

/** Tapestry-4-Seite: der Service wird per HiveMind-Object-Referenz injiziert. */
public abstract class HomePage extends BasePage {
    @InjectObject("service:demo.Mailer")
    public abstract Mailer getMailer();
}
