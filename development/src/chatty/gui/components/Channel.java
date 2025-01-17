
package chatty.gui.components;

import chatty.gui.MouseClickedListener;
import chatty.gui.StyleManager;
import chatty.gui.StyleServer;
import chatty.gui.MainGui;
import chatty.User;
import chatty.gui.Message;
import chatty.gui.components.menus.ContextMenuListener;
import chatty.gui.components.textpane.ChannelTextPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A single channel window, combining styled text pane, userlist and input box.
 * 
 * @author tduva
 */
public class Channel extends JPanel {
    
    public enum Type {
        NONE, CHANNEL, WHISPER
    }
    
    private static final int DIVIDER_SIZE = 5;
    
    private final ChannelEditBox input;
    private final ChannelTextPane text;
    private final UserList users;
    private final JSplitPane mainPane;
    private final JScrollPane userlist;
    private final JScrollPane west;
    private final StyleServer styleManager;
    private final MainGui main;
    private Type type;
    
    private boolean userlistEnabled = true;
    private int previousUserlistWidth;
    private int userlistMinWidth;
    
    private String name;

    public Channel(String name, Type type, MainGui main, StyleManager styleManager,
            ContextMenuListener contextMenuListener) {
        this.setLayout(new BorderLayout());
        this.styleManager = styleManager;
        this.name = name;
        this.main = main;
        this.type = type;
        
        // Text Pane
        text = new ChannelTextPane(main,styleManager,false,false); //creates the channel last var false starts adding at top however new msgs are bottom
        text.setContextMenuListener(contextMenuListener);
        
        
        setTextPreferredSizeTemporarily();
        
        west = new JScrollPane(text);
        text.setScrollPane(west);
        //System.out.println(west.getVerticalScrollBarPolicy());
        //System.out.println(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        west.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        // PageUp/Down hotkeys / Scrolling
        InputMap westScrollInputMap = west.getInputMap(WHEN_IN_FOCUSED_WINDOW);
        westScrollInputMap.put(KeyStroke.getKeyStroke("PAGE_UP"), "pageUp");
        west.getActionMap().put("pageUp", new ScrollAction("pageUp", west.getVerticalScrollBar()));
        westScrollInputMap.put(KeyStroke.getKeyStroke("PAGE_DOWN"), "pageDown");
        west.getActionMap().put("pageDown", new ScrollAction("pageDown", west.getVerticalScrollBar()));
        west.getVerticalScrollBar().setUnitIncrement(40);

        
        // User list
        users = new UserList(contextMenuListener, main.getUserListener());
        userlist = new JScrollPane(users);
        
        
        // Split Pane
        mainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,west,userlist);
        mainPane.setResizeWeight(1);
        mainPane.setDividerSize(DIVIDER_SIZE);
        
        // Text input
        input = new ChannelEditBox(40);
        input.addActionListener(main.getActionListener());
        input.setCompletionServer(new InputCompletionServer());
        

        // Add components
        add(mainPane, BorderLayout.CENTER);
        add(input, BorderLayout.SOUTH); //changing this to north will put the text input on top

