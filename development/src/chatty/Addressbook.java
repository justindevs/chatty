
package chatty;

import chatty.util.FileWatcher;
import chatty.util.StringUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class stores {@code AddressbookEntry}s (which associate a username with
 * categories) and provides text commands and methods to modify/save/load those
 * entries.
 *
 * @author tduva
 */
public class Addressbook {
    
    private static final Logger LOGGER = Logger.getLogger(Addressbook.class.getName());
    
    private static final Charset CHARSET = Charset.forName("UTF-8");
    
    /**
     * Map of entries.
     */
    private final Map<String, AddressbookEntry> entries = new HashMap<>();
    
    /**
     * Each category that appears in any of the entries should be collected here
     * so it can be used in the GUI for more convenient editing.
     */
    private final Set<String> presetCategories = new TreeSet<>();
    
    private final Set<String> somewhatUniqueCategories = new HashSet<>();
    
    /**
     * The complete path and name of the file to save/load the entries.
     */
    private final String fileName;
    
    /**
     * The complete path and name of the file to read Addressbook commands from,
     * which can be used to modify the entries from outside the program.
     */
    private final String importFileName;
    
    /**
     * Whether the Addressbook was already saved this session.
     */
    private boolean saved;
    
    public Addressbook(String fileName, String importFilename) {
        this.fileName = fileName;
        this.importFileName = importFilename;
    }
    
    /**
     * Set a comma-seperated list of categories that should be unqiue to one
     * user when using the add/set commands.
     * 
     * @param cats Comma-seperated list of categories (spaces are removed)
     */
    public void setSomewhatUniqueCategories(String cats) {
        if (cats == null) {
            return;
        }
        synchronized(somewhatUniqueCategories) {
            somewhatUniqueCategories.clear();
            String[] split = cats.split(",");
            for (String cat : split) {
                if (!cat.trim().isEmpty()) {
                    somewhatUniqueCategories.add(cat.trim());
                }
            }
        }
    }
    
    /**
     * Parses text commands. Commands can be:
     * 
     * <ul>
     * <li>{@code get <name>}</li>
     * <li>{@code add <name> [categories]}</li>
     * <li>{@code set <name> <categories>}</li>
     * <li>{@code remove <name> [categories]}</li>
     * <li>{@code renameCategory <oldCategoryName> <newCategoryName>}</li>
     * <li>{@code removeCategory <categoryName>}</li>
     * <li>{@code change <name> <categoryChange>}</li>
     * <li>{@code info}</li>
     * </ul>
     * 
     * @param text The text to parse.
     * @return An appropriate text response to the command, either a message
     * showing the result of the command or an error message.
     * @throws NullPointerException if text is null
     */
    public synchronized String command(String text) {
        text = StringUtil.removeDuplicateWhitespace(text).trim();
        if (text.isEmpty()) {
            return "Invalid command.";
        }
        String[] split = text.split(" ", 2);
        String command = split[0];
        String[] parameters = new String[0];
        String parameter = "";
        if (split.length == 2) {
            parameters = split[1].split(" ");
            parameter = split[1].trim();
        }
        if (command.equals("get")) {
            return commandGet(parameters);
        } else if (command.equals("add")) {
            return commandAdd(parameters);
        } else if (command.equals("set")) {
            return commandSet(parameters);
        } else if (command.equals("remove")) {
            return commandRemove(parameters);
        } else if (command.equals("renameCategory")) {
            return commandRenameCategory(parameters);
        } else if (command.equals("removeCategory")) {
            return commandRemoveCategory(parameters);
        } else if (command.equals("change")) {
            return commandChange(parameter);
        } else if (command.equals("info")) {
            return commandInfo();
        }
        return "Invalid command.";
    }
    
    /**
     * Display the categories for this name, if it exists.
     * 
     * @param parameters The array of String parameters, which in this case
     * should only be the name to look up
     * @return The response containing the name and categories or an appropriate
     * error message.
     */
    private String commandGet(String[] parameters) {
        if (parameters.length == 1) {
            String name = parameters[0].trim();
            AddressbookEntry entry = get(name);
            if (entry == null) {
                return "No entry for '"+name+"'.";
            }
            return "'"+name+"' has categories "+categoriesToString(entry.getCategories())+".";
        } else {
            return "Get: Invalid number of parameters.";
        }
    }
    
