package com.example.demo.impl;

import com.example.demo.Mailer;
import com.example.demo.Settings;

import java.util.List;

public class MailerImpl implements Mailer {
    private Settings settings;
    private List<Host> hosts;
    private String sender;

    public void setSettings(Settings settings) { this.settings = settings; }
    public void setHosts(List<Host> hosts) { this.hosts = hosts; }
    public void setSender(String sender) { this.sender = sender; }

    @Override
    public void send(String to, String text) {
    }
}