        input.requestFocusInWindow();
        setStyles();
    }
    
    public void cleanUp() {
        text.cleanUp();
        input.cleanUp();
    }
    
    public void setType(Type type) {
        this.type = type;
    }
    
    public Type getType() {
        return type;
    }
    
    public void setScrollbarAlways(boolean always) {
        west.setVerticalScrollBarPolicy(always ? 
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS : JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    }
    
    public void setMouseClickedListener(MouseClickedListener listener) {
        text.setMouseClickedListener(listener);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Gets the name of the stream (without leading #) if it is a stream channel
     * (and thus has a leading #) ;)
     * @return 
     */
    public String getStreamName() {
        if (name.startsWith("#")) {
            return name.substring(1);
        }
        return null;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void addUser(User user) {
        users.addUser(user);
    }
    
    public void removeUser(User user) {
        users.removeUser(user);
    }
    
    public void updateUser(User user) {
        users.updateUser(user);
    }
    
    public void resortUserlist() {
        users.resort();
    }
    
    public void clearUsers() {
        users.clearUsers();
    }
    
    public int getNumUsers() {
        return users.getNumUsers();
    }

    private class InputCompletionServer implements AutoCompletionServer {
        
        private final UserSorterNew userSorterNew = new UserSorterNew();
        private final UserSorterAlphabetic userSorterAlphabetical = new UserSorterAlphabetic();
        
        private final Set<String> commands = new TreeSet<>(Arrays.asList(new String[]{
            "subscribers", "subscribersOff", "timeout", "ban", "unban", "host", "unhost", "clear", "mods",
            "part", "close", "reconnect", "slow", "slowOff", "r9k", "r9koff",
            "connection", "uptime", "dir", "wdir", "openDir", "openWdir", "releaseInfo", "openBackupDir",
            "clearChat", "refresh", "changetoken", "testNotification", "server",
            "set", "add", "clearSetting", "remove", "customCompletion",
            "clearStreamChat", "getStreamChatSize", "setStreamChatSize", "streamChatTest", "openStreamChat",
            "customEmotes", "reloadCustomEmotes", "addStreamHighlight", "openStreamHighlights",
            "ignore", "unignore", "ignoreWhisper", "unignoreWhisper", "ignoreChat", "unignoreChat"
        }));
        
        private void updateSettings() {
            input.setCompletionMaxItemsShown((int) main.getSettings().getLong("completionMaxItemsShown"));
            input.setCompletionShowPopup(main.getSettings().getBoolean("completionShowPopup"));
            input.setCompleteToCommonPrefix(main.getSettings().getBoolean("completionCommonPrefix"));
        }
        
        @Override
        public CompletionItems getCompletionItems(String type, String prefix, String search) {
            updateSettings();
            search = search.toLowerCase(Locale.ENGLISH);
            if (type == null) {
                return getRegularCompletionItems(prefix, search);
            } else if (type.equals("special")) {
                return getSpecialItems(prefix, search);
            }
            return new CompletionItems();
        }
        
        private CompletionItems getRegularCompletionItems(String prefix, String search) {
            List<String> items;
            if (prefix.startsWith("/")
                    && (prefix.equals("/set ") || prefix.equals("/get ")
                    || prefix.equals("/add ") || prefix.equals("/remove ")
                    || prefix.equals("/clearSetting ")
                    || prefix.equals("/reset "))) {
                items = filterCompletionItems(main.getSettingNames(), search);
                input.setCompleteToCommonPrefix(true);
            } else if (prefix.equals("/")) {
                items = filterCompletionItems(commands, search);
            } else {
                items = getCompletionItemsNames(search);
            }
            return new CompletionItems(items, "");
        }
        
        private CompletionItems getSpecialItems(String prefix, String search) {
            if (prefix.endsWith(".")) {
                return new CompletionItems(getCustomCompletionItems(search), ".");
            } else {
                Collection<String> emotes = new LinkedList<>(main.getEmoteNames());
                emotes.addAll(main.getEmoteNamesPerStream(getStreamName()));
                return new CompletionItems(filterCompletionItems(emotes, search), "");
            }
        }
        
        private List<String> getCustomCompletionItems(String search) {
            String result = main.getCustomCompletionItem(search);
            List<String> list = new ArrayList<>();
            if (result != null) {
                list.add(result);
            }
            return list;
        }
        
        private List<String> filterCompletionItems(Collection<String> data,
                String search) {
            List<String> matched = new ArrayList<>();
            for (String name : data) {
                if (name.toLowerCase().startsWith(search)) {
                    matched.add(name);
                }
            }
            Collections.sort(matched);
            return matched;
        }
            
        private List<String> getCompletionItemsNames(String search) {
            List<User> matched = new ArrayList<>();
            for (User user : users.getData()) {
                if (user.nick.startsWith(search)) {
                    matched.add(user);
                }
            }
            switch (main.getSettings().getString("completionSorting")) {
                case "predictive":
                    Collections.sort(matched, userSorterNew);
                    break;
                case "alphabetical":
                    Collections.sort(matched, userSorterAlphabetical);
                    break;
                default:
                    Collections.sort(matched);
            }
            List<String> nicks = new ArrayList<>();
            for (User user : matched) {
                nicks.add(user.getRegularDisplayNick());
            }
            return nicks;
        }
        
        private class UserSorterNew implements Comparator<User> {

            @Override
            public int compare(User o1, User o2) {
                int s1 = o1.getActivityScore();
                int s2 = o2.getActivityScore();
                System.out.println(o1+" "+s1+" "+o2+" "+s2);
                if (s1 == s2) {
                    return o1.compareTo(o2);
                } else if (s1 > s2) {
                    return -1;
                }
                return 1;
            }
            
        }
        
        private class UserSorterAlphabetic implements Comparator<User> {

            @Override
            public int compare(User o1, User o2) {
                return o1.nick.compareToIgnoreCase(o2.nick);
            }
            
        }
        
    }

    public ChannelEditBox getInput() {
        return input;
    }
    
    public String getInputText() {
        return input.getText();
    }

    @Override
    public boolean requestFocusInWindow() {
        // Invoke later, because otherwise it wouldn't get focus for some
        // reason.
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                //System.out.println("requesting focus for " + name);
                input.requestFocusInWindow();
            }
        });
        return input.requestFocusInWindow();
        
    }
    
    
    // Messages
    
    public boolean search(String searchText) {
        return text.search(searchText);
    }
    
    public void resetSearch() {
        text.resetSearch();
    }
    
    public void printLine(String line) {
        text.printLine(line);
    }
    
    public void userBanned(User user) {
        text.userBanned(user);
    }
    
    public void printCompact(String type, User user) {
        text.printCompact(type, user);
    }
    
    public void printMessage(Message message) {
        text.printMessage(message);
    }
    
    
    // Style
    
    public void refreshStyles() {
        text.refreshStyles();
        setStyles();
    }
    
    private void setStyles() {
        input.setFont(styleManager.getFont());
        input.setBackground(styleManager.getColor("inputBackground"));
        input.setCaretColor(styleManager.getColor("inputForeground"));
        input.setForeground(styleManager.getColor("inputForeground"));
        users.setBackground(styleManager.getColor("background"));
        users.setForeground(styleManager.getColor("foreground"));
    }
    
    public void clearChat() {
        text.clearAll();
    }
    
    /**
     * Insert text into the input box at the current caret position.
     * 
     * @param text
     * @param withSpace 
     * @throws NullPointerException if the text is null
     */
    public void insertText(String text, boolean withSpace) {
        input.insertAtCaret(text, withSpace);
    }
    
    private static class ScrollAction extends AbstractAction {
        
        private final String action;
        private final JScrollBar scrollbar;
        
        ScrollAction(String action, JScrollBar scrollbar) {
            this.scrollbar = scrollbar;
            this.action = action;
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            int now = scrollbar.getValue();
            int height = scrollbar.getVisibleAmount();
            height = height - height / 10;
            int newValue = 0;
            switch (action) {
                case "pageUp": newValue = now - height; break;
                case "pageDown": newValue = now + height; break;
            }
            scrollbar.setValue(newValue);
        }
    }
    
    public final void setUserlistWidth(int width, int minWidth) {
        userlist.setPreferredSize(new Dimension(width, 10));
        userlist.setMinimumSize(new Dimension(minWidth, 0));
        userlistMinWidth = minWidth;
    }
    
    /**
     * Setting the preferred size to 0, so the text pane doesn't influence the
     * size of the userlist. Setting it back later so it doesn't flicker when
     * being scrolled up (and possibly other issues). This is an ugly hack, but
     * I don't know enough about this to find a proper solution.
     */
    private void setTextPreferredSizeTemporarily() {
        text.setPreferredSize(new Dimension(0, 0));
        Timer t = new Timer(5000, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                text.setPreferredSize(null);
            }
        });
        t.setRepeats(false);
        t.start();
    }
    
    /**
     * Toggle visibility for the text input box.
     */
    public final void toggleInput() {
        input.setVisible(!input.isVisible());
        revalidate();
    }
    
    /**
     * Enable or disable the userlist. As with setting the initial size, this
     * requires some hacky stuff to get the size back correctly.
     * 
     * @param enable 
     */
    public final void setUserlistEnabled(boolean enable) {
        if (enable) {
            userlist.setVisible(true);
            mainPane.setDividerSize(DIVIDER_SIZE);
            setUserlistWidth(previousUserlistWidth, userlistMinWidth);
            setTextPreferredSizeTemporarily();
            mainPane.setDividerLocation(-1);
        } else {
            previousUserlistWidth = userlist.getWidth();
            userlist.setVisible(false);
            mainPane.setDividerSize(0);
        }
        userlistEnabled = enable;
        revalidate();
    }
    
    /**
     * Toggle the userlist.
     */
    public final void toggleUserlist() {
        setUserlistEnabled(!userlistEnabled);
    }
    
    public void selectPreviousUser() {
        text.selectPreviousUser();
    }
    
    public void selectNextUser() {
        text.selectNextUser();
    }
    
    public void selectNextUserExitAtBottom() {
        text.selectNextUserExitAtBottom();
    }
    
    public void exitUserSelection() {
        text.exitUserSelection();
    }
    
    public void toggleUserSelection() {
        text.toggleUserSelection();
    }
    
    public User getSelectedUser() {
        return text.getSelectedUser();
    }
    
        
    @Override
    public String toString() {
        return String.format("%s '%s'", type, name);
    }
    
}
