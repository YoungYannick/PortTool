package com.YoungYannick;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;



import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PortScannerApp extends Application {

    public static class ProcessInfo {
        private final SimpleStringProperty id;
        private final SimpleStringProperty name;
        private final SimpleStringProperty path;
        private final SimpleStringProperty port;

        public ProcessInfo(String id, String name, String path, String port) {
            this.id = new SimpleStringProperty(id);
            this.name = new SimpleStringProperty(name);
            this.path = new SimpleStringProperty(path);
            this.port = new SimpleStringProperty(port != null && !port.isEmpty() ? port : "-1");
        }

        public String getId() {
            return id.get();
        }

        public String getName() {
            return name.get();
        }

        public String getPath() {
            return path.get();
        }

        public String getPort() {
            return port.get();
        }
    }

    private Timer searchTimer;
    private String lastSearchText = "";

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        TableView<ProcessInfo> table = new TableView<>();
        ObservableList<ProcessInfo> data = FXCollections.observableArrayList();

        TableColumn<ProcessInfo, String> indexCol = new TableColumn<>("#");
        indexCol.setCellValueFactory(column -> new SimpleStringProperty(String.valueOf(data.indexOf(column.getValue()) + 1)));
        indexCol.setComparator((o1, o2) -> {
            Integer i1 = o1 == null || o1.isEmpty() ? -1 : Integer.parseInt(o1);
            Integer i2 = o2 == null || o2.isEmpty() ? -1 : Integer.parseInt(o2);
            return i1.compareTo(i2);
        });

        TableColumn<ProcessInfo, String> portCol = new TableColumn<>("端口号");
        portCol.setCellValueFactory(new PropertyValueFactory<>("port"));
        portCol.setComparator((o1, o2) -> {
            Integer p1 = o1 == null || o1.isEmpty() ? -1 : Integer.parseInt(o1);
            Integer p2 = o2 == null || o2.isEmpty() ? -1 : Integer.parseInt(o2);
            return p1.compareTo(p2);
        });

        TableColumn<ProcessInfo, String> idCol = new TableColumn<>("进程ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setComparator((o1, o2) -> {
            Integer pid1 = o1 == null || o1.isEmpty() ? -1 : Integer.parseInt(o1);
            Integer pid2 = o2 == null || o2.isEmpty() ? -1 : Integer.parseInt(o2);
            return pid1.compareTo(pid2);
        });

        TableColumn<ProcessInfo, String> pathCol = new TableColumn<>("进程名");
        pathCol.setCellValueFactory(new PropertyValueFactory<>("path"));

        TableColumn<ProcessInfo, String> nameCol = new TableColumn<>("进程路径");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        table.setItems(data);
        table.getColumns().addAll(indexCol, portCol, idCol, pathCol, nameCol);

        ContextMenu contextMenu = new ContextMenu();
        MenuItem killItem = new MenuItem("结束进程");
        killItem.setOnAction(actionEvent -> {
            ProcessInfo processInfo = table.getSelectionModel().getSelectedItem();
            if (processInfo != null) {
                killProcess(processInfo.getId());
                 refreshTable(table, data, root);
            }
        });
        MenuItem refreshItem = new MenuItem("刷新页面");
        refreshItem.setOnAction(actionEvent -> refreshTable(table, data, root));
        contextMenu.getItems().addAll(killItem, refreshItem);

        table.setRowFactory(tv -> {
            TableRow<ProcessInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.SECONDARY) {
                    table.getSelectionModel().select(row.getItem());
                    contextMenu.show(table, event.getScreenX(), event.getScreenY());
                } else {
                    contextMenu.hide();
                }
            });
            return row;
        });

        // Add scroll listener to hide context menu when scrolling
        table.setOnScroll(event -> contextMenu.hide());

        // Add listener to hide context menu when table view changes
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> contextMenu.hide());

        TextField searchField = new TextField();
        searchField.setPromptText("模糊搜索");
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                performSearch(searchField.getText(), data, table);
            }
        });

        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (searchTimer != null) {
                searchTimer.cancel();
            }
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    performSearch(newText, data, table);
                }
            }, 500);
        });

        Button searchButton = new Button("查询");
        searchButton.setOnAction(event -> performSearch(searchField.getText(), data, table));

        HBox searchBox = new HBox(searchField, searchButton);
        searchBox.setAlignment(Pos.CENTER);
        searchBox.setSpacing(10);

        root.setTop(searchBox);
        root.setCenter(table);

        // 创建作者信息部分
        HBox authorInfoBox = createAuthorInfoBox();
        root.setBottom(authorInfoBox);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("YoungYannick Port Scanner");
        // 设置左上角的图标
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/Yannick.png")));

        primaryStage.show();

        // 显示加载标志
        showLoadingIndicator(root);

        // 弹出欢迎信息弹窗
        Alert welcomeAlert = new Alert(Alert.AlertType.INFORMATION);
        welcomeAlert.setTitle("欢迎");
        welcomeAlert.setHeaderText("欢迎使用YoungYannick Port Scanner");
        welcomeAlert.setContentText("请您耐心等待,正在加载数据~");

        // 设置3秒后自动关闭
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), event -> welcomeAlert.close()));
        timeline.setCycleCount(1);
        timeline.play();

        welcomeAlert.show();


        // 异步加载数据
        Task<List<ProcessInfo>> loadTask = new Task() {
            @Override
            protected List<ProcessInfo> call() {
                return getProcessInfo();
            }
        };

        loadTask.setOnSucceeded(event -> {
            data.setAll(loadTask.getValue());
            // 按端口号排序
            FXCollections.sort(data, Comparator.comparingInt(processInfo -> Integer.parseInt(processInfo.getPort())));
            root.setCenter(table);
        });

        new Thread(loadTask).start();

        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
    }



    private void performSearch(String searchText, ObservableList<ProcessInfo> data, TableView<ProcessInfo> table) {
        if (!searchText.equals(lastSearchText)) {
            lastSearchText = searchText;
            ObservableList<ProcessInfo> filteredData = FXCollections.observableArrayList();
            for (ProcessInfo info : data) {
                if (info.getId().toLowerCase().contains(searchText.toLowerCase()) ||
                        info.getName().toLowerCase().contains(searchText.toLowerCase()) ||
                        info.getPath().toLowerCase().contains(searchText.toLowerCase()) ||
                        info.getPort().toLowerCase().contains(searchText.toLowerCase())) {
                    filteredData.add(info);
                }
            }
            table.setItems(filteredData);
            table.refresh();

        }
    }

    private void refreshTable(TableView<ProcessInfo> table, ObservableList<ProcessInfo> data, BorderPane root) {
        // 显示加载标志
        showLoadingIndicator(root);

        // 异步加载数据
        Task<List<ProcessInfo>> loadTask = new Task() {
            @Override
            protected List<ProcessInfo> call() {
                return getProcessInfo();
            }
        };

        loadTask.setOnSucceeded(event -> {
            data.setAll(loadTask.getValue());
            // 按端口号排序
            FXCollections.sort(data, Comparator.comparingInt(processInfo -> Integer.parseInt(processInfo.getPort())));
            // 移除加载指示器并重新显示表格
            root.setCenter(table);
        });

        new Thread(loadTask).start();
    }


    private void showLoadingIndicator(BorderPane root) {
        ProgressIndicator progressIndicator = new ProgressIndicator();
        Label loadingLabel = new Label("正在加载数据...");
        VBox loadingBox = new VBox(progressIndicator, loadingLabel);
        loadingBox.setAlignment(Pos.CENTER);
        root.setCenter(loadingBox);
    }


    private List<ProcessInfo> getProcessInfo() {
        List<ProcessInfo> processInfos = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("netstat", "-ano");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            List<String> lines = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.contains("LISTENING")) {
                    lines.add(line);
                }
            }

            int batchSize = 10; // 每批次处理的任务数量
            int numThreads = Runtime.getRuntime().availableProcessors(); // 线程池大小
            ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
            for (int i = 0; i < lines.size(); i += batchSize) {
                int end = Math.min(i + batchSize, lines.size());
                List<String> batchLines = lines.subList(i, end);
                executorService.submit(() -> {
                    for (String l : batchLines) {
                        try {
                            String[] parts = l.trim().split("\\s+");
                            String port = parts[1].split(":")[1];
                            String pid = parts[4];
                            ProcessBuilder pb2 = new ProcessBuilder("wmic", "process", "where", "processid=" + pid, "get", "name,executablepath,processid");
                            Process process2 = pb2.start();
                            BufferedReader reader2 = new BufferedReader(new InputStreamReader(process2.getInputStream()));
                            // Skip first informational line
                            reader2.readLine();
                            String line2;
                            while ((line2 = reader2.readLine()) != null) {
                                if (!line2.trim().isEmpty()) {
                                    String[] parts2 = line2.trim().split("\\s+", 3);
                                    String name = parts2[0];
                                    String path = parts2.length > 1 ? parts2[1] : "";
                                    synchronized (processInfos) {
                                        processInfos.add(new ProcessInfo(pid, name, path, port));
                                    }
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return processInfos;
    }


    private void killProcess(String pid) {
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "taskkill /PID " + pid + " /F");
            Process process = builder.start();

            // 等待进程执行完成
            int exitCode = process.waitFor();

            // 检查进程的返回状态
            if (exitCode == 0) {
                // 进程成功结束，弹出提示
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("进程结束");
                    alert.setHeaderText(null);
                    alert.setContentText("进程 " + pid + " 已成功结束。");
                    alert.showAndWait();
                });
            } else {
                // 进程结束失败，弹出错误提示
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("进程结束失败");
                    alert.setHeaderText(null);
                    alert.setContentText("进程 " + pid + " 结束失败。");
                    alert.showAndWait();
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private HBox createAuthorInfoBox() {
        HBox authorInfoBox = new HBox(10);
        authorInfoBox.setAlignment(Pos.CENTER);
        authorInfoBox.setPadding(new Insets(10));
        authorInfoBox.setPrefHeight(20); // 强制设置高度为10

        // 左半部分，占窗口宽度的75%
        HBox leftBox = new HBox(10);
        leftBox.setAlignment(Pos.BOTTOM_LEFT);


        // GitHub链接和图标
        Hyperlink githubLink = new Hyperlink();
        Image githubIcon = new Image(getClass().getResourceAsStream("/github.png"));
        ImageView githubIconView = new ImageView(githubIcon);
        githubIconView.setFitWidth(20);
        githubIconView.setFitHeight(20);
        githubLink.setGraphic(githubIconView);
        githubLink.setOnAction(e -> getHostServices().showDocument("https://github.com/YoungYannick"));

        // BiliBili链接和图标
        Hyperlink biliLink = new Hyperlink();
        Image biliIcon = new Image(getClass().getResourceAsStream("/币站.png"));
        ImageView biliIconView = new ImageView(biliIcon);
        biliIconView.setFitWidth(20);
        biliIconView.setFitHeight(20);
        biliLink.setGraphic(biliIconView);
        biliLink.setOnAction(e -> getHostServices().showDocument("https://space.bilibili.com/505709474"));

        // YouTuBe链接和图标
        Hyperlink YouTuBeLink = new Hyperlink();
        Image youtubeIcon = new Image(getClass().getResourceAsStream("/youtube.png"));
        ImageView youtubeIconView = new ImageView(youtubeIcon);
        youtubeIconView.setFitWidth(20);
        youtubeIconView.setFitHeight(20);
        YouTuBeLink.setGraphic(youtubeIconView);
        YouTuBeLink.setOnAction(e -> getHostServices().showDocument("https://www.youtube.com/@yannick_yang"));



        leftBox.getChildren().addAll(githubLink, YouTuBeLink , biliLink);

        // 右半部分，占窗口宽度的25%
        HBox rightBox = new HBox();
        rightBox.setAlignment(Pos.BOTTOM_RIGHT);
        rightBox.setPrefHeight(16); // 强制设置高度为10

        // 实时时间显示
        Label timeLabel = new Label();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(1), event -> {
            timeLabel.setText(dateFormat.format(new Date()));
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        rightBox.getChildren().add(timeLabel);

        // 设置左右两部分的宽度比例
        HBox.setHgrow(leftBox, Priority.ALWAYS);
        HBox.setHgrow(rightBox, Priority.ALWAYS);

        authorInfoBox.getChildren().addAll(leftBox, rightBox);
        return authorInfoBox;
    }


    public static void main(String[] args) {
        launch(args);
    }
}
