
package chatty.gui.components;

import chatty.User;
import chatty.gui.MainGui;
import chatty.gui.Message;
import chatty.gui.StyleManager;
import chatty.gui.StyleServer;
import chatty.gui.components.menus.ContextMenuListener;
import chatty.gui.components.menus.HighlightsContextMenu;
import chatty.gui.components.textpane.ChannelTextPane;
import chatty.util.api.Emoticon.EmoticonImage;
import chatty.util.api.Emoticons.TagEmotes;
import chatty.util.api.StreamInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Collection;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * Simple dialog that contains a ChannelTextPane with stream chat features
 * enabled (optional timeout of messages). Can have messages from several
 * channels redirected to it.
 * 
 * @author tduva
 */
public class StreamChat extends JDialog {
    
    private final ChannelTextPane textPane;
    private final ContextMenuListener contextMenuListener;
    
    public StreamChat(MainGui g, StyleManager styles, ContextMenuListener contextMenuListener,
            boolean startAtBottom) {
        super(g);
        this.contextMenuListener = contextMenuListener;
        setTitle("Stream Chat");

        textPane = new TextPane(g, styles, startAtBottom);
        textPane.setContextMenuListener(new MyContextMenuListener());
        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        textPane.setScrollPane(scroll);
        textPane.setPreferredSize(new Dimension(200,100));
        
        add(scroll, BorderLayout.CENTER);
        
        pack();
    }
    
    public void printMessage(Message message) {
        textPane.printMessage(message);
    }
    
    public void userBanned(User user) {
        textPane.userBanned(user);
    }
    
    public void setMessageTimeout(int seconds) {
        textPane.setMessageTimeout(seconds);
    }
    
    public void refreshStyles() {
        textPane.refreshStyles();
    }
    
    public void clear() {
        textPane.clearAll();
    }
    
    /**
     * Normal channel text pane modified a bit to fit the needs for this.
     */
    static class TextPane extends ChannelTextPane {
        
        public TextPane(MainGui main, StyleServer styleServer, boolean startAtBottom) {
            // Enables the "special" parameter to be able to remove old lines
            super(main, styleServer, true, startAtBottom);
            
            // Overriding constructor is required to set the custom context menu
            linkController.setDefaultContextMenu(new HighlightsContextMenu());
        }
        
    }
    
    /**
     * Redirect everything to the normal listener except the clear event.
     */
    private class MyContextMenuListener implements ContextMenuListener {
        
        @Override
        public void menuItemClicked(ActionEvent e) {
            if (e.getActionCommand().equals("clearHighlights")) {
                textPane.clearAll();
            }
            contextMenuListener.menuItemClicked(e);
        }

        @Override
        public void userMenuItemClicked(ActionEvent e, User user) {
            contextMenuListener.userMenuItemClicked(e, user);
        }

        @Override
        public void urlMenuItemClicked(ActionEvent e, String url) {
            contextMenuListener.urlMenuItemClicked(e, url);
        }

        @Override
        public void streamsMenuItemClicked(ActionEvent e, Collection<String> streams) {
            contextMenuListener.streamsMenuItemClicked(e, streams);
        }

        @Override
        public void streamInfosMenuItemClicked(ActionEvent e, Collection<StreamInfo> streamInfos) {
            contextMenuListener.streamInfosMenuItemClicked(e, streamInfos);
        }

        @Override
        public void emoteMenuItemClicked(ActionEvent e, EmoticonImage emote) {
            contextMenuListener.emoteMenuItemClicked(e, emote);
        }
    }
    
}
