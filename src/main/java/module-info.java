module github.tilcob.app.listmerging {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires com.fasterxml.jackson.databind;
    requires org.apache.poi.ooxml;
    requires com.opencsv;
    requires org.slf4j;
    requires commons.math3;

    opens github.tilcob.app.listmerging.model to com.fasterxml.jackson.databind;
    opens github.tilcob.app.listmerging to javafx.fxml;
    exports github.tilcob.app.listmerging;
    exports github.tilcob.app.listmerging.controller;
    opens github.tilcob.app.listmerging.controller to javafx.fxml;
}