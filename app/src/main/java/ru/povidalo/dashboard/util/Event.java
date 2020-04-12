package ru.povidalo.dashboard.util;

public final class Event {
    public static class SunMovieUpdated {
        private final String filePath;
        
        public SunMovieUpdated(String filePath) {
            this.filePath = filePath;
        }
        
        public String getFilePath() {
            return filePath;
        }
    }

    public static class TimerStateUpdated {
        public final boolean running;
        public final int secondsLeft;

        public TimerStateUpdated(boolean running, final int secondsLeft) {
            this.running = running;
            this.secondsLeft = secondsLeft;
        }
    }
}