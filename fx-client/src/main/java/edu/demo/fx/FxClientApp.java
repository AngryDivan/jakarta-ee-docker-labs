package edu.demo.fx;

import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FxClientApp extends Application {

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();

    private final TextField titleField = new TextField();
    private final Button refreshBtn = new Button("Refresh");
    private final Button addBtn = new Button("Add");
    private final Button toggleBtn = new Button("Toggle selected");
    private final Button deleteBtn = new Button("Delete selected");

    private final TableView<Row> table = new TableView<>();

    private final String base = "http://localhost:8080/lab-demo/api/tasks";

    @Override
    public void start(Stage stage) {
        TableColumn<Row, Number> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> c.getValue().id);

        TableColumn<Row, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(c -> c.getValue().title);

        TableColumn<Row, Boolean> doneCol = new TableColumn<>("Done");
        doneCol.setCellValueFactory(c -> c.getValue().done);

        table.getColumns().addAll(idCol, titleCol, doneCol);
        table.setItems(rows);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        refreshBtn.setOnAction(e -> reload());
        addBtn.setOnAction(e -> add());
        toggleBtn.setOnAction(e -> toggleSelected());
        deleteBtn.setOnAction(e -> deleteSelected());

        HBox top = new HBox(10,
                new Label("Title:"), titleField,
                addBtn, refreshBtn, toggleBtn, deleteBtn
        );
        VBox root = new VBox(10, top, table);
        root.setPrefSize(900, 500);

        stage.setTitle("JavaFX client → Jakarta REST (GlassFish)");
        stage.setScene(new Scene(root));
        stage.show();

        reload();
    }

    private void reload() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base)).GET().build();
            String json = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            List<TaskDto> list = parseTasks(json);

            rows.clear();
            for (TaskDto t : list) rows.add(new Row(t));
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void add() {
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        if (title.isEmpty()) return;

        String body = "{\"title\":\"" + escape(title) + "\",\"done\":false}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
            titleField.setText("");
            reload();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void toggleSelected() {
        Row r = table.getSelectionModel().getSelectedItem();
        if (r == null) return;
        long id = r.id.get();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/" + id + "/toggle"))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
            reload();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void deleteSelected() {
        Row r = table.getSelectionModel().getSelectedItem();
        if (r == null) return;
        long id = r.id.get();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/" + id))
                    .DELETE()
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
            reload();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showError(Exception ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText("Error");
        a.setContentText(String.valueOf(ex));
        a.showAndWait();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Мини-парсер JSON именно под формат массива Task (без внешних библиотек)
    private List<TaskDto> parseTasks(String json) {
        List<TaskDto> out = new ArrayList<>();
        String s = json == null ? "" : json.trim();
        if (s.length() < 2 || s.charAt(0) != '[') return out;

        int i = 0;
        while (i < s.length()) {
            int objStart = s.indexOf('{', i);
            if (objStart < 0) break;
            int objEnd = findMatchingBrace(s, objStart);
            if (objEnd < 0) break;

            String obj = s.substring(objStart + 1, objEnd);
            TaskDto t = new TaskDto();
            t.id = parseLongField(obj, "\"id\"");
            t.title = parseStringField(obj, "\"title\"");
            t.done = parseBoolField(obj, "\"done\"");
            if (t.id != null) out.add(t);

            i = objEnd + 1;
        }
        return out;
    }

    private int findMatchingBrace(String s, int start) {
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inStr = !inStr;
            if (inStr) continue;
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private Long parseLongField(String obj, String key) {
        int k = obj.indexOf(key);
        if (k < 0) return null;
        int colon = obj.indexOf(':', k);
        if (colon < 0) return null;
        int end = findValueEnd(obj, colon + 1);
        String v = obj.substring(colon + 1, end).trim();
        try { return Long.parseLong(v); } catch (Exception e) { return null; }
    }

    private String parseStringField(String obj, String key) {
        int k = obj.indexOf(key);
        if (k < 0) return "";
        int colon = obj.indexOf(':', k);
        if (colon < 0) return "";
        int firstQuote = obj.indexOf('"', colon + 1);
        if (firstQuote < 0) return "";
        int i = firstQuote + 1;
        StringBuilder sb = new StringBuilder();
        while (i < obj.length()) {
            char c = obj.charAt(i);
            if (c == '"' && obj.charAt(i - 1) != '\\') break;
            sb.append(c);
            i++;
        }
        return sb.toString().replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private boolean parseBoolField(String obj, String key) {
        int k = obj.indexOf(key);
        if (k < 0) return false;
        int colon = obj.indexOf(':', k);
        if (colon < 0) return false;
        int end = findValueEnd(obj, colon + 1);
        String v = obj.substring(colon + 1, end).trim();
        return v.startsWith("true");
    }

    private int findValueEnd(String s, int from) {
        int i = from;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ',' ) return i;
            i++;
        }
        return s.length();
    }

    public static class Row {
        final LongProperty id = new SimpleLongProperty();
        final StringProperty title = new SimpleStringProperty();
        final BooleanProperty done = new SimpleBooleanProperty();

        Row(TaskDto t) {
            id.set(t.id == null ? 0 : t.id);
            title.set(t.title == null ? "" : t.title);
            done.set(t.done);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}