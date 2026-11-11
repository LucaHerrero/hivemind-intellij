package com.example.demo;

/** Kein Service in hivemodule.xml – HiveMind kann eine Property dieses Typs nicht per Autowiring belegen. */
public interface Archive {
    void store(String text);
}
