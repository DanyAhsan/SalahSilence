package com.dany.salahsilence;
public class PrayerTime {
    private String name;
    private String startTime;
    private String endTime;
    private boolean isEnabled;

    public PrayerTime(String name, String startTime, String endTime, boolean isEnabled) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isEnabled = isEnabled;
    }

    public String getName() {
        return name;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public String getStartTime12HourFormat() {
        return convertTo12HourFormat(startTime);
    }

    public String getEndTime12HourFormat() {
        return convertTo12HourFormat(endTime);
    }

    private String convertTo12HourFormat(String time24) {
        try {
            String[] timeParts = time24.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            String period = hour >= 12 ? "PM" : "AM";
            hour = hour % 12;
            if (hour == 0) {
                hour = 12;
            }
            return String.format("%02d:%02d %s", hour, minute, period);
        } catch (Exception e) {
            return time24;
        }
    }
}
