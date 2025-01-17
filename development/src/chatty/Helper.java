
package chatty;

import chatty.util.Replacer;
import chatty.util.StringUtil;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Some Chatty-specific static helper methods.
 * 
 * @author tduva
 */
public class Helper {
    
    public static final DecimalFormat VIEWERCOUNT_FORMAT = new DecimalFormat();
    
    public static String formatViewerCount(int viewerCount) {
        return VIEWERCOUNT_FORMAT.format(viewerCount);
    }
    
    /**
     * Parses comma-separated channels from a String.
     * 
     * @param channels The list channels to parse
     * @param prepend Whether to prepend # if necessary
     * @return Set of channels sorted as in the String
     */
    public static Set<String> parseChannelsFromString(String channels, boolean prepend) {
        String[] parts = channels.split(",");
        Set<String> result = new LinkedHashSet<>();
        for (String part : parts) {
            String channel = part.trim();
            if (validateChannel(channel)) {
                if (prepend && !channel.startsWith("#")) {
                    channel = "#"+channel;
                }
                result.add(StringUtil.toLowerCase(channel));
            }
        }
        return result;
    }
    
    public static String[] parseChannels(String channels, boolean prepend) {
        return parseChannelsFromString(channels, prepend).toArray(new String[0]);
    }
    
    public static String[] parseChannels(String channels) {
        return parseChannels(channels, true);
    }
    
    /**
     * Takes a Set of Strings and builds a single comma-seperated String of
     * streams out of it.
     * 
     * @param set
     * @return 
     */
    public static String buildStreamsString(Collection<String> set) {
        String result = "";
        String sep = "";
        for (String channel : set) {
            result += sep+channel.replace("#", "");
            sep = ", ";
        }
        return result;
    }
    
    public static String USERNAME_REGEX = "[a-zA-Z0-9_]+";
    
    /**
     * Kind of relaxed valiadation if a channel, which can have a leading # or
     * not.
     * 
     * @param channel
     * @return 
     */
    public static boolean validateChannel(String channel) {
        try {
            return channel.matches("(?i)^#{0,1}"+USERNAME_REGEX+"$");
        } catch (PatternSyntaxException | NullPointerException ex) {
            return false;
        }
    }
    
    /**
     * Checks if the given channel is a regular channel, which means it starts
     * with a # (and is valid otherwise).
     * 
     * @param channel
     * @return 
     */
    public static boolean isRegularChannel(String channel) {
        return validateChannel(channel) && channel.startsWith("#");
    }
    
    /**
     * Checks if the given name is a valid stream (no leading # and valid
     * otherwise).
     * 
     * @param stream
     * @return 
     */
    public static boolean validateStream(String stream) {
        try {
            return stream.matches("(?i)^"+USERNAME_REGEX+"$");
        } catch (PatternSyntaxException | NullPointerException ex) {
            return false;
        }
    }
    
    /**
     * Checks if the given stream/channel is valid and turns it into a channel
     * if necessary (leading # and all lowercase).
     *
     * @param channel The channel, valid or invalid, leading # or not.
     * @return The channelname with leading #, or null if channel was invalid.
     */
    public static String toValidChannel(String channel) {
        if (channel == null) {
            return null;
        }
        if (!validateChannel(channel)) {
            return null;
        }
        if (!channel.startsWith("#")) {
            channel = "#"+channel;
        }
        return StringUtil.toLowerCase(channel);
    }
    
    /**
     * If this is a valid channel name, then it turns it into a channel (which
     * means adding the # in front if necessary). Otherwise it just returns the
     * input in all lowercase.
     * 
     * @param chan
     * @return 
     */
    public static String toChannel(String chan) {
        if (chan == null) {
            return null;
        }
        if (!validateChannel(chan)) {
            return StringUtil.toLowerCase(chan);
        }
        if (!chan.startsWith("#")) {
            chan = "#"+chan;
        }
        return StringUtil.toLowerCase(chan);
    }
    
    /**
     * Removes any # from the String.
     * 
     * @param channel
     * @return 
     */
    public static String toStream(String channel) {
        if (channel == null) {
            return null;
        }
        return channel.replace("#", "");
    }
    
    /**
     * Makes a readable message out of the given reason code.
     * 
     * @param reason
     * @param reasonMessage
     * @return 
     */
    public static String makeDisconnectReason(int reason, String reasonMessage) {
        String result = "";
        
        switch (reason) {
            case Irc.ERROR_UNKNOWN_HOST:
                result = "Unknown host";
                break;
            case Irc.REQUESTED_DISCONNECT:
                result = "Requested";
                break;
            case Irc.ERROR_CONNECTION_CLOSED:
                result = "";
                break;
            case Irc.ERROR_REGISTRATION_FAILED:
                result = "Failed to complete login.";
                break;
            case Irc.ERROR_SOCKET_TIMEOUT:
                result = "Connection timed out.";
                break;
            case Irc.SSL_ERROR:
                result = "Could not established secure connection ("+reasonMessage+")";
                break;
            case Irc.ERROR_SOCKET_ERROR:
                result = reasonMessage;
                break;
        }
        
        if (!result.isEmpty()) {
            result = " ("+result+")";
        }
        
        return result;
    }
    