    /**
     * Add an entry or categories to an entry.
     * 
     * @param parameters The array of String parameters, in this case:
     * {@code <name>,[categorires]}
     * @return 
     * @see add(String, String)
     */
    private String commandAdd(String[] parameters) {
        if (parameters.length == 1) {
            String name = parameters[0].trim();
            if (add(name, "") == null) {
                return "Added '" + name + "'.";
            }
            return "Didn't add '" + name + "' (already present).";
        } else if (parameters.length == 2) {
            String name = parameters[0].trim();
            String categoriesString = parameters[1].trim();
            Set<String> categories = getCategoriesFromString(categoriesString);
            AddressbookEntry previous = get(name);
            clearSomewhatUniqueCategories(categories);
            AddressbookEntry result = add(name, categories);
            if (result == null) {
                return "Added '" + name + "' with categories "
                        + "'"+categoriesString+"'.";
            }
            Set<String> resultCategories = result.getCategories();
            if (previous.getCategories().equals(resultCategories)) {
                return "Didn't change '"+name+"', categories already "
                        +categoriesToString(resultCategories)+".";
            }
            return "Changed '" + name + "', categories now "
                    + categoriesToString(result.getCategories())+".";
        } else {
            return "Add: Invalid number of parameters.";
        }
    }
    
    /**
     * Changes an entries categories (creating it if necessary).
     * 
     * @param parameter All parameters as a single String
     * @return The response of the command
     * @see changeCategories(Set, String)
     * @see set(String, Set)
     */
    private String commandChange(String parameter) {
        String[] parameters = parameter.split(" ", 2);
        if (parameters.length == 2) {
            String name = parameters[0].trim();
            AddressbookEntry current = get(name);
            Set<String> categories;
            if (current == null) {
                categories = new HashSet<>();
            } else {
                categories = current.getCategories();
            }
            Set<String> changedCats = changeCategories(categories, parameters[1]);
            set(name, changedCats);
            String catOutput = categoriesToString(changedCats);
            if (current == null) {
                return "Added '"+name+"' with categories "+catOutput+".";
            } else if (categories.equals(changedCats)) {
                return "Didn't change '"+name+"', categories already "+catOutput+".";
            }
            return "Changed '"+name+"', categories now "
                    +catOutput+".";
        } else {
            return "Change: Invalid number of parameters.";
        }
    }
    
    /**
     * Set an entry to the given categories, creating it if necessary.
     * 
     * @param parameters
     * @return 
     * @see set(String, Set)
     */
    private String commandSet(String[] parameters) {
        if (parameters.length == 2) {
            String name = parameters[0].trim();
            String categoriesString = parameters[1].trim();
            Set<String> categories = getCategoriesFromString(categoriesString);
            AddressbookEntry previousEntry = get(name);
            clearSomewhatUniqueCategories(categories);
            set(name, categories);
            String categoriesOutput = categoriesToString(categories);
            if (previousEntry == null) {
                return "Added '"+name+"' with categories "+categoriesOutput+".";
            } else if (previousEntry.getCategories().equals(categories)) {
                return "Didn't change '"+name+"', categories already "+categoriesOutput+".";
            }
            return "Set '"+name+"' to categories "+categoriesOutput+".";
        }
        return "Set: Invalid number of parameters.";
    }
    
    private void clearSomewhatUniqueCategories(Set<String> catsToCheck) {
        for (String cat : catsToCheck) {
            synchronized(somewhatUniqueCategories) {
                if (somewhatUniqueCategories.contains(cat)) {
                    removeCategory(cat);
                }
            }
        }
    }
    
    /**
     * Removes the given name or categories from the given name if categories
     * are given.
     * 
     * @param parameters
     * @return 
     * @see remove(String)
     * @see remove(String, Set)
     */
    private String commandRemove(String[] parameters) {
        if (parameters.length == 1) {
            String name = parameters[0].trim();
            if (remove(name) == null) {
                return "Didn't remove '" + name + "' (entry not present).";
            }
            return "Removed '" + name + "'.";
        } else if (parameters.length == 2) {
            String name = parameters[0].trim();
            Set<String> categories
                    = getCategoriesFromString(parameters[1].trim());
            AddressbookEntry result = remove(name, categories);
            if (result == null) {
                return "Didn't remove anything from '" + name + "' (entry not present).";
            }
            return "Removed categories " + categoriesToString(categories)
                    + " from '" + name + "' (categories now "
                    + categoriesToString(result.getCategories()) + ").";
        } else {
            return "Remove: Invalid number of parameters.";
        }
    }
    
    /**
     * Renames any occurences of categories of one name to the other.
     * 
     * @param parameters
     * @return 
     * @see renameCategory(String, String)
     */
    private String commandRenameCategory(String[] parameters) {
        if (parameters.length != 2) {
            return "Rename category: Invalid number of parameters.";
        }
        String oldCategoryName = parameters[0];
        String newCategoryName = parameters[1];
        int result = renameCategory(oldCategoryName, newCategoryName);
        return "Renamed category '"+oldCategoryName+"'->'"+newCategoryName+"' "
                + "in "+result+" entries.";
    }
    
