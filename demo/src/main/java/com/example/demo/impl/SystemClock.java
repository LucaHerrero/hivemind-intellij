package com.example.demo.impl;

import com.example.demo.Clock;

public class SystemClock implements Clock {
    @Override
    public long now() { return System.currentTimeMillis(); }
}