    /**
     * http://stackoverflow.com/questions/5609500/remove-jargon-but-keep-real-characters/5609532#5609532
     * 
     * Combining characters seem to affect performance sometimes. Opening the
     * User Info Dialog can take a noticeable amount of time to open if the
     * history contains these characters (or at least some of them).
     * 
     * Removing anything longer than 2 characters seemed to work well enough,
     * but keeps some legit stuff (or semi-legit stuff) intact.
     * 
     * Tests showed no clearly different performance compared to removing any
     * number of characters.
     */
    private static final Pattern COMBINING_CHARACTERS_STRICT
            = Pattern.compile("[\\u0300-\\u036f\\u0483-\\u0489\\u1dc0-\\u1dff\\u20d0-\\u20ff\\ufe20-\\ufe2f]{1,}");
    
    private static final Pattern COMBINING_CHARACTERS_LENIENT
            = Pattern.compile("[\\u0300-\\u036f\\u0483-\\u0489\\u1dc0-\\u1dff\\u20d0-\\u20ff\\ufe20-\\ufe2f]{3,}");
    
    public static final int FILTER_COMBINING_CHARACTERS_OFF = 0;
    public static final int FILTER_COMBINING_CHARACTERS_LENIENT = 1;
    public static final int FILTER_COMBINING_CHARACTERS_STRICT = 2;
    
    /**
     * Replaces combining characters in certain ranges with the given
     * replacement string.
     * 
     * @param text The input text
     * @param replaceWith The text to replace any matching characters with
     * @return The changed text
     */
    public static String filterCombiningCharacters(String text, String replaceWith, int mode) {
        if (mode == FILTER_COMBINING_CHARACTERS_STRICT) {
            return COMBINING_CHARACTERS_STRICT.matcher(text).replaceAll(replaceWith);
        } else if (mode == FILTER_COMBINING_CHARACTERS_LENIENT) {
            return COMBINING_CHARACTERS_LENIENT.matcher(text).replaceAll(replaceWith);
        }
        return text;
    }

    
    private static final Pattern ALL_UPERCASE_LETTERS = Pattern.compile("[A-Z]+");
    
    public static boolean isAllUppercaseLetters(String text) {
        return ALL_UPERCASE_LETTERS.matcher(text).matches();
    }
    
    private static final Replacer HTMLSPECIALCHARS_ENCODE;
    private static final Replacer HTMLSPECIALCHARS_DECODE;
    private static final Replacer TAGS_VALUE_DECODE;
    
    static {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("&amp;", "&");
        replacements.put("&lt;", "<");
        replacements.put("&gt;", ">");
        replacements.put("&quot;", "\"");
        
        Map<String, String> replacementsReverse = new HashMap<>();
        for (String key : replacements.keySet()) {
            replacementsReverse.put(replacements.get(key), key);
        }
        HTMLSPECIALCHARS_ENCODE = new Replacer(replacementsReverse);
        HTMLSPECIALCHARS_DECODE = new Replacer(replacements);
        
        Map<String, String> replacements2 = new HashMap<>();
        replacements2.put("\\\\s", " ");
        replacements2.put("\\\\n", "\n");
        replacements2.put("\\\\r", "\r");
        replacements2.put("\\\\:", ";");
        replacements2.put("\\\\\\\\", "\\");
        
        TAGS_VALUE_DECODE = new Replacer(replacements2);
    }
    
    public static String tagsvalue_decode(String s) {
        if (s == null) {
            return null;
        }
        return TAGS_VALUE_DECODE.replace(s);
    }
    
    public static String htmlspecialchars_decode(String s) {
        if (s == null) {
            return null;
        }
        return HTMLSPECIALCHARS_DECODE.replace(s);
    }
    
    public static String htmlspecialchars_encode(String s) {
        if (s == null) {
            return null;
        }
        return HTMLSPECIALCHARS_ENCODE.replace(s);
    }
    
    
    
    private static final Pattern UNDERSCORE = Pattern.compile("_");
    
    public static String replaceUnderscoreWithSpace(String input) {
        return UNDERSCORE.matcher(input).replaceAll(" ");
    }
    
    
    
    public static <T> List<T> subList(List<T> list, int start, int end) {
        List<T> subList = new ArrayList<>();
        for (int i=start;i<end;i++) {
            if (list.size() > i) {
                subList.add(list.get(i));
            } else {
                break;
            }
        }
        return subList;
    }
    