    /**
     * Removes the given categories from all entries.
     * 
     * @param parameters An array of String parameters, in this case only the
     * name of the category to remove
     * @return The response containing the removed category and the number of
     * entries affected or an error message.
     * @see removeCategory(String)
     */
    private String commandRemoveCategory(String[] parameters) {
        if (parameters.length != 1) {
            return "Remove category: Invalid number of parameters.";
        }
        int result = removeCategory(parameters[0]);
        return "Removed category '"+parameters[0]+"' from "+result+" entries.";
    }
    
    /**
     * Shows general info about the addressbook.
     * 
     * @return The response containing the number of entries.
     * @see getNumEntries()
     */
    private String commandInfo() {
        return "Number of entries: "+getNumEntries()+" / Used categories: "+getCategories();
    }
    
    /**
     * Enables auto import, which means it starts looking for file changes in
     * the Addressbook import file.
     */
    public void enableAutoImport() {
        FileWatcher.createFileWatcher(Paths.get(importFileName), new FileWatcher.FileChangedListener() {

            @Override
            public void fileChanged() {
                LOGGER.info("[AddressbookImport] Detected file change: Auto import..");
                importFromFile();
            }
        });
    }
    
    /**
     * Reads the commands from the import file and performs them.
     */
    public synchronized void importFromFile() {
        Path file = Paths.get(importFileName);
        try (BufferedReader reader = Files.newBufferedReader(file, CHARSET)) {
            LOGGER.info("[AddressbookImport] "+file.toAbsolutePath());
            String line;
            while((line = reader.readLine()) != null) {
                String result = command(line);
                LOGGER.info(String.format("[AddressbookImport] %s [%s]", result, line));
            }
        } catch (IOException ex) {
            LOGGER.warning("Failed importing addressbook from file: "+ex);
        }
    }
    
    /**
     * Adds a new entry with the given name and categories or adds the
     * categories if an entry already exists.
     * 
     * @param name The name, should not be null or empty.
     * @param categories The categories as a comma-seperated String, can not be
     *  null.
     * @return The changed entry or <tt>null</tt> if no entry for this name
     * existed before.
     */
    public AddressbookEntry add(String name, String categories) {
        return add(name, getCategoriesFromString(categories));
    }
    
    /**
     * Adds a new name with the given categories or adds the categories if the
     * name already exists.
     * 
     * @param name The name, shouldn't be empty or null.
     * @param categories The categories, can be empty, but not null.
     * @return The changed entry if the name already existed, or <tt>null</tt>
     * if it didn't.
     */
    public synchronized AddressbookEntry add(String name, Set<String> categories) {
        name = name.toLowerCase(Locale.ENGLISH);
        addCategories(categories);
        if (!entries.containsKey(name)) {
            set(name, categories);
            return null;
        } else {
            AddressbookEntry current = entries.get(name);
            AddressbookEntry changed = new AddressbookEntry(current, categories);
            entries.put(name, changed);
            return changed;
        }
    }
    
    /**
     * Sets the categories for this name as given, replacing any already
     * existing entry for this name.
     * 
     * @param name The name, can't be empty or null
     * @param categories The categories, can be empty, but not null
     */
    public synchronized void set(String name, Set<String> categories) {
        addCategories(categories);
        entries.put(StringUtil.toLowerCase(name), new AddressbookEntry(name, categories));
    }
    
    /**
     * Sets this entry, replacing any already existing entry.
     * 
     * @param entry The entry, can't be null.
     */
    public synchronized void set(AddressbookEntry entry) {
        addCategories(entry.getCategories());
        entries.put(entry.getName(), entry);
    }
    
    /**
     * Removes the entry with the name in the given entry.
     * 
     * @param entry 
     */
    public synchronized void remove(AddressbookEntry entry) {
        entries.remove(entry.getName());
    }
    
    /**
     * Removes the entry for the given name.
     * 
     * @param name The name, should not be empty or null
     * @return The entry that was removed.
     */
    public synchronized AddressbookEntry remove(String name) {
        return entries.remove(name.toLowerCase());
    }
    
    public AddressbookEntry remove(String name, String categories) {
        return remove(name, getCategoriesFromString(categories));
    }
    
