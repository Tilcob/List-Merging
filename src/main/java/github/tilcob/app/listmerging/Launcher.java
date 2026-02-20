package github.tilcob.app.listmerging;

public class Launcher {
    public static void main(String[] args) {
        Thread.currentThread().setContextClassLoader(Launcher.class.getClassLoader());
        javafx.application.Application.launch(Application.class, args);
    }
}
