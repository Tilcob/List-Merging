package github.tilcob.app.listmerging;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class Application extends javafx.application.Application {
    @Override
    public void start(Stage stage) throws IOException {
        Thread.currentThread().setContextClassLoader(Application.class.getClassLoader());

        URL fxml = Application.class.getResource("mainView.fxml");
        if (fxml == null) {
            throw new IllegalStateException("mainView.fxml not found (resource path is incorrect).");
        }

        FXMLLoader fxmlLoader = new FXMLLoader(fxml);
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        stage.setTitle("Merging Files");
        stage.setScene(scene);
        stage.show();
    }
}
