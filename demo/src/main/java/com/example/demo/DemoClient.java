package com.example.demo;

import org.apache.hivemind.Registry;
import org.apache.hivemind.impl.RegistryBuilder;

/** Strg+Klick auf die ids führt in hivemodule.xml; Umbenennen des Services passt sie mit an. */
public class DemoClient {
    public static void main(String[] args) {
        Registry registry = RegistryBuilder.constructDefaultRegistry();
        Mailer mailer = (Mailer) registry.getService("demo.Mailer", Mailer.class);
        Object hosts = registry.getConfiguration("demo.Hosts");
        mailer.send("someone@example.com", "Hallo");
    }
}
