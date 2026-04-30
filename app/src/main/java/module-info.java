module org.file.transfer {
    requires transitive javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.prefs;
    requires java.desktop;

    opens org.file.transfer to javafx.fxml;

    exports org.file.transfer;

    opens org.file.transfer.controller to javafx.fxml;
}