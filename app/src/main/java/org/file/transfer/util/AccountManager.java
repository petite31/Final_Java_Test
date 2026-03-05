package org.file.transfer.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class AccountManager {
    private static final String FILE_PATH = "account.xml";

    public static void saveUsername(String username) {
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            writer.write("<account>\n");
            writer.write("    <username>" + username + "</username>\n");
            writer.write("</account>");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String loadUsername() {
        File file = new File(FILE_PATH);
        if (!file.exists())
            return null;

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.startsWith("<username>") && line.endsWith("</username>")) {
                    return line.substring(10, line.length() - 11);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