    /**
     * Removes the given categories from the entry with the given name.
     * 
     * @param name The name of the entry (can't be null or empty).
     * @param categoriesToRemove The categories to remove (can't be null).
     * @return The changed entry or null if no entry was found for that name.
     */
    public synchronized AddressbookEntry remove(String name, Set<String> categoriesToRemove) {
        name = StringUtil.toLowerCase(name);
        AddressbookEntry entry = entries.get(name);
        if (entry != null) {
            Set<String> currentCategories = entry.getCategories();
            for (String category : categoriesToRemove) {
                currentCategories.remove(category);
            }
            AddressbookEntry changed = new AddressbookEntry(name, currentCategories);
            entries.put(name, changed);
            return changed;
        }
        return null;
    }
    
    /**
     * Removes the entry with the given name and sets the new entry, possibly
     * replacing an entry with the same name.
     * 
     * @param name
     * @param entry 
     */
    public synchronized void rename(String name, AddressbookEntry entry) {
        entries.remove(StringUtil.toLowerCase(name));
        set(entry);
    }
    
    /**
     * Renames the category with <tt>currentName</tt> to <tt>newName</tt> in
     * all entries.
     * 
     * @param currentName The name of the category to be renamed.
     * @param newName The new name of the category.
     * @return The number of entries affected (that contained the category).
     */
    public synchronized int renameCategory(String currentName, String newName) {
        int count = 0;
        for (Map.Entry<String, AddressbookEntry> entry : entries.entrySet()) {
            if (entry.getValue().hasCategory(currentName)) {
                AddressbookEntry changedEntry
                        = renameCategory(entry.getValue(), currentName, newName);
                entry.setValue(changedEntry);
                count++;
            }
        }
        return count;
    }
    
    /**
     * Removes the category with <tt>categoryName</tt> from all entries.
     * 
     * @param categoryName The name of the category to be removed.
     * @return The number of entries affected (that contained the category).
     */
    public synchronized int removeCategory(String categoryName) {
        int count = 0;
        for (Map.Entry<String, AddressbookEntry> entry : entries.entrySet()) {
            if (entry.getValue().hasCategory(categoryName)) {
                AddressbookEntry changedEntry
                        = renameCategory(entry.getValue(), categoryName, null);
                entry.setValue(changedEntry);
                count++;
            }
        }
        return count;
    }
    
    /**
     * Creates a new AddressbookEntry from <tt>entry</tt> that contains the
     * renamed version of <tt>oldCategoryName</tt>, or doesn't contain the
     * category at all if <tt>newCategoryName</tt> was null.
     * 
     * @param entry The entry to change.
     * @param oldCategoryName The name of the category to change.
     * @param newCategoryName The new name of the category, shouldn't be empty,
     *  can be null to not rename but instead remove the category.
     * @return The changed <tt>AddressbookEntry</tt> (or rather a new one).
     */
    public static AddressbookEntry renameCategory(AddressbookEntry entry, String oldCategoryName, String newCategoryName) {
        Set<String> categories = entry.getCategories();
        categories.remove(oldCategoryName);
        if (newCategoryName != null) {
            categories.add(newCategoryName);
        }
        return new AddressbookEntry(entry.getName(), categories);
    }
    
    /**
     * Gets the {@link AddressbookEntry} for the given name.
     * 
     * @param name The name of the entry.
     * @return The <tt>AddressbookEntry</tt> or {@code null} if no entry for
     * this name exists.
     */
    public synchronized AddressbookEntry get(String name) {
        return entries.get(StringUtil.toLowerCase(name));
    }
    
    /**
     * Returns all entries.
     * 
     * @return A list of all entries.
     */
    public synchronized List<AddressbookEntry> getEntries() {
        return new ArrayList<>(entries.values());
    }
    
    /**
     * Turns a String of comma-seperated categories into a Set of categories.
     * 
     * @param categoriesString The categories.
     * @return The Set of categories.
     */
    public static Set<String> getCategoriesFromString(String categoriesString) {
        Set<String> categories = new HashSet<>();
        String[] split = categoriesString.split(",");
        for (String category : split) {
            category = StringUtil.toLowerCase(category.trim());
            if (!category.isEmpty() && !category.contains(" ")) {
                categories.add(category);
            }
        }
        return categories;
    }
    