    public static void unhandledException() {
        String[] a = new String[0];
        String b = a[1];
    }
    
    public static boolean arrayContainsInt(int[] array, int test) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == test) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Splits up a String in the format "Integer1,Integer2" and returns the
     * {@code Integer}s.
     *
     * @param input The input String
     * @return Both {@code Integer} values as a {@code IntegerPair} or
     * {@code null} if the format was invalid
     * @see IntegerPair
     */
    public static IntegerPair getNumbersFromString(String input) {
        String[] split = input.split(",");
        if (split.length != 2) {
            return null;
        }
        try {
            int a = Integer.parseInt(split[0]);
            int b = Integer.parseInt(split[1]);
            return new IntegerPair(a, b);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    
    /**
     * Gets two {@code Integer} values on creation, which can be accessed with
     * the {@code final} attributes {@code a} and {@code b}.
     */
    public static class IntegerPair {
        public final int a;
        public final int b;
        
        public IntegerPair(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }
    
    
    
    public static final void main(String[] args) {
//        System.out.println(htmlspecialchars_encode("< >"));
//        System.out.println(shortenTo("abcd", 0));
//        System.out.println(shortenTo("abcd", 1));
//        System.out.println(shortenTo("abcd", 2));
//        System.out.println(shortenTo("abcd", 3));
//        System.out.println(shortenTo("abcd", 4));
//        System.out.println(shortenTo("abcd", 5));
//        System.out.println(shortenTo("abcd", -2));
//        System.out.println(shortenTo("abcd", -3));
//        System.out.println(shortenTo("abcd", -4));
//        long start = System.currentTimeMillis();
//        for (int i=0;i<100000;i++) {
//            htmlspecialchars_encode("&");
//        }
//        System.out.println(System.currentTimeMillis() - start);
        
        System.out.println(Arrays.asList(parseChannels("b,a,b,c")));
    }
    
    /**
     * Checks if the id matches the given User. The id can be one of: $mod,
     * $sub, $turbo, $admin, $broadcaster, $staff, $bot. If the user has the
     * appropriate user status, this returns true. If the id is unknown or the
     * user doesn't have the required status, this returns false.
     * 
     * @param id The id that is required
     * @param user The User object to check against
     * @return true if the id is known and matches the User, false otherwise
     */
    public static boolean matchUserStatus(String id, User user) {
        if (id.equals("$mod")) {
            if (user.isModerator()) {
                return true;
            }
        } else if (id.equals("$sub")) {
            if (user.isSubscriber()) {
                return true;
            }
        } else if (id.equals("$turbo")) {
            if (user.hasTurbo()) {
                return true;
            }
        } else if (id.equals("$admin")) {
            if (user.isAdmin()) {
                return true;
            }
        } else if (id.equals("$broadcaster")) {
            if (user.isBroadcaster()) {
                return true;
            }
        } else if (id.equals("$staff")) {
            if (user.isStaff()) {
                return true;
            }
        } else if (id.equals("$bot")) {
            if (user.isBot()) {
                return true;
            }
        } else if (id.equals("$globalmod")) {
            if (user.isGlobalMod()) {
                return true;
            }
        } else if (id.equals("$anymod")) {
            if (user.isAdmin() || user.isBroadcaster() || user.isGlobalMod()
                    || user.isModerator() || user.isStaff()) {
                return true;
            }
        }
        return false;
    }
    
    public static String checkHttpUrl(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("//")) {
            url = "https:"+url;
        }
        return url;
    }
    
    public static String systemInfo() {
        return String.format("Java: %s (%s) OS: %s (%s/%s)",
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"));
    }
    
    /**
     * Top Level Domains (only relevant for URLs not starting with http or www).
     */
    private static final String TLD = "(?:tv|com|org|edu|gov|uk|net|ca|de|jp|fr|au|us|ru|ch|it|nl|se|no|es|me|gl|fm|io)";
    
    private static final String MID = "[-A-Z0-9+&@#/%=~_|$?!:,.()]";
    
    private static final String END = "[A-Z0-9+&@#/%=~_|$)]";
    
    /**
     * Start of the URL.
     */
    private static final String S1 = "(?:(?:https?)://|www\\.)";
    
    /**
     * Start of the URL (second possibility).
     */
    private static final String S2 = "(?:[A-Z0-9.-]+\\."+TLD+"\\b)";
    
    /**
     * Complete URL.
     */
    private static final String T1 = "(?:(?:"+S1+"|"+S2+")"+MID+"*"+END+")";
    
    /**
     * Complete URL (only domain).
     */
    private static final String T2 = "(?:"+S2+")";
    
    /**
     * The regex String for finding URLs in messages.
     */
    private static final String URL_REGEX = "(?i)\\b"+T1+"|"+T2;
    
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);
    
    public static Pattern getUrlPattern() {
        return URL_PATTERN;
    }
    
}
