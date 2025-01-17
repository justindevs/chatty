
package chatty.util.chatlog;

import chatty.Chatty;
import chatty.Helper;
import chatty.User;
import chatty.util.DateTime;
import chatty.util.api.StreamInfo.ViewerStats;
import chatty.util.settings.Settings;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Translates chat specific messages into loggable lines and sends them to the
 * LogManager.
 * 
 * @author tduva
 */
public class ChatLog {
    
    private static final Logger LOGGER = Logger.getLogger(ChatLog.class.getName());
    
    private SimpleDateFormat sdf;
    
    private final Map<String, Compact> compactForChannels;
    
    private final Settings settings;
    
    /**
     * Reference to the LogManager. This is null if the log path was invalid and
     * couldn't be created.
     */
    private final LogManager log;
    
    public ChatLog(Settings settings) {
        this.settings = settings;
        
        Path path = getPath();
        if (path == null) {
            log = null;
        } else {
            this.log = new LogManager(path);
        }
        compactForChannels = new HashMap<>();
        try {
            String timestamp = settings.getString("logTimestamp");
            if (!timestamp.equals("off")) {
                sdf = new SimpleDateFormat(timestamp+" ");
            }
        } catch (IllegalArgumentException ex) {
            sdf = null;
        }
    }
    
    /**
     * Gets either the default log path, or the custom one from the settings, if
     * not empty.
     * 
     * @return The Path to write the log files to, or null if the path could not
     * be created
     */
    private Path getPath() {
        String pathToUse = Chatty.getUserDataDirectory()+"logs";
        String customPath = settings.getString("logPath");
        if (!customPath.isEmpty()) {
            pathToUse = customPath;
        }
        try {
            return Paths.get(pathToUse);
        } catch (InvalidPathException ex) {
            LOGGER.warning("Invalid path for chatlog: "+pathToUse);
            return null;
        }
    }
    
    public void start() {
        if (log != null) {
            log.start();
        }
    }

    public void message(String channel, User user, String message, boolean action) {
        if (isEnabled(channel)) {
            String line;
            if (action) {
                line = timestamp()+"<"+user+">* "+message;
            } else {
                line = timestamp()+"<"+user+"> "+message;
            }
            writeLine(channel, line);
        }
    }

    public void info(String channel, String message) {
        if (isTypeEnabled("Info") && isEnabled(channel)) {
            writeLine(channel, timestamp()+message);
        }
    }
    
    public void viewerstats(String channel, ViewerStats stats) {
        if (isTypeEnabled("Viewerstats") && isEnabled(channel)) {
            if (stats != null && stats.isValid()) {
                writeLine(channel, timestamp()+stats);
                //System.out.println(stats);
            }
        }
    }
    
    public void viewercount(String channel, int viewercount) {
        if (isTypeEnabled("Viewercount") && isEnabled(channel)) {
            writeLine(channel, timestamp()+"VIEWERS: "
                    +Helper.formatViewerCount(viewercount));
        }
    }
    
    public void system(String channel, String message) {
        if (isEnabled(channel) && isTypeEnabled("System")) {
            writeLine(channel, timestamp()+message);
        }
    }
    
    private void writeLine(String channel, String message) {
        if (log != null) {
            compactClose(channel);
            log.writeLine(channel, message);
        }
    }
    
    public void compact(String channel, String type, User user) {
        if (isEnabled(channel)) {
            if ((type.equals("MOD") || type.equals("UNMOD")) && isTypeEnabled("Mod")
                    || (type.equals("JOIN") || type.equals("PART")) && isTypeEnabled("JoinPart")
                    || type.equals("BAN") && isTypeEnabled("Ban")) {
                compactAdd(channel, type, user);
            }
        }
    }
    
    private void compactAdd(String channel, String tpye, User user) {
        synchronized(compactForChannels) {
            getCompact(channel).add(tpye, user);
        }
    }
    
    private void compactClose(String channel) {
        synchronized(compactForChannels) {
            if (channel != null) {
                getCompact(channel).close();
            } else {
                for (Compact c : compactForChannels.values()) {
                    c.close();
                }
            }
        }
    }
    
    private Compact getCompact(String channel) {
        Compact c = compactForChannels.get(channel);
        if (c == null) {
            c = new Compact(channel);
            compactForChannels.put(channel, c);
        }
        return c;
    }
    
    private String timestamp() {
        if (sdf == null) {
            return "";
        }
        return DateTime.currentTime(sdf);
    }
    
    /**
     * Close chatlogging, which writes any remaining lines in the buffer closes
     * all files and stops the thread. This waits for the thread to finish, so
     * it can take some time.
     */
    public void close() {
        if (log != null) {
            compactClose(null);
            log.close();
        }
    }
    
    public void closeChannel(String channel) {
        if (log != null) {
            compactClose(channel);
            log.writeLine(channel, null);
        }
    }
    
    private boolean isEnabled(String channel) {
        if (log == null) {
            return false;
        }
        if (channel == null || channel.isEmpty()) {
            return false;
        }
        String mode = settings.getString("logMode");
        if (mode.equals("off")) {
            return false;
        }
        if (mode.equals("always")) {
            return true;
        }
        if (mode.equals("blacklist") && !settings.listContains("logBlacklist", channel)) {
            return true;
        }
        if (mode.equals("whitelist") && settings.listContains("logWhitelist", channel)) {
            return true;
        }
        return false;
    }
    
    private boolean isTypeEnabled(String type) {
        return settings.getBoolean("log"+type);
    }

    private class Compact {
        
        private static final String SEPERATOR = ", ";
        private static final int MAX_LENGTH = 10;
        private static final int MAX_TIME = 2000;
        
        private final String channel;
        
        private StringBuilder text;
        private int length;
        private long start;
        private String mode;
        
        public Compact(String channel) {
            this.channel = channel;
        }
        
        /**
         * Prints something in compact mode, meaning that nick events of the
         * same type appear in the same line, for as long as possible.
         *
         * This is mainly used for a compact way of printing
         * joins/parts/mod/unmod.
         *
         * @param type
         * @param user
         */
        protected void add(String type, User user) {
            String seperator = SEPERATOR;
            if (start(type)) {
                // If compact mode has actually been started for this print,
                // print prefix first
                text = new StringBuilder();
                text.append(timestamp());
                text.append(type);
                text.append(": ");
                seperator = "";
            }
            text.append(seperator);
            text.append(user.getDisplayNick());

            length++;
            // If max number of compact prints happened, close compact mode to
            // start a new line
            if (length >= MAX_LENGTH) {
                close();
            }
        }

        /**
         * Enters compact mode, closes it first if necessary.
         *
         * @param type
         * @return
         */
        private boolean start(String type) {

            // Check if max time has passed, and if so close first
            long timePassed = System.currentTimeMillis() - start;
            if (timePassed > MAX_TIME) {
                close();
            }

            // If this is another type, close first
            if (!type.equals(mode)) {
                close();
            }

            // Only start if not already/still going
            if (mode == null) {
                mode = type;
                start = System.currentTimeMillis();
                length = 0;
                return true;
            }
            return false;
        }

        /**
         * Leaves compact mode (if necessary) and logs the buffered text.
         */
        protected void close() {
            if (mode != null && log != null) {
                log.writeLine(channel, text.toString());
                //System.out.println("Compact: "+text.toString());
                mode = null;
            }
        }
        
    }
    
 
}