    /**
     * Returns a version of {@code present} that is modified by the changes
     * specified in {@code change}. Changes are sets categories that have to be
     * prepended by + to add them, - to remove them and ! to toggle them (add if
     * not present and remove if present). Categories are seperated by comma,
     * sets of categories are seperated by space.
     * 
     * <p>Does not modifiy the {@code present} parameter, but performs a copy
     * instead, which is then modified.</p>
     * 
     * @param present The categories to modify
     * @param change The String to change the categories
     * @return The changed categories
     */
    public static Set<String> changeCategories(Set<String> present, String change) {
        Set<String> result = new HashSet<>(present);
        Pattern p = Pattern.compile("(\\+|-|!)(\\S+)");
        Matcher m = p.matcher(change);
        while (m.find()) {
            String action = m.group(1);
            Set<String> categories = getCategoriesFromString(m.group(2));
            if (action.equals("+")) {
                result.addAll(categories);
            } else if (action.equals("-")) {
                result.removeAll(categories);
            } else if (action.equals("!")) {
                for (String cat : categories) {
                    if (present.contains(cat)) {
                        result.remove(cat);
                    } else {
                        result.add(cat);
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * Turns a Set of categories into a comma-seperated String of categories.
     * 
     * @param categories The Set of categories.
     * @return The String of categories.
     */
    public static String getStringFromCategories(Collection<String> categories) {
        return StringUtil.join(categories, ",");
    }
    
    public static String categoriesToString(Collection<String> categories) {
        return "'"+getStringFromCategories(categories)+"'";
    }
    
    /**
     * Loads the addressbook from file.
     */
    public synchronized void loadFromFile() {
        entries.clear();
        
        // DEBUG stuff
//        for (int i=0;i<10000;i++) {
//            AddressbookEntry e = new AddressbookEntry("name"+i, new HashSet<String>());
//            entries.put("name"+i, e);
//        }
        
        Path file = Paths.get(fileName);
        try (BufferedReader reader = Files.newBufferedReader(file, CHARSET)) {
            String line;
            do {
                line = reader.readLine();
                AddressbookEntry parsedEntry = parseLine(line);
                if (parsedEntry != null) {
                    entries.put(parsedEntry.getName(), parsedEntry);
                }
            } while (line != null);
        } catch (IOException ex) {
            LOGGER.warning("Error reading addressbook: "+ex.getLocalizedMessage());
            // This may not make too much sense because it also shows when no
            // addressbook was saved yet
            //LOGGER.log(Logging.USERINFO, "Error reading addressbook.");
        }
        LOGGER.info("Read "+entries.size()+" addressbook entries from "+fileName);
        scanCategories();
    }
    
    /**
     * Parses a single line from the addressbook file and turns it into an
     * <tt>AddresssbookEntry</tt>-object.
     * 
     * @param line The line of text in the format "<tt>name cat1,cat2</tt>".
     * @return The <tt>AddressbookEntry</tt> object or <tt>null</tt> if it
     * wasn't a valid line.
     */
    private AddressbookEntry parseLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] split = line.trim().split(" ");
        if (split.length == 1) {
            String name = split[0];
            return new AddressbookEntry(name, new HashSet<String>());
        }
        if (split.length == 2) {
            String name = split[0];
            Set<String> categories = getCategoriesFromString(split[1]);
            return new AddressbookEntry(name, categories);
        }
        return null;
    }
    
    public synchronized void saveToFileOnce() {
        if (!saved) {
            saveToFile();
        }
    }
    
    /**
     * Saves all entries to file.
     */
    public synchronized void saveToFile() {
        Path file = Paths.get(fileName);
        LOGGER.info("Writing addressbook to "+fileName);
        System.out.println("Saving addressbook.");
        try (BufferedWriter writer = Files.newBufferedWriter(file, CHARSET)) {
            for (AddressbookEntry entry : entries.values()) {
                writer.write(makeLine(entry));
                writer.newLine();
            }
            saved = true;
        } catch (IOException ex) {
            LOGGER.warning("Error writing addressbook: "+ex.getLocalizedMessage());
        }
    }
    
    /**
     * Creates a line to write to the file from a single <tt>AddressbookEntry</tt>.
     * 
     * @param entry The <tt>AddressbookEntry</tt>.
     * @return A line in the form "<tt>name cat1,cat2</tt>".
     */
    private String makeLine(AddressbookEntry entry) {
        return entry.getName()+" "+getStringFromCategories(entry.getCategories());
    }
    
    /**
     * Goes through all entries and adds all found categories to a Set.
     */
    private void scanCategories() {
        presetCategories.clear();
        for (AddressbookEntry entry : entries.values()) {
            addCategories(entry.getCategories());
        }
    }
    
    /**
     * Adds the given categories to the Set of categories.
     * 
     * @param categories The categories to add (if not already present)
     */
    private void addCategories(Collection<String> categories) {
        presetCategories.addAll(categories);
    }
    
    /**
     * Returns a List of all categories that were found in the entries.
     * 
     * @return The List of categories, whereas each category only appears once.
     */
    public synchronized List<String> getCategories() {
        return new ArrayList<>(presetCategories);
    }
    
    public synchronized int getNumEntries() {
        return entries.size();
    }
    
}
