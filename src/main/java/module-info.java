module org.file.transfer {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens org.file.transfer to javafx.fxml;
    exports org.file.transfer;

    opens org.file.transfer.controller to javafx.fxml;
}