package de.flashheart.rlgagent.hardware.pinhandler;

public class PinScheduleEvent {
    boolean on;
    long duration;

    public PinScheduleEvent(String on, String duration) {
        this.on = on.equalsIgnoreCase("on");
        if (duration.equals("∞")) this.duration = Long.MAX_VALUE;
        else this.duration = Long.parseLong(duration);
    }

    public boolean isOn() {
        return on;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return (isOn() ? "on" : "off") + "," + duration;
    }
}
