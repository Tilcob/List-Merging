module github.tilcob.app.listmerging {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;

    opens github.tilcob.app.listmerging to javafx.fxml;
    exports github.tilcob.app.listmerging;
}