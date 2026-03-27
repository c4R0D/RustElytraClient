package dev.rstminecraft.utils;

import dev.rstminecraft.RustElytraClient;

public class TimelinessCounter {
    private int count = 0;
    private final int updateInterval;
    private int LastUpdateTick = Integer.MIN_VALUE;

    public TimelinessCounter(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    public void accumulate() {
        if (RustElytraClient.currentTick - LastUpdateTick > updateInterval) {
            count = 1;
            LastUpdateTick = RustElytraClient.currentTick;
        } else {
            count++;
        }
    }

    public int getCount() {
        return (RustElytraClient.currentTick - LastUpdateTick > updateInterval) ? 0 : count;
    }

    public void clear(){
        count = 0;
        LastUpdateTick = RustElytraClient.currentTick;
    }
}
