package com.example.demo.impl;

import com.example.demo.Clock;

public class FixedClock implements Clock {
    @Override
    public long now() { return 0; }
}
